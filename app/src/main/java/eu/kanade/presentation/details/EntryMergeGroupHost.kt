package eu.kanade.presentation.details

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import eu.kanade.domain.merge.EntryMergeManager
import eu.kanade.domain.merge.GroupChapterFlags
import eu.kanade.domain.merge.MergeScope
import tachiyomi.core.common.util.lang.launchIO
import eu.kanade.presentation.components.ManageMergeSourceRow

typealias EntryMergeSource = ManageMergeSourceRow

data class ClearDownloadsTarget(val sourceName: String?, val ids: List<Long>)

data class DetailsRemoval(val openedId: Long, val groupIds: List<Long>, val selectedId: Long?) {
    val offersGroup: Boolean get() = selectedId == null && groupIds.size > 1

    fun targets(removeGrouped: Boolean): List<Long> =
        if (offersGroup && !removeGrouped) listOf(openedId) else viewedMembers(groupIds, selectedId, openedId)
}

fun viewedMembers(groupIds: List<Long>, selectedId: Long?, openedId: Long): List<Long> = when {
    selectedId != null -> listOf(selectedId)
    groupIds.size > 1 -> groupIds
    else -> listOf(openedId)
}

class EntryMergeGroupHost(
    private val mergeManager: EntryMergeManager,
    initialIds: LongArray,
    private val anchorChanges: Flow<Long>,
    private val onSourceChange: (from: Long?, to: Long?) -> Unit,
    private val resolveSources: suspend (LongArray) -> List<EntryMergeSource>,
) {

    data class GroupState(val ids: LongArray, val selected: Long?) {
        val mergeScope: MergeScope get() = MergeScope.of(selected != null)

        fun <T> rowFlags(
            pooled: List<T>,
            shown: List<T>,
            stitch: List<ChapterUnit>,
            id: (T) -> Long,
            read: (T) -> Boolean,
            bookmark: (T) -> Boolean,
            onDisk: () -> Set<Long>,
        ) = GroupChapterFlags(mergeScope, pooled, shown, stitch, id, read, bookmark, onDisk)

        fun viewedIds(anchorId: Long): List<Long> = viewedMembers(ids.asList(), selected, anchorId)
    }

    private val _state = MutableStateFlow(GroupState(initialIds, selected = null))

    val state: StateFlow<GroupState> = _state.asStateFlow()

    val relatedIds: LongArray get() = _state.value.ids

    val selectedSource: Long? get() = _state.value.selected

    val selectedSourceChanges: Flow<Long?> = _state.map { it.selected }.distinctUntilChanged()

    private val _chips = MutableStateFlow<List<EntryMergeSource>>(emptyList())

    val chips: StateFlow<List<EntryMergeSource>> = _chips.asStateFlow()

    fun chipsOf(group: GroupState): List<EntryMergeSource> =
        _chips.value.filter { it.id in group.ids }.takeIf { it.size > 1 }.orEmpty()

    fun observe(scope: CoroutineScope) {
        scope.launchIO {
            combine(anchorChanges, mergeManager.relatedIdsChanges()) { anchor, _ -> anchor }
                .collectLatest { setRelated(mergeManager.computeRelatedIds(it)) }
        }
        scope.launchIO {
            _state.map { it.ids }.distinctUntilChanged().collectLatest { _chips.value = resolveSources(it) }
        }
    }

    fun setRelated(ids: LongArray) {
        _state.value = GroupState(ids, _state.value.selected?.takeIf { it in ids })
    }

    fun selectSource(entryId: Long?) {
        val current = _state.value
        if (entryId != null && entryId !in current.ids) return
        if (entryId == current.selected) return
        onSourceChange(current.selected, entryId)
        _state.update { it.copy(selected = entryId?.takeIf { id -> id in it.ids }) }
    }

    fun clearDownloadsTarget(anchorId: Long): ClearDownloadsTarget = _state.value.let { group ->
        ClearDownloadsTarget(
            sourceName = group.selected?.let { id -> chipsOf(group).firstOrNull { it.id == id }?.sourceName },
            ids = group.viewedIds(anchorId),
        )
    }

    fun removal(openedId: Long): DetailsRemoval =
        _state.value.let { DetailsRemoval(openedId, it.ids.toList(), it.selected) }

    suspend fun refresh(anchorId: Long) = setRelated(mergeManager.computeRelatedIds(anchorId))

    suspend fun <T> expandToGroup(
        chapters: List<T>,
        id: (T) -> Long,
        stitch: suspend () -> List<ChapterUnit>,
        load: suspend (Set<Long>) -> List<T>,
    ): List<T> = expand(MergeScope.Group, chapters, id, stitch, load)

    suspend fun <T> expandForDelete(
        chapters: List<T>,
        id: (T) -> Long,
        stitch: suspend () -> List<ChapterUnit>,
        load: suspend (Set<Long>) -> List<T>,
    ): List<T> = expand(_state.value.mergeScope, chapters, id, stitch, load)

    private suspend fun <T> expand(
        scope: MergeScope,
        chapters: List<T>,
        id: (T) -> Long,
        stitch: suspend () -> List<ChapterUnit>,
        load: suspend (Set<Long>) -> List<T>,
    ): List<T> {
        if (relatedIds.size <= 1) return chapters
        val held = chapters.mapTo(HashSet(), id)
        val wanted = scope.copiesOf(held, stitch()) - held
        return if (wanted.isEmpty()) chapters else chapters + load(wanted)
    }
}
