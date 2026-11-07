package eu.kanade.presentation.manga.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Scale
import coil3.size.Size
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.tachiyomi.R

enum class MangaCover(val ratio: Float) {
    Square(1f / 1f),
    Book(2f / 3f),
    Panorama(16f / 9f),
    ;

    @Composable
    operator fun invoke(
        data: Any?,
        modifier: Modifier = Modifier,
        contentDescription: String = "",
        shape: Shape = RoundedCornerShape(12.dp),
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null,
    ) {
        val context = LocalContext.current
        var srcAspect by remember { mutableStateOf<Float?>(null) }
        val model = if (this == Panorama && data !is ImageRequest) {
            remember(data) {
                ImageRequest.Builder(context)
                    .data(data)
                    .size(Size.ORIGINAL)
                    .scale(Scale.FIT)
                    .crossfade(false)
                    .build()
            }
        } else {
            data
        }
        AsyncImage(
            model = model,
            placeholder = ColorPainter(CoverPlaceholderColor),
            error = rememberResourceBitmapPainter(id = R.drawable.cover_error),
            contentDescription = contentDescription,
            onSuccess = { state ->
                val w = state.result.image.width
                val h = state.result.image.height
                if (w > 0 && h > 0) {
                    srcAspect = w.toFloat() / h.toFloat()
                }
            },
            modifier = modifier
                .aspectRatio(ratio)
                .clip(shape)
                .then(
                    if (this == Panorama) {
                        Modifier.drawWithContent {
                            val aspect = srcAspect
                            if (aspect != null && aspect > 0f && size.width > 0f && size.height > 0f) {
                                val dstAspect = size.width / size.height
                                val sx = if (aspect < dstAspect) dstAspect / aspect else 1f
                                val sy = if (aspect > dstAspect) aspect / dstAspect else 1f
                                scale(scaleX = sx, scaleY = sy, pivot = center) {
                                    drawContent()
                                }
                            } else {
                                drawContent()
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (onClick != null || onLongClick != null) {
                        Modifier.combinedClickable(
                            role = Role.Button,
                            onClick = { onClick?.invoke() },
                            onLongClick = onLongClick,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentScale = if (this == Panorama) ContentScale.Fit else ContentScale.Crop,
        )
    }
}

private val CoverPlaceholderColor = Color(0x1F888888)
