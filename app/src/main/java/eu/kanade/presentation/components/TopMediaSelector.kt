package eu.kanade.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun TopMediaSelector(
    selectedMedia: String,
    onSelectMedia: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        "Manga" to "📖 ${stringResource(MR.strings.browse_media_manga)}",
        "Anime" to "🎬 ${stringResource(MR.strings.browse_media_anime)}",
        "Novel" to "📚 ${stringResource(MR.strings.browse_media_novel)}",
    )
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        items.forEachIndexed { index, (mediaKey, label) ->
            val isSelected = selectedMedia == mediaKey
            SegmentedButton(
                checked = isSelected,
                onCheckedChange = { onSelectMedia(mediaKey) },
                shape = SegmentedButtonDefaults.itemShape(
                    index,
                    items.size,
                ),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
