package eu.kanade.presentation.selection

import kotlin.math.abs

data class SelectionState<T>(
    val selection: Set<T> = emptySet(),
    val anchor: T? = null,
) {
    val isEmpty: Boolean get() = selection.isEmpty()
    operator fun contains(item: T): Boolean = item in selection
    val size: Int get() = selection.size
}

object EntrySelection {

    fun <T> toggle(state: SelectionState<T>, item: T): SelectionState<T> {
        val selection = if (item in state.selection) state.selection - item else state.selection + item
        return SelectionState(selection, item.takeIf { selection.isNotEmpty() })
    }

    fun <T> range(state: SelectionState<T>, item: T, ordered: List<T>): SelectionState<T> {
        val to = ordered.indexOf(item)
        if (to < 0) return state
        val from = state.anchor?.let(ordered::indexOf) ?: -1
        val selection = if (from < 0) {
            state.selection + item
        } else {
            state.selection + ordered.subList(minOf(from, to), maxOf(from, to) + 1)
        }
        return SelectionState(selection, item)
    }

    fun <T> rangeOrToggle(state: SelectionState<T>, item: T, ordered: List<T>): SelectionState<T> =
        if (item in state.selection) toggle(state, item) else range(state, item, ordered)

    fun <T> clear(): SelectionState<T> = SelectionState()
}
