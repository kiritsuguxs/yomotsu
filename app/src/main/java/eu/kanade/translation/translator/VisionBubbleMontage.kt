package eu.kanade.translation.translator

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Base64
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationRegion
import eu.kanade.translation.model.sourceRegion
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class VisionBubbleReference(
    val index: Int,
    val pageKey: String,
    val blockIndex: Int,
)

data class VisionMontageAttachment(
    val mimeType: String,
    val base64Data: String,
    val references: List<VisionBubbleReference>,
)

/**
 * Pure-Kotlin geometry and grid planner for constructing compact numbered
 * speech-bubble montage sheets for multimodal Vision LLMs.
 */
internal object VisionBubbleMontagePlanner {

    data class CropRect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val width: Int get() = (right - left).coerceAtLeast(1)
        val height: Int get() = (bottom - top).coerceAtLeast(1)
    }

    data class CropRequest(
        val reference: VisionBubbleReference,
        val cropRect: CropRect,
    )

    data class MontageCellPlacement(
        val reference: VisionBubbleReference,
        val cropRect: CropRect,
        val cellLeft: Int,
        val cellTop: Int,
        val cellWidth: Int,
        val cellHeight: Int,
    )

    data class MontageLayout(
        val sheetWidth: Int,
        val sheetHeight: Int,
        val placements: List<MontageCellPlacement>,
    )

    fun computeBubbleCropRect(
        region: TranslationRegion,
        pageWidth: Int,
        pageHeight: Int,
        paddingRatio: Float = DEFAULT_CONTEXT_PADDING_RATIO,
        minPaddingPx: Int = DEFAULT_MIN_PADDING_PX,
        maxPaddingPx: Int = DEFAULT_MAX_PADDING_PX,
    ): CropRect {
        val safePageW = pageWidth.coerceAtLeast(1)
        val safePageH = pageHeight.coerceAtLeast(1)
        val padX = (region.width * paddingRatio).roundToInt().coerceIn(minPaddingPx, maxPaddingPx)
        val padY = (region.height * paddingRatio).roundToInt().coerceIn(minPaddingPx, maxPaddingPx)

        val rawLeft = (region.x.roundToInt() - padX).coerceIn(0, safePageW - 1)
        val rawTop = (region.y.roundToInt() - padY).coerceIn(0, safePageH - 1)
        val rawRight = ((region.x + region.width).roundToInt() + padX).coerceIn(rawLeft + 1, safePageW)
        val rawBottom = ((region.y + region.height).roundToInt() + padY).coerceIn(rawTop + 1, safePageH)

        return CropRect(
            left = rawLeft,
            top = rawTop,
            right = rawRight,
            bottom = rawBottom,
        )
    }

    fun chooseSampleSize(
        cropWidth: Int,
        cropHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Int {
        var sampleSize = 1
        val safeTargetW = targetWidth.coerceAtLeast(1)
        val safeTargetH = targetHeight.coerceAtLeast(1)
        while ((cropWidth / (sampleSize * 2)) >= safeTargetW &&
            (cropHeight / (sampleSize * 2)) >= safeTargetH
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }

    fun planMontageGrid(
        requests: List<CropRequest>,
        maxSheetWidth: Int = DEFAULT_MAX_SHEET_WIDTH,
        maxSheetHeight: Int = DEFAULT_MAX_SHEET_HEIGHT,
        maxCellHeight: Int = DEFAULT_MAX_CELL_HEIGHT,
        paddingPx: Int = DEFAULT_GRID_PADDING_PX,
        maxCells: Int = DEFAULT_MAX_CELLS,
    ): MontageLayout {
        val bounded = requests.take(maxCells)
        if (bounded.isEmpty()) return MontageLayout(0, 0, emptyList())

        val cols = when {
            bounded.size <= 1 -> 1
            bounded.size <= 4 -> 2
            else -> 3
        }
        val availableWidth = (maxSheetWidth - paddingPx * (cols + 1)).coerceAtLeast(cols)
        val colWidth = availableWidth / cols

        val rowCount = ceil(bounded.size / cols.toDouble()).toInt()
        val unscaledPlacements = ArrayList<MontageCellPlacement>(bounded.size)
        var currentTop = paddingPx

        for (row in 0 until rowCount) {
            val startIdx = row * cols
            val endIdx = min(startIdx + cols, bounded.size)
            var rowHeight = 0

            val rowSizes = (startIdx until endIdx).map { idx ->
                val crop = bounded[idx].cropRect
                val fitScale = minOf(
                    colWidth.toFloat() / crop.width.toFloat(),
                    maxCellHeight.toFloat() / crop.height.toFloat(),
                    MAX_UPSCALE_FACTOR,
                )
                val targetW = (crop.width * fitScale).roundToInt().coerceIn(1, colWidth)
                val targetH = (crop.height * fitScale).roundToInt().coerceIn(1, maxCellHeight)
                rowHeight = max(rowHeight, targetH)
                targetW to targetH
            }

            for (offset in 0 until (endIdx - startIdx)) {
                val item = bounded[startIdx + offset]
                val (cellW, cellH) = rowSizes[offset]
                val colLeft = paddingPx + offset * (colWidth + paddingPx) + (colWidth - cellW) / 2
                val cellTop = currentTop + (rowHeight - cellH) / 2
                unscaledPlacements += MontageCellPlacement(
                    reference = item.reference,
                    cropRect = item.cropRect,
                    cellLeft = colLeft,
                    cellTop = cellTop,
                    cellWidth = cellW,
                    cellHeight = cellH,
                )
            }
            currentTop += rowHeight + paddingPx
        }

        val usedRight = unscaledPlacements.maxOf { it.cellLeft + it.cellWidth } + paddingPx
        val sheetW = usedRight.coerceIn(1, maxSheetWidth)
        val rawSheetH = currentTop.coerceAtLeast(1)

        if (rawSheetH <= maxSheetHeight) {
            return MontageLayout(sheetW, rawSheetH, unscaledPlacements)
        }

        val shrink = maxSheetHeight.toFloat() / rawSheetH.toFloat()
        val scaledPlacements = unscaledPlacements.map { placement ->
            val left = (placement.cellLeft * shrink).roundToInt()
            val top = (placement.cellTop * shrink).roundToInt()
            val w = (placement.cellWidth * shrink).roundToInt().coerceAtLeast(1)
            val h = (placement.cellHeight * shrink).roundToInt().coerceAtLeast(1)
            placement.copy(
                cellLeft = left,
                cellTop = top,
                cellWidth = w,
                cellHeight = h,
            )
        }
        val finalW = (scaledPlacements.maxOf { it.cellLeft + it.cellWidth } + max(2, (paddingPx * shrink).roundToInt()))
            .coerceIn(1, maxSheetWidth)
        val finalH = (scaledPlacements.maxOf { it.cellTop + it.cellHeight } + max(2, (paddingPx * shrink).roundToInt()))
            .coerceIn(1, maxSheetHeight)
        return MontageLayout(finalW, finalH, scaledPlacements)
    }

    fun mapCropRectToDecoder(
        crop: CropRect,
        pageWidth: Int,
        pageHeight: Int,
        decoderWidth: Int,
        decoderHeight: Int,
    ): CropRect? {
        if (decoderWidth <= 0 || decoderHeight <= 0) return null
        val safePageW = pageWidth.coerceAtLeast(1)
        val safePageH = pageHeight.coerceAtLeast(1)
        if (safePageW == decoderWidth && safePageH == decoderHeight) {
            val left = crop.left.coerceIn(0, decoderWidth - 1)
            val top = crop.top.coerceIn(0, decoderHeight - 1)
            val right = crop.right.coerceIn(left + 1, decoderWidth)
            val bottom = crop.bottom.coerceIn(top + 1, decoderHeight)
            return CropRect(left, top, right, bottom)
        }
        val scaleX = decoderWidth.toFloat() / safePageW.toFloat()
        val scaleY = decoderHeight.toFloat() / safePageH.toFloat()
        val left = (crop.left * scaleX).roundToInt().coerceIn(0, decoderWidth - 1)
        val top = (crop.top * scaleY).roundToInt().coerceIn(0, decoderHeight - 1)
        val right = (crop.right * scaleX).roundToInt().coerceIn(left + 1, decoderWidth)
        val bottom = (crop.bottom * scaleY).roundToInt().coerceIn(top + 1, decoderHeight)
        return CropRect(left, top, right, bottom)
    }

    const val DEFAULT_CONTEXT_PADDING_RATIO = 0.28f
    const val DEFAULT_MIN_PADDING_PX = 24
    const val DEFAULT_MAX_PADDING_PX = 180
    const val DEFAULT_MAX_SHEET_WIDTH = 1280
    const val DEFAULT_MAX_SHEET_HEIGHT = 2048
    const val DEFAULT_MAX_CELL_HEIGHT = 380
    const val DEFAULT_GRID_PADDING_PX = 10
    const val DEFAULT_MAX_CELLS = 30
    private const val MAX_UPSCALE_FACTOR = 1.5f
}

internal object VisionBubbleMontageBuilder {

    fun build(
        pages: Map<String, PageTranslation>,
        scratchDir: File? = null,
        openPageStream: (String) -> InputStream?,
    ): VisionMontageAttachment? {
        val pageSizes = HashMap<String, Pair<Int, Int>>(pages.size)
        val requests = ArrayList<VisionBubbleMontagePlanner.CropRequest>()
        var nextLabel = 1
        for ((pageKey, page) in pages) {
            val pageW = page.imgWidth.roundToInt()
            val pageH = page.imgHeight.roundToInt()
            if (pageW <= 0 || pageH <= 0) continue
            pageSizes[pageKey] = pageW to pageH
            page.blocks.forEachIndexed { blockIdx, block ->
                val focusRegion = block.layoutRegion ?: block.sourceRegion()
                val cropRect = VisionBubbleMontagePlanner.computeBubbleCropRect(
                    region = focusRegion,
                    pageWidth = pageW,
                    pageHeight = pageH,
                )
                requests += VisionBubbleMontagePlanner.CropRequest(
                    reference = VisionBubbleReference(
                        index = nextLabel++,
                        pageKey = pageKey,
                        blockIndex = blockIdx,
                    ),
                    cropRect = cropRect,
                )
            }
        }
        if (requests.isEmpty()) return null

        val layout = VisionBubbleMontagePlanner.planMontageGrid(requests)
        if (layout.sheetWidth <= 0 || layout.sheetHeight <= 0 || layout.placements.isEmpty()) return null

        var montageBitmap: Bitmap? = null
        val tempFile = scratchDir?.let { dir ->
            runCatching {
                dir.mkdirs()
                File.createTempFile("vision-page-", ".img", dir)
            }.getOrNull()
        }
        val renderedRefs = ArrayList<VisionBubbleReference>(layout.placements.size)
        return try {
            val bitmap = Bitmap.createBitmap(
                layout.sheetWidth,
                layout.sheetHeight,
                Bitmap.Config.RGB_565,
            )
            montageBitmap = bitmap
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(24, 24, 24))

            val cellBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(68, 68, 68)
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(225, 16, 16, 20)
                style = Paint.Style.FILL
            }
            val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(255, 224, 72)
                typeface = Typeface.DEFAULT_BOLD
                textSize = 20f
            }
            val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

            val byPage = layout.placements.groupBy { it.reference.pageKey }
            for ((pageKey, pagePlacements) in byPage) {
                val (pageW, pageH) = pageSizes[pageKey] ?: continue
                withPageDecoder(
                    pageKey = pageKey,
                    tempFile = tempFile,
                    openPageStream = openPageStream,
                ) { decoder ->
                    val decoderW = decoder.width
                    val decoderH = decoder.height
                    for (placement in pagePlacements) {
                        val mappedCrop = VisionBubbleMontagePlanner.mapCropRectToDecoder(
                            crop = placement.cropRect,
                            pageWidth = pageW,
                            pageHeight = pageH,
                            decoderWidth = decoderW,
                            decoderHeight = decoderH,
                        ) ?: continue
                        val sampleSize = VisionBubbleMontagePlanner.chooseSampleSize(
                            cropWidth = mappedCrop.width,
                            cropHeight = mappedCrop.height,
                            targetWidth = placement.cellWidth,
                            targetHeight = placement.cellHeight,
                        )
                        val opts = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.RGB_565
                            inSampleSize = sampleSize
                        }
                        val cropBitmap = runCatching {
                            decoder.decodeRegion(
                                Rect(mappedCrop.left, mappedCrop.top, mappedCrop.right, mappedCrop.bottom),
                                opts,
                            )
                        }.getOrNull() ?: continue

                        try {
                            val dst = RectF(
                                placement.cellLeft.toFloat(),
                                placement.cellTop.toFloat(),
                                (placement.cellLeft + placement.cellWidth).toFloat(),
                                (placement.cellTop + placement.cellHeight).toFloat(),
                            )
                            canvas.drawBitmap(cropBitmap, null, dst, bitmapPaint)
                            canvas.drawRect(dst, cellBorderPaint)
                            drawIndexBadge(
                                canvas = canvas,
                                cell = dst,
                                label = "#${placement.reference.index}",
                                bgPaint = badgeBgPaint,
                                textPaint = badgeTextPaint,
                            )
                            renderedRefs += placement.reference
                        } finally {
                            cropBitmap.recycle()
                        }
                    }
                }
            }

            if (renderedRefs.isEmpty()) return null

            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            val bytes = output.toByteArray()
            if (bytes.isEmpty()) return null
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            VisionMontageAttachment(
                mimeType = "image/jpeg",
                base64Data = base64,
                references = renderedRefs.sortedBy { it.index },
            )
        } catch (_: Throwable) {
            null
        } finally {
            montageBitmap?.recycle()
            tempFile?.delete()
        }
    }

    private inline fun withPageDecoder(
        pageKey: String,
        tempFile: File?,
        openPageStream: (String) -> InputStream?,
        block: (BitmapRegionDecoder) -> Unit,
    ) {
        if (tempFile != null) {
            val copied = runCatching {
                val stream = openPageStream(pageKey) ?: return@runCatching false
                stream.use { input ->
                    tempFile.outputStream().use { out ->
                        input.copyTo(out)
                    }
                }
                true
            }.getOrDefault(false)
            if (!copied) return

            val decoder = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        BitmapRegionDecoder.newInstance(pfd)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    BitmapRegionDecoder.newInstance(tempFile.absolutePath, false)
                }
            }.getOrNull() ?: return

            try {
                block(decoder)
            } finally {
                decoder.recycle()
            }
            return
        }

        val stream = openPageStream(pageKey) ?: return
        stream.use { input ->
            val decoder = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BitmapRegionDecoder.newInstance(input)
                } else {
                    @Suppress("DEPRECATION")
                    BitmapRegionDecoder.newInstance(input, false)
                }
            }.getOrNull() ?: return@use

            try {
                block(decoder)
            } finally {
                decoder.recycle()
            }
        }
    }

    private fun drawIndexBadge(
        canvas: Canvas,
        cell: RectF,
        label: String,
        bgPaint: Paint,
        textPaint: Paint,
    ) {
        val textWidth = textPaint.measureText(label)
        val fontMetrics = textPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        val padH = 8f
        val padV = 4f
        val badgeLeft = cell.left + 4f
        val badgeTop = cell.top + 4f
        val badgeRight = min(cell.right - 2f, badgeLeft + textWidth + padH * 2f)
        val badgeBottom = min(cell.bottom - 2f, badgeTop + textHeight + padV * 2f)
        if (badgeRight <= badgeLeft || badgeBottom <= badgeTop) return

        canvas.drawRoundRect(
            RectF(badgeLeft, badgeTop, badgeRight, badgeBottom),
            6f,
            6f,
            bgPaint,
        )
        canvas.drawText(
            label,
            badgeLeft + padH,
            badgeTop + padV - fontMetrics.ascent,
            textPaint,
        )
    }

    private const val JPEG_QUALITY = 82
}
