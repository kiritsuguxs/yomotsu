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

            val customSizedRegion = block.layoutRegion?.takeIf {
                block.cleanupRegion == it
            }

            // When a speech balloon is detected or manually customized/resized, clean its safe inner
            // layout region to erase any un-OCR'd lines or line gaps completely.
            if ((block.balloonDetected && block.layoutRegion != null) || customSizedRegion != null) {
                val layout = (customSizedRegion ?: block.layoutRegion!!).clamped(pageWidth, pageHeight)
                val cornerRatio = if (block.balloonDetected) 0.35f else 0.15f
                val safeCornerRadius = minOf(layout.width, layout.height) * cornerRatio
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
            val width = cleanup.mask.pageWidth
            val height = cleanup.mask.pageHeight
            val paint = Paint().apply {
                color = block.backgroundColor?.let { Color(it) } ?: Color.White
                isAntiAlias = false
            }
            val padding = (width * 0.003f).coerceAtLeast(2f)
            Canvas(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(
                        (pageWidth * scaleFactor).pxToDp(),
                        (pageHeight * scaleFactor).pxToDp(),
                    ),
            ) {
                cleanup.mask.forEachRun(width, height) { y, x, length ->
                    drawContext.canvas.drawRect(
                        (x - padding) * scaleFactor,
                        (y - padding) * scaleFactor,
                        (x + length + padding) * scaleFactor,
                        (y + 1 + padding) * scaleFactor,
                        paint,
                    )
                }
            }
        }
        is AotPatchCleanup -> {
            val patchBitmap = remember(cleanup.patchBase64) {
                runCatching {
                    val bytes = Base64.decode(cleanup.patchBase64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull()
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
