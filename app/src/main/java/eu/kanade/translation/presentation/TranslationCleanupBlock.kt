package eu.kanade.translation.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.dp
import eu.kanade.translation.model.DbnetMaskCleanup
import eu.kanade.translation.model.LegacyCleanup
import eu.kanade.translation.model.NoCleanup
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.clamped
import eu.kanade.translation.model.resolveCleanup

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

            // When a speech balloon is detected, clean its safe inner layout region to erase
            // any un-OCR'd lines or line gaps completely, preventing English text from leaking behind the translation.
            if (block.balloonDetected && block.layoutRegion != null) {
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

            cleanup.patches.forEach { patch ->
                val region = patch.region
                val padX = if (block.balloonDetected) maxOf(block.symWidth * 0.35f, (pageWidth * 0.006f).coerceAtLeast(4f)) else (pageWidth * 0.003f).coerceAtLeast(2f)
                val padY = if (block.balloonDetected) maxOf(block.symHeight * 0.28f, (pageWidth * 0.005f).coerceAtLeast(4f)) else (pageWidth * 0.003f).coerceAtLeast(2f)
                val adjustedX = (region.x - padX).coerceAtLeast(0f)
                val adjustedY = (region.y - padY).coerceAtLeast(0f)
                val adjustedWidth = minOf(pageWidth - adjustedX, region.width + padX * 2f)
                val adjustedHeight = minOf(pageHeight - adjustedY, region.height + padY * 2f)
                val safeRadius = if (patch.cornerRadius.isFinite()) patch.cornerRadius else 0f
                val cleanupShape = if (block.balloonDetected || (!block.balloonDetected && block.backgroundColor == null)) {
                    RoundedCornerShape((safeRadius * scaleFactor).pxToDp())
                } else {
                    RoundedCornerShape(minOf(3.dp, (safeRadius * scaleFactor).pxToDp()))
                }
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
        NoCleanup -> Unit
    }
}
