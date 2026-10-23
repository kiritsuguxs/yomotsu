package eu.kanade.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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
        shape: Shape = MaterialTheme.shapes.extraSmall,
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null,
    ) {
        val clickModifier = if (onClick != null || onLongClick != null) {
            Modifier.combinedClickable(
                role = Role.Button,
                onClick = { onClick?.invoke() },
                onLongClick = onLongClick,
            )
        } else {
            Modifier
        }

        if (this == Panorama) {
            var isPortraitCover by remember(data) { mutableStateOf(true) }

            Box(
                modifier = modifier
                    .aspectRatio(ratio)
                    .clip(shape)
                    .background(CoverPlaceholderColor)
                    .then(clickModifier),
                contentAlignment = Alignment.Center,
            ) {
                if (isPortraitCover) {
                    // Ambient blurred fill so the 16:9 panorama frame is 100% filled edge-to-edge
                    AsyncImage(
                        model = data,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(18.dp),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.18f)),
                    )
                }

                // Foreground cover: 100% of the original cover without cutting off logo/heads/legs
                AsyncImage(
                    model = data,
                    placeholder = ColorPainter(CoverPlaceholderColor),
                    error = rememberResourceBitmapPainter(id = R.drawable.cover_error),
                    contentDescription = contentDescription,
                    onSuccess = { state ->
                        val w = state.result.image.width
                        val h = state.result.image.height
                        if (w > 0 && h > 0) {
                            isPortraitCover = h > w
                        }
                    },
                    modifier = if (isPortraitCover) {
                        Modifier
                            .fillMaxHeight()
                            .clip(MaterialTheme.shapes.small)
                    } else {
                        Modifier.fillMaxSize()
                    },
                    contentScale = if (isPortraitCover) ContentScale.Fit else ContentScale.Crop,
                )
            }
        } else {
            AsyncImage(
                model = data,
                placeholder = ColorPainter(CoverPlaceholderColor),
                error = rememberResourceBitmapPainter(id = R.drawable.cover_error),
                contentDescription = contentDescription,
                modifier = modifier
                    .aspectRatio(ratio)
                    .clip(shape)
                    .then(clickModifier),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private val CoverPlaceholderColor = Color(0x1F888888)
