package eu.kanade.tachiyomi.ui.player.cast

import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import eu.kanade.tachiyomi.R
import tachiyomi.presentation.core.components.material.padding

@Composable
fun CastButton(
    isConnected: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = MaterialTheme.padding.small,
    verticalSpacing: Dp = MaterialTheme.padding.small,
    iconSize: Dp = 24.dp,
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }

    val mediaRouteButton = remember {
        MediaRouteButton(context).also { button ->
            CastButtonFactory.setUpMediaRouteButton(
                context,
                button,
            )
            button.visibility = View.GONE
        }
    }

    AndroidView(factory = { mediaRouteButton })

    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 24.dp),
                onClick = { mediaRouteButton.performClick() },
            )
            .padding(
                vertical = verticalSpacing,
                horizontal = horizontalSpacing,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(iconSize),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_cast_24dp),
                contentDescription = "Cast",
                modifier = Modifier.size(iconSize),
                tint = if (isConnected) MaterialTheme.colorScheme.primary else Color.White,
            )
        }
    }
}
