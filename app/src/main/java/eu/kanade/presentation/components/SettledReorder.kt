package eu.kanade.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun <T> rememberSettledReorder(
    items: SnapshotStateList<T>,
    listState: LazyListState,
    keyOf: (T) -> Any,
    contentPadding: PaddingValues = PaddingValues(),
    onSettled: (List<T>) -> Unit,
): ReorderableLazyListState {
    var didDrag by remember { mutableStateOf(false) }
    val currentOnSettled by rememberUpdatedState(onSettled)
    val state = rememberReorderableLazyListState(listState, contentPadding) { from, to ->
        val fromIndex = items.indexOfFirst { keyOf(it) == from.key }
        val toIndex = items.indexOfFirst { keyOf(it) == to.key }
        if (fromIndex == -1 || toIndex == -1) return@rememberReorderableLazyListState
        items.add(toIndex, items.removeAt(fromIndex))
        didDrag = true
    }
    LaunchedEffect(state.isAnyItemDragging) {
        if (!state.isAnyItemDragging && didDrag) {
            didDrag = false
            currentOnSettled(items.toList())
        }
    }
    return state
}
