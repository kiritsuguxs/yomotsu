package eu.kanade.translation.recognizer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.model.OCRRunResult
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class PaddleOcrEngine(
    context: Context,
    override val language: TextRecognizerLanguage,
) : OcrEngine {
    override val type = OcrEngineType.PADDLE_OCR

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var paddleOcr: PaddleOCR? = null

    init {
        require(type.supports(language)) { "PaddleOCR does not support $language" }
    }

    override suspend fun recognize(image: OcrImage): OcrPage = mutex.withLock {
        val pageStart = System.nanoTime()
        var initializationTimeMs = 0L
        try {
            val engine = paddleOcr ?: run {
                val initializationStart = System.nanoTime()
                PaddleOcrRuntime.requireOpenCv { OpenCVUtils.init(appContext) }
                val langDir = java.io.File(appContext.filesDir, "models/paddle/${language.name.lowercase()}")
                val customRecModel = java.io.File(langDir, "inference.onnx")
                val customRecConfig = java.io.File(langDir, "inference.yml")
                val instance = if (customRecModel.exists() && customRecConfig.exists()) {
                    PaddleOCR.create(
                        appContext,
                        com.paddle.ocr.PaddleOCRConfig(),
                        com.paddle.ocr.EngineConfig(),
                        detModelAssetPath = "models/det/inference.onnx",
                        recModelAssetPath = customRecModel.absolutePath,
                        recConfigAssetPath = customRecConfig.absolutePath,
                    )
                } else {
                    PaddleOCR.create(appContext)
                }
                instance.also {
                    paddleOcr = it
                    initializationTimeMs = elapsedMs(initializationStart)
                }
            }

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            image.openInputStream().use { BitmapFactory.decodeStream(it, null, boundsOptions) }
            val probedW = boundsOptions.outWidth
            val probedH = boundsOptions.outHeight
            val tiles = PaddleWebtoonTiler.planTiles(probedW, probedH)

            if (tiles.size <= 1) {
                val bitmap = image.openInputStream().use { BitmapFactory.decodeStream(it) }
                    ?: throw IllegalStateException("Unable to decode image for OCR")
                val result = try { engine.recognize(bitmap) } finally { bitmap.recycle() }
                require(result.imageWidth > 0 && result.imageHeight > 0) { "Unable to decode OCR image dimensions" }
                val blocks = result.results.mapNotNull { block ->
                    PaddleTextBlockMapper.map(
                        text = block.text,
                        confidence = block.confidence,
                        points = block.box.points.map { point -> OcrPoint(point.x, point.y) },
                    )
                }
                val totalTimeMs = elapsedMs(pageStart)
                logcat(LogPriority.INFO) {
                    "OCR page engine=PADDLE_OCR tiles=1 initMs=$initializationTimeMs ocrMs=${result.totalTimeMs} " +
                        "totalMs=$totalTimeMs blocks=${blocks.size} detMs=${result.detectionTimeMs} " +
                        "recMs=${result.recognitionTimeMs} detShape=${result.detInputShape.joinToString("x")}"
                }
                return@withLock OcrPage(
                    width = result.imageWidth,
                    height = result.imageHeight,
                    blocks = blocks,
                    metrics = OcrPerformanceMetrics(
                        initializationTimeMs = initializationTimeMs,
                        ocrTimeMs = result.totalTimeMs,
                        totalTimeMs = totalTimeMs,
                        detectionTimeMs = result.detectionTimeMs,
                        recognitionTimeMs = result.recognitionTimeMs,
                    ),
                )
            }

            val tiled = recognizeTiledWebtoon(image, engine, probedW, probedH, tiles)
            val totalTimeMs = elapsedMs(pageStart)
            logcat(LogPriority.INFO) {
                "OCR page engine=PADDLE_OCR tiles=${tiles.size} initMs=$initializationTimeMs ocrMs=${tiled.ocrMs} " +
                    "totalMs=$totalTimeMs blocks=${tiled.blocks.size} detMs=${tiled.detMs} " +
                    "recMs=${tiled.recMs} detShape=${tiled.detShape}"
            }
            OcrPage(
                width = probedW,
                height = probedH,
                blocks = tiled.blocks,
                metrics = OcrPerformanceMetrics(
                    initializationTimeMs = initializationTimeMs,
                    ocrTimeMs = tiled.ocrMs,
                    totalTimeMs = totalTimeMs,
                    detectionTimeMs = tiled.detMs,
                    recognitionTimeMs = tiled.recMs,
                ),
            )
        } catch (error: Throwable) {
            logcat(LogPriority.ERROR, error) {
                "OCR failure engine=PADDLE_OCR totalMs=${elapsedMs(pageStart)} error=${error::class.simpleName}"
            }
            throw error
        }
    }

    private data class TiledOcrSummary(
        val blocks: List<OcrTextBlock>,
        val ocrMs: Long,
        val detMs: Long,
        val recMs: Long,
        val detShape: String,
    )

    private suspend fun recognizeTiledWebtoon(
        image: OcrImage,
        engine: PaddleOCR,
        pageWidth: Int,
        pageHeight: Int,
        tiles: List<PaddleWebtoonTiler.TileWindow>,
    ): TiledOcrSummary {
        val rawBlocks = ArrayList<OcrTextBlock>()
        var ocrMs = 0L
        var detMs = 0L
        var recMs = 0L
        var lastShape = ""
        val decodeOpts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val usedRegionDecoder = image.openInputStream().use { stream ->
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
                    val rect = Rect(0, tile.top, pageWidth, tile.bottom)
                    val tileBitmap = decoder.decodeRegion(rect, decodeOpts) ?: continue
                    val tileRes: OCRRunResult = try {
                        engine.recognize(tileBitmap)
                    } finally {
                        tileBitmap.recycle()
                    }
                    ocrMs += tileRes.totalTimeMs
                    detMs += tileRes.detectionTimeMs
                    recMs += tileRes.recognitionTimeMs
                    if (lastShape.isEmpty() && tileRes.detInputShape.isNotEmpty()) {
                        lastShape = tileRes.detInputShape.joinToString("x")
                    }
                    collectTileBlocks(
                        tileRes = tileRes,
                        tile = tile,
                        prevTile = tiles.getOrNull(index - 1),
                        nextTile = tiles.getOrNull(index + 1),
                        out = rawBlocks,
                    )
                }
                true
            } finally {
                decoder.recycle()
            }
        }

        if (!usedRegionDecoder) {
            val fullBitmap = image.openInputStream().use { BitmapFactory.decodeStream(it, null, decodeOpts) }
                ?: throw IllegalStateException("Unable to decode image for OCR")
            try {
                for ((index, tile) in tiles.withIndex()) {
                    val sliceHeight = tile.height.coerceAtMost(fullBitmap.height - tile.top)
                    if (sliceHeight <= 0) continue
                    val tileBitmap = Bitmap.createBitmap(fullBitmap, 0, tile.top, fullBitmap.width, sliceHeight)
                    val tileRes: OCRRunResult = try {
                        engine.recognize(tileBitmap)
                    } finally {
                        if (tileBitmap !== fullBitmap) tileBitmap.recycle()
                    }
                    ocrMs += tileRes.totalTimeMs
                    detMs += tileRes.detectionTimeMs
                    recMs += tileRes.recognitionTimeMs
                    if (lastShape.isEmpty() && tileRes.detInputShape.isNotEmpty()) {
                        lastShape = tileRes.detInputShape.joinToString("x")
                    }
                    collectTileBlocks(
                        tileRes = tileRes,
                        tile = tile,
                        prevTile = tiles.getOrNull(index - 1),
                        nextTile = tiles.getOrNull(index + 1),
                        out = rawBlocks,
                    )
                }
            } finally {
                fullBitmap.recycle()
            }
        }

        return TiledOcrSummary(
            blocks = PaddleWebtoonTiler.deduplicateBlocks(rawBlocks),
            ocrMs = ocrMs,
            detMs = detMs,
            recMs = recMs,
            detShape = lastShape,
        )
    }

    private fun collectTileBlocks(
        tileRes: OCRRunResult,
        tile: PaddleWebtoonTiler.TileWindow,
        prevTile: PaddleWebtoonTiler.TileWindow?,
        nextTile: PaddleWebtoonTiler.TileWindow?,
        out: MutableList<OcrTextBlock>,
    ) {
        for (item in tileRes.results) {
            val localPoints = item.box.points.map { point -> OcrPoint(point.x, point.y) }
            if (PaddleWebtoonTiler.isClippedAtTileSeam(localPoints, tile, prevTile, nextTile)) {
                continue
            }
            val pagePoints = localPoints.map { point -> OcrPoint(point.x, point.y + tile.top) }
            val mapped = PaddleTextBlockMapper.map(
                text = item.text,
                confidence = item.confidence,
                points = pagePoints,
            )
            if (mapped != null) {
                out += mapped
            }
        }
    }

    override suspend fun release() {
        mutex.withLock {
            try {
                paddleOcr?.release()
            } finally {
                paddleOcr = null
            }
        }
    }

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / NANOS_PER_MILLISECOND

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

internal object PaddleOcrRuntime {
    fun requireOpenCv(initializer: () -> Boolean) {
        check(initializer()) { "Unable to initialize OpenCV for PaddleOCR" }
    }
}
