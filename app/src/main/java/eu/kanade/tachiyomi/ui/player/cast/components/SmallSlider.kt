package eu.kanade.tachiyomi.ui.player.cast.components

import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SmallSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier,
    )
}
