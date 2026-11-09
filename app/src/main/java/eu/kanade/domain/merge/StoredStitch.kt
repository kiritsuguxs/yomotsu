package eu.kanade.domain.merge

import tachiyomi.domain.merge.ChapterUnit
import tachiyomi.domain.merge.stampedReadingOrder

fun <T> renderStoredStitch(chapters: List<T>, stitch: List<ChapterUnit>, id: (T) -> Long): List<T> {
    if (stitch.isEmpty()) return chapters
    val available = chapters.associateBy(id)
    return stitch.asSequence()
        .filter { it.chapterId in available }
        .bestCopyByUnit()
        .toSortedMap()
        .values
        .mapNotNull(available::get)
}

private fun Sequence<ChapterUnit>.bestCopyByUnit(): Map<Int, Long> =
    groupBy { it.unit }.mapValues { (_, copies) -> copies.minBy { it.copyOrder }.chapterId }

fun <T> renderMergedReadingOrder(
    chapters: List<T>,
    stitch: List<ChapterUnit>,
    id: (T) -> Long,
    restamp: (T, Long) -> T,
): List<T> = if (stitch.isEmpty()) chapters else stampedReadingOrder(renderStoredStitch(chapters, stitch, id), restamp)

fun <T> flaggedOnAnotherSource(
    chapters: List<T>,
    shown: List<T>,
    stitch: List<ChapterUnit>,
    id: (T) -> Long,
    flag: (T) -> Boolean,
): Set<Long> {
    if (stitch.isEmpty()) return emptySet()
    val flaggedIds = chapters.asSequence().filter(flag).mapTo(HashSet(), id)
    if (flaggedIds.isEmpty()) return emptySet()
    val flaggedUnits = stitch.asSequence().filter { it.chapterId in flaggedIds }.mapTo(HashSet()) { it.unit }
    if (flaggedUnits.isEmpty()) return emptySet()
    val unitByChapterId = stitch.associate { it.chapterId to it.unit }
    return shown.asSequence()
        .filter { !flag(it) && unitByChapterId[id(it)] in flaggedUnits }
        .mapTo(HashSet(), id)
}

enum class MergeScope {
    Group,
    Source,
    ;

    fun copiesIn(stitch: List<ChapterUnit>): List<ChapterUnit> = if (this == Group) stitch else emptyList()

    fun copiesOf(chapterIds: Set<Long>, stitch: List<ChapterUnit>): Set<Long> =
        expandToUnits(chapterIds, copiesIn(stitch))

    companion object {
        fun of(sourceScoped: Boolean): MergeScope = if (sourceScoped) Source else Group
    }
}

class CopyToOpen<T>(
    scope: MergeScope,
    chapters: List<T>,
    stitch: List<ChapterUnit>,
    private val id: (T) -> Long,
    private val onDisk: Set<Long>,
    private val fetched: DownloadTargets = DownloadTargets.OWN,
) {
    private val byId = chapters.associateBy(id)
    private val unitOf = scope.copiesIn(stitch).associate { it.chapterId to it.unit }
    private val bestOnDisk = scope.copiesIn(stitch).asSequence()
        .filter { it.chapterId in onDisk && it.chapterId in byId }
        .bestCopyByUnit()

    fun idOf(chapterId: Long): Long = if (chapterId in onDisk) {
        chapterId
    } else {
        unitOf[chapterId]?.let(bestOnDisk::get)
            ?: fetched.idOf(chapterId)?.takeIf { it in byId }
            ?: chapterId
    }

    fun isOnDisk(chapterId: Long): Boolean = idOf(chapterId) in onDisk

    fun inPlaceOf(shown: List<T>, keepPlace: (copy: T, row: T) -> T): List<T> = shown.map { row ->
        val copyId = idOf(id(row))
        if (copyId == id(row)) row else keepPlace(byId.getValue(copyId), row)
    }
}

data class DownloadTargets(
    private val moved: Map<Long, Long> = emptyMap(),
    private val unavailable: Set<Long> = emptySet(),
) {
    fun idOf(chapterId: Long): Long? = if (chapterId in unavailable) null else moved[chapterId] ?: chapterId

    fun <D : Any> queuedFor(chapterId: Long, queued: (Long) -> D?): D? =
        queued(chapterId) ?: idOf(chapterId)?.let(queued)

    suspend fun <T> of(rows: List<T>, id: (T) -> Long, load: suspend (Set<Long>) -> List<T>): List<T> {
        val missing = rows.mapNotNullTo(HashSet()) { idOf(id(it)) } - rows.mapTo(HashSet(), id)
        return of(rows, if (missing.isEmpty()) rows else rows + load(missing), id)
    }

    fun <T> of(rows: List<T>, copies: List<T>, id: (T) -> Long): List<T> {
        val byId = copies.associateBy(id)
        return rows.mapNotNull { idOf(id(it)) }.distinct().mapNotNull(byId::get)
    }

    companion object {
        val OWN = DownloadTargets()

        fun <T> of(
            scope: MergeScope,
            pooled: List<T>,
            shown: List<T>,
            stitch: List<ChapterUnit>,
            id: (T) -> Long,
            isInstalled: (T) -> Boolean,
        ): DownloadTargets {
            val installed = pooled.asSequence().filter(isInstalled).mapTo(HashSet(), id)
            val copies = scope.copiesIn(stitch)
            val unitOf = copies.associate { it.chapterId to it.unit }
            val bestInstalled = copies.asSequence().filter { it.chapterId in installed }.bestCopyByUnit()
            val moved = HashMap<Long, Long>()
            val unavailable = HashSet<Long>()
            shown.asSequence().map(id).filterNot { it in installed }.forEach { rowId ->
                val copy = unitOf[rowId]?.let(bestInstalled::get)
                if (copy == null) unavailable += rowId else moved[rowId] = copy
            }
            return DownloadTargets(moved, unavailable)
        }
    }
}

data class MergedChapterKey(val groupId: Long, val unit: Int)

class CollapsedArrivals(val announced: Set<Long>, val keyOf: Map<Long, MergedChapterKey>) {
    fun dedupeKey(chapterId: Long): Any = keyOf[chapterId] ?: chapterId
}

fun <T> collapseNewChapters(
    newByEntry: Map<Long, List<T>>,
    groupOf: Map<Long, Long>,
    stitches: Map<Long, List<ChapterUnit>>,
    id: (T) -> Long,
): CollapsedArrivals {
    val announced = HashSet<Long>()
    val keyOf = HashMap<Long, MergedChapterKey>()
    val pooled = HashMap<Long, MutableList<T>>()
    newByEntry.forEach { (entryId, chapters) ->
        val groupId = groupOf[entryId]
        if (groupId == null) {
            chapters.mapTo(announced, id)
        } else {
            pooled.getOrPut(groupId) { mutableListOf() } += chapters
        }
    }
    pooled.forEach { (groupId, chapters) ->
        val stitch = stitches[groupId].orEmpty()
        if (stitch.isEmpty()) {
            chapters.mapTo(announced, id)
            return@forEach
        }
        val arrived = chapters.mapTo(HashSet(), id)
        stitch.forEach { if (it.chapterId in arrived) keyOf[it.chapterId] = MergedChapterKey(groupId, it.unit) }
        val alreadyHad = stitch.asSequence().filter { it.chapterId !in arrived }.mapTo(HashSet()) { it.unit }
        arrived.filterTo(announced) { it !in keyOf }
        announced += stitch.asSequence()
            .filter { it.chapterId in arrived && it.unit !in alreadyHad }
            .bestCopyByUnit()
            .values
    }
    return CollapsedArrivals(announced, keyOf)
}

suspend fun <T> collapseNewChapters(
    newByEntry: Map<Long, List<T>>,
    memberships: Map<Long, Long>,
    stitchOf: suspend (groupId: Long) -> List<ChapterUnit>,
    id: (T) -> Long,
): CollapsedArrivals {
    val groupOf = memberships.filterKeys { it in newByEntry }
    val stitches = groupOf.values.distinct().associateWith { stitchOf(it) }
    return collapseNewChapters(newByEntry, groupOf, stitches, id)
}

fun expandToUnits(chapterIds: Set<Long>, stitch: List<ChapterUnit>): Set<Long> {
    if (stitch.isEmpty()) return chapterIds
    val units = stitch.asSequence().filter { it.chapterId in chapterIds }.mapTo(HashSet()) { it.unit }
    if (units.isEmpty()) return chapterIds
    return stitch.asSequence().filter { it.unit in units }.mapTo(HashSet(chapterIds)) { it.chapterId }
}
