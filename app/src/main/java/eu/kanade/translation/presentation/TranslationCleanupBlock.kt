package eu.kanade.translation.presentation

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import eu.kanade.translation.model.AotPatchCleanup
import eu.kanade.translation.model.CURRENT_TRANSLATION_GEOMETRY_VERSION
import eu.kanade.translation.model.DbnetMaskCleanup
import eu.kanade.translation.model.LegacyCleanup
import eu.kanade.translation.model.NoCleanup
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.clamped
import eu.kanade.translation.model.resolveCleanup
import eu.kanade.translation.model.sourceRegion

@Composable
fun TranslationCleanupBlock(
    block: TranslationBlock,
    scaleFactor: Float,
    pageWidth: Float,
    pageHeight: Float,
    experimentalMaskPageValid: Boolean = true,
) {
    when (val cleanup = block.resolveCleanup(pageWidth, pageHeight, experimentalMaskPageValid)) {
        is LegacyCleanup -> {
            val color = block.backgroundColor?.let { Color(it) } ?: Color.White
            if (color.alpha == 0f) return

            // When an unedited speech balloon is detected, clean its safe inner layout region
            // to erase any un-OCR'd lines or line gaps. Once the user moves/resizes the text box,
            // keep cleanup strictly anchored to the original text patches so white background never grows.
            if (block.balloonDetected && !block.layoutCustomized && block.layoutRegion != null) {
                val layout = block.layoutRegion!!.clamped(pageWidth, pageHeight)
                val safeCornerRadius = minOf(layout.width, layout.height) * 0.35f
                val shape = RoundedCornerShape((safeCornerRadius * scaleFactor).pxToDp())
                Box(
                    modifier = Modifier
                        .offset((layout.x * scaleFactor).pxToDp(), (layout.y * scaleFactor).pxToDp())
                        .requiredSize(
                            (layout.width * scaleFactor).pxToDp(),
                            (layout.height * scaleFactor).pxToDp(),
                        )
                        .background(color, shape),
                )
            }

            val sources = block.sourceRegions.ifEmpty { listOf(block.sourceRegion()) }
            val boundaryMeasured = block.geometryVersion >= CURRENT_TRANSLATION_GEOMETRY_VERSION &&
                block.backgroundColor != null

            cleanup.patches.forEachIndexed { index, patch ->
                val region = patch.region
                val source = sources.getOrNull(index)
                val basePadX = when {
                    boundaryMeasured -> 1.5f
                    block.balloonDetected -> maxOf(block.symWidth * 0.35f, (pageWidth * 0.006f).coerceAtLeast(4f))
                    else -> (pageWidth * 0.003f).coerceAtLeast(2f)
                }
                val basePadY = when {
                    boundaryMeasured -> 1.5f
                    block.balloonDetected -> maxOf(block.symHeight * 0.28f, (pageWidth * 0.005f).coerceAtLeast(4f))
                    else -> (pageWidth * 0.003f).coerceAtLeast(2f)
                }
                val leftPad = if (boundaryMeasured && source != null && source.x - region.x < 1.5f) 0f else basePadX
                val rightPad = if (boundaryMeasured && source != null && (region.x + region.width) - (source.x + source.width) < 1.5f) 0f else basePadX
                val topPad = if (boundaryMeasured && source != null && source.y - region.y < 1.5f) 0f else basePadY
                val bottomPad = if (boundaryMeasured && source != null && (region.y + region.height) - (source.y + source.height) < 1.5f) 0f else basePadY

                val adjustedX = (region.x - leftPad).coerceAtLeast(0f)
                val adjustedY = (region.y - topPad).coerceAtLeast(0f)
                val adjustedRight = minOf(pageWidth, region.x + region.width + rightPad)
                val adjustedBottom = minOf(pageHeight, region.y + region.height + bottomPad)
                val adjustedWidth = (adjustedRight - adjustedX).coerceAtLeast(1f)
                val adjustedHeight = (adjustedBottom - adjustedY).coerceAtLeast(1f)
                val safeRadius = if (patch.cornerRadius.isFinite()) patch.cornerRadius else 0f
                val capsuleRadius = minOf(adjustedWidth, adjustedHeight) * 0.24f
                val effectiveRadius = maxOf(safeRadius, capsuleRadius)
                val cleanupShape = RoundedCornerShape((effectiveRadius * scaleFactor).pxToDp())
                Box(
                    modifier = Modifier
                        .offset((adjustedX * scaleFactor).pxToDp(), (adjustedY * scaleFactor).pxToDp())
                        .requiredSize(
                            (adjustedWidth * scaleFactor).pxToDp(),
                            (adjustedHeight * scaleFactor).pxToDp(),
                        )
                        // OCR bounds are axis-aligned. Rotating the eraser exposed their corners
                        // in Y8, so cleanup intentionally stays axis-aligned in Y9.
                        .background(color, cleanupShape),
                )
            }
        }
        is DbnetMaskCleanup -> {
            val maskColor = block.backgroundColor?.let { Color(it) } ?: Color.White
            if (maskColor.alpha == 0f) return
            val width = cleanup.mask.pageWidth
            val height = cleanup.mask.pageHeight
            val padding = (width * 0.003f).coerceAtLeast(2f)
            val bounds = remember(cleanup.mask, width, height, padding) {
                var minX = Float.POSITIVE_INFINITY
                var minY = Float.POSITIVE_INFINITY
                var maxX = Float.NEGATIVE_INFINITY
                var maxY = Float.NEGATIVE_INFINITY
                val valid = cleanup.mask.forEachRun(width, height) { y, x, length ->
                    if (x - padding < minX) minX = x - padding
                    if (y - padding < minY) minY = y - padding
                    if (x + length + padding > maxX) maxX = x + length + padding
                    if (y + 1 + padding > maxY) maxY = y + 1 + padding
                }
                if (!valid || !minX.isFinite() || !minY.isFinite()) {
                    null
                } else {
                    val left = minX.coerceAtLeast(0f)
                    val top = minY.coerceAtLeast(0f)
                    val right = maxX.coerceAtMost(pageWidth).coerceAtLeast(left + 1f)
                    val bottom = maxY.coerceAtMost(pageHeight).coerceAtLeast(top + 1f)
                    floatArrayOf(left, top, right - left, bottom - top)
                }
            } ?: return
            val paint = remember(maskColor) {
                Paint().apply {
                    color = maskColor
                    isAntiAlias = false
                }
            }
            val originX = bounds[0]
            val originY = bounds[1]
            Canvas(
                modifier = Modifier
                    .offset((originX * scaleFactor).pxToDp(), (originY * scaleFactor).pxToDp())
                    .requiredSize(
                        (bounds[2] * scaleFactor).pxToDp(),
                        (bounds[3] * scaleFactor).pxToDp(),
                    ),
            ) {
                cleanup.mask.forEachRun(width, height) { y, x, length ->
                    drawContext.canvas.drawRect(
                        (x - padding - originX) * scaleFactor,
                        (y - padding - originY) * scaleFactor,
                        (x + length + padding - originX) * scaleFactor,
                        (y + 1 + padding - originY) * scaleFactor,
                        paint,
                    )
                }
            }
        }
        is AotPatchCleanup -> {
            val patchBitmap = remember(cleanup.patchBase64) {
                decodeCachedAotPatch(cleanup.patchBase64)
            }
            val region = cleanup.region
            if (patchBitmap != null) {
                Image(
                    bitmap = patchBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier
                        .offset((region.x * scaleFactor).pxToDp(), (region.y * scaleFactor).pxToDp())
                        .requiredSize(
                            (region.width * scaleFactor).pxToDp(),
                            (region.height * scaleFactor).pxToDp(),
                        ),
                )
            } else {
                val fallbackColor = block.backgroundColor?.let { Color(it) } ?: Color.White
                if (fallbackColor.alpha > 0f) {
                    Box(
                        modifier = Modifier
                            .offset((region.x * scaleFactor).pxToDp(), (region.y * scaleFactor).pxToDp())
                            .requiredSize(
                                (region.width * scaleFactor).pxToDp(),
                                (region.height * scaleFactor).pxToDp(),
                            )
                            .background(fallbackColor),
                    )
                }
            }
        }
        NoCleanup -> Unit
    }
}

private val aotPatchBitmapCache: MutableMap<String, androidx.compose.ui.graphics.ImageBitmap> =
    java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, androidx.compose.ui.graphics.ImageBitmap>(32, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, androidx.compose.ui.graphics.ImageBitmap>?,
            ): Boolean = size > 48
        },
    )

private fun decodeCachedAotPatch(patchBase64: String): androidx.compose.ui.graphics.ImageBitmap? {
    aotPatchBitmapCache[patchBase64]?.let { return it }
    val decoded = runCatching {
        val bytes = Base64.decode(patchBase64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull() ?: return null
    if (patchBase64.length <= 262_144) {
        aotPatchBitmapCache[patchBase64] = decoded
    }
    return decoded
}
