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
fun MediaFilterChips(
    selectedMedia: String,
    onSelectMedia: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filters = listOf(
        "ALL" to "Todos",
        "MANGA" to stringResource(MR.strings.browse_media_manga),
        "ANIME" to stringResource(MR.strings.browse_media_anime),
        "NOVEL" to "Novels",
    )
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        filters.forEachIndexed { index, (key, label) ->
            val isSelected = selectedMedia == key
            SegmentedButton(
                checked = isSelected,
                onCheckedChange = { onSelectMedia(key) },
                shape = SegmentedButtonDefaults.itemShape(
                    index,
                    filters.size,
                ),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
