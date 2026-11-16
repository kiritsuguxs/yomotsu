package eu.kanade.translation.recognizer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class MlKitOcrEngine(
    context: Context,
    override val language: TextRecognizerLanguage,
) : OcrEngine {
    override val type = OcrEngineType.ML_KIT

    private val appContext = context.applicationContext
    private val initializationStart = System.nanoTime()
    private val recognizer = TextRecognition.getClient(
        when (language) {
            TextRecognizerLanguage.ENGLISH -> TextRecognizerOptions.DEFAULT_OPTIONS
            TextRecognizerLanguage.CHINESE -> ChineseTextRecognizerOptions.Builder().build()
            TextRecognizerLanguage.JAPANESE -> JapaneseTextRecognizerOptions.Builder().build()
            TextRecognizerLanguage.KOREAN -> KoreanTextRecognizerOptions.Builder().build()
        },
    )
    private val initializationTimeMs = elapsedMs(initializationStart)
    private var initializationReported = false

    override suspend fun recognize(image: OcrImage): OcrPage = withContext(Dispatchers.IO) {
        val pageStart = System.nanoTime()
        try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching {
                image.openInputStream().use { BitmapFactory.decodeStream(it, null, boundsOptions) }
            }
            val probedW = boundsOptions.outWidth
            val probedH = boundsOptions.outHeight
            val tiles = if (probedW > 0 && probedH > 0) {
                PaddleWebtoonTiler.planTiles(
                    imageWidth = probedW,
                    imageHeight = probedH,
                    detLimitSideLen = MLKIT_TILE_MAX_SIDE,
                    targetMinDetWidth = MLKIT_MIN_TILE_WIDTH,
                )
            } else {
                emptyList()
            }

            if (tiles.size > 1) {
                val tiledResult = recognizeTiledWebtoon(image, probedW, probedH, tiles, pageStart)
                if (tiledResult != null) {
                    return@withContext tiledResult
                }
            }

            val inputImage = InputImage.fromFilePath(appContext, image.uri)
            val ocrStart = System.nanoTime()
            val result = recognizer.process(inputImage).await()
            val ocrTimeMs = elapsedMs(ocrStart)
            val blocks = extractBlocks(result, offsetY = 0f)
            val reportedInitializationMs = consumeInitializationTimeMs()
            val totalTimeMs = elapsedMs(pageStart)
            logcat(LogPriority.INFO) {
                "OCR page engine=ML_KIT tiles=1 initMs=$reportedInitializationMs ocrMs=$ocrTimeMs " +
                    "totalMs=$totalTimeMs blocks=${blocks.size}"
            }
            OcrPage(
                width = inputImage.width,
                height = inputImage.height,
                blocks = blocks,
                metrics = OcrPerformanceMetrics(
                    initializationTimeMs = reportedInitializationMs,
                    ocrTimeMs = ocrTimeMs,
                    totalTimeMs = totalTimeMs,
                ),
            )
        } catch (error: Throwable) {
            logcat(LogPriority.ERROR, error) {
                "OCR failure engine=ML_KIT totalMs=${elapsedMs(pageStart)} error=${error::class.simpleName}"
            }
            throw error
        }
    }

    private suspend fun recognizeTiledWebtoon(
        image: OcrImage,
        pageWidth: Int,
        pageHeight: Int,
        tiles: List<PaddleWebtoonTiler.TileWindow>,
        pageStart: Long,
    ): OcrPage? {
        val rawBlocks = ArrayList<OcrTextBlock>()
        var ocrTimeMs = 0L
        val decodeOpts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decodedAny = image.openInputStream().use { stream ->
            val decoder = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BitmapRegionDecoder.newInstance(stream)
                } else {
                    @Suppress("DEPRECATION")
                    BitmapRegionDecoder.newInstance(stream, false)
                }
            }.getOrNull() ?: return@use false

            try {
                for ((index, tile) in tiles.withIndex()) {
                    val tileBitmap = decoder.decodeRegion(Rect(0, tile.top, pageWidth, tile.bottom), decodeOpts) ?: continue
                    val prevTile = tiles.getOrNull(index - 1)
                    val nextTile = tiles.getOrNull(index + 1)
                    val tileResult = try {
                        val sliceStart = System.nanoTime()
                        val inputImage = InputImage.fromBitmap(tileBitmap, 0)
                        val result = recognizer.process(inputImage).await()
                        ocrTimeMs += elapsedMs(sliceStart)
                        result
                    } finally {
                        tileBitmap.recycle()
                    }
                    extractBlocks(tileResult, offsetY = 0f).forEach { localBlock ->
                        val corners = listOf(
                            OcrPoint(localBlock.x, localBlock.y),
                            OcrPoint(localBlock.x + localBlock.width, localBlock.y),
                            OcrPoint(localBlock.x + localBlock.width, localBlock.y + localBlock.height),
                            OcrPoint(localBlock.x, localBlock.y + localBlock.height),
                        )
                        if (!PaddleWebtoonTiler.isClippedAtTileSeam(corners, tile, prevTile, nextTile)) {
                            rawBlocks += localBlock.copy(y = localBlock.y + tile.top)
                        }
                    }
                }
                true
            } finally {
                decoder.recycle()
            }
        }
        if (!decodedAny) return null

        val deduplicated = PaddleWebtoonTiler.deduplicateBlocks(rawBlocks)
        val reportedInitializationMs = consumeInitializationTimeMs()
        val totalTimeMs = elapsedMs(pageStart)
        logcat(LogPriority.INFO) {
            "OCR page engine=ML_KIT tiles=${tiles.size} initMs=$reportedInitializationMs ocrMs=$ocrTimeMs " +
                "totalMs=$totalTimeMs blocks=${deduplicated.size}"
        }
        return OcrPage(
            width = pageWidth,
            height = pageHeight,
            blocks = deduplicated,
            metrics = OcrPerformanceMetrics(
                initializationTimeMs = reportedInitializationMs,
                ocrTimeMs = ocrTimeMs,
                totalTimeMs = totalTimeMs,
            ),
        )
    }

    private fun extractBlocks(result: Text, offsetY: Float): List<OcrTextBlock> {
        return result.textBlocks.mapNotNull { block ->
            val bounds = block.boundingBox ?: return@mapNotNull null
            val symbolBounds = block.lines
                .flatMap { it.elements }
                .flatMap { it.symbols }
                .mapNotNull { it.boundingBox }
                .filter { it.width() > 0 && it.height() > 0 }
            val medianSymbolWidth = if (symbolBounds.isNotEmpty()) {
                val widths = symbolBounds.map { it.width() }.sorted()
                widths[widths.size / 2].toFloat()
            } else {
                1f
            }
            val medianSymbolHeight = if (symbolBounds.isNotEmpty()) {
                val heights = symbolBounds.map { it.height() }.sorted()
                heights[heights.size / 2].toFloat()
            } else {
                1f
            }

            OcrTextBlock(
                text = block.text,
                x = bounds.left.toFloat(),
                y = bounds.top.toFloat() + offsetY,
                width = bounds.width().toFloat(),
                height = bounds.height().toFloat(),
                symbolWidth = medianSymbolWidth,
                symbolHeight = medianSymbolHeight,
                angle = block.lines.firstOrNull()?.angle ?: 0f,
            )
        }
    }

    private fun consumeInitializationTimeMs(): Long {
        val reported = if (initializationReported) 0L else initializationTimeMs
        initializationReported = true
        return reported
    }

    override suspend fun release() {
        recognizer.close()
    }

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / NANOS_PER_MILLISECOND

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val MLKIT_TILE_MAX_SIDE = 4096
        const val MLKIT_MIN_TILE_WIDTH = 1024
    }
}
