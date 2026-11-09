package eu.kanade.domain.merge

import tachiyomi.domain.merge.MergedChapterUnitRepository
import tachiyomi.domain.merge.MergedChapters
import tachiyomi.domain.merge.model.ContentType

interface MergedGroupStitcher {

    val contentType: ContentType

    suspend fun stitch(groupId: Long): MergedChapterUnitRepository.GroupStitch

    suspend fun trunkNumbers(groupId: Long): Map<Long, Double>

    suspend fun rankings(): Map<Long, String>
}

fun <T> storedUnitsOf(
    chapters: List<T>,
    merged: MergedChapters<T>,
    id: (T) -> Long,
    name: (T) -> String,
    number: (T) -> Double,
    order: (T) -> Long,
): List<MergedChapterUnitRepository.StoredUnit> {
    val placed = merged.units.associateBy { it.chapterId }
    return chapters.map { chapter ->
        val unit = placed[id(chapter)]
        MergedChapterUnitRepository.StoredUnit(
            chapterId = id(chapter),
            unit = unit?.unit,
            copyOrder = unit?.copyOrder ?: 0,
            chapterName = name(chapter),
            chapterNumber = number(chapter),
            sourceOrder = order(chapter),
        )
    }
}
