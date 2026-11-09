package eu.kanade.presentation.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.SwapCalls
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.selection.EntrySelection
import eu.kanade.presentation.selection.SelectionState
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.selectedBackground

data class ManageMergeSourceRow(
    val id: Long,
    val sourceName: String,
    val subtitle: String? = null,
)

@Composable
fun ManageMergeSourcesDialog(
    sources: List<ManageMergeSourceRow>,
    isOverridden: Boolean,
    onDismissRequest: () -> Unit,
    onReorder: (orderedIds: List<Long>) -> Unit,
    onResetOrder: () -> Unit,
    onSplit: (List<Long>) -> Unit,
    onRemoveFromLibrary: (List<Long>) -> Unit,
    onRemoveAll: () -> Unit,
) {
    val items = remember { sources.toMutableStateList() }
    val listState = rememberLazyListState()
    var selectionMode by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(SelectionState<Long>()) }
    val selection = picked.selection
    var overridden by remember { mutableStateOf(isOverridden) }

    val reorderState = rememberSettledReorder(items, listState, keyOf = { it.id }) { settled ->
        overridden = true
        onReorder(settled.map { it.id })
    }

    fun exitSelection() {
        selectionMode = false
        picked = EntrySelection.clear()
    }

    fun toggle(id: Long) {
        picked = EntrySelection.toggle(picked, id)
        if (picked.isEmpty) selectionMode = false
    }

    fun rangeSelect(id: Long) {
        selectionMode = true
        picked = EntrySelection.rangeOrToggle(picked, id, items.map { it.id })
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(MR.strings.action_manage_sources),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { if (selectionMode) exitSelection() else selectionMode = true },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SelectAll,
                        contentDescription = stringResource(MR.strings.action_select_all),
                        tint = if (selectionMode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            LocalContentColor.current
                        },
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!overridden && !selectionMode) {
                    Text(
                        text = stringResource(MR.strings.merge_sources_reorder_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                ) {
                    items(items, key = { it.id }) { row ->
                        ReorderableItem(reorderState, key = row.id) {
                            SourceRow(
                                row = row,
                                isPrimary = items.firstOrNull()?.id == row.id,
                                isSelected = row.id in selection,
                                selectionMode = selectionMode,
                                onClick = { if (selectionMode) toggle(row.id) },
                                onLongClick = { rangeSelect(row.id) },
                                onSplit = { onSplit(listOf(row.id)) },
                                onRemove = { onRemoveFromLibrary(listOf(row.id)) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { if (selectionMode) exitSelection() else onDismissRequest() }) {
                    Text(
                        stringResource(
                            if (selectionMode) MR.strings.action_cancel else MR.strings.action_close,
                        ),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectionMode) {
                        TextButton(onClick = { onSplit(selection.toList()) }) {
                            Text(stringResource(MR.strings.merge_sources_split_action))
                        }
                        TextButton(onClick = { onRemoveFromLibrary(selection.toList()) }) {
                            Text(stringResource(MR.strings.merge_sources_remove_action))
                        }
                    } else {
                        if (overridden) {
                            TextButton(onClick = onResetOrder) {
                                Text(stringResource(MR.strings.merge_sources_reset_order_action))
                            }
                        }
                        TextButton(onClick = onRemoveAll) {
                            Text(stringResource(MR.strings.merge_sources_remove_all_action))
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ReorderableCollectionItemScope.SourceRow(
    row: ManageMergeSourceRow,
    isPrimary: Boolean,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSplit: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectedBackground(isSelected)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.DragHandle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .draggableHandle(),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.sourceName,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isPrimary) {
                    Text(
                        text = stringResource(MR.strings.merge_sources_primary),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            row.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!selectionMode) {
            IconButton(onClick = onSplit) {
                Icon(
                    imageVector = Icons.Outlined.SwapCalls,
                    contentDescription = stringResource(MR.strings.merge_sources_split_action),
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(MR.strings.merge_sources_remove_action),
                )
            }
        }
    }
}
