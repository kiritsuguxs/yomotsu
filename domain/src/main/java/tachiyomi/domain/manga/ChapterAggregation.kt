package tachiyomi.domain.manga

import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.merge.MergedChapterOrder
import tachiyomi.domain.merge.MergedChapters
import tachiyomi.domain.merge.sourcePriority
import tachiyomi.domain.merge.stampedReadingOrder
import tachiyomi.domain.merge.stitchOrder
import tachiyomi.domain.merge.trunkOrder
import tachiyomi.domain.merge.unstitchedChapters

object ChapterAggregation {

    fun merge(
        chaptersBySource: Map<Long, List<Chapter>>,
        sourceIdByManga: Map<Long, Long> = emptyMap(),
        preferredSourceIds: List<Long> = emptyList(),
        gallerySourceMangaIds: Set<Long> = emptySet(),
        memberRanking: List<Long> = emptyList(),
    ): MergedChapters<Chapter> {
        if (chaptersBySource.size <= 1) {
            return unstitchedChapters(chaptersBySource.values.firstOrNull().orEmpty()) { it.id }
        }

        val ranked = walkOrder(chaptersBySource, sourceIdByManga, preferredSourceIds, memberRanking)

        val order = MergedChapterOrder<Chapter> { chapter ->
            chapter.chapterNumber.toFloat()
                .takeIf { chapter.isRecognizedNumber && chapter.mangaId !in gallerySourceMangaIds }
        }
        ranked.forEachIndexed { index, source ->
            order.startSource()
            val isTrunk = index == 0
            val isGallery = source.mangaId in gallerySourceMangaIds
            for (chapter in source.chapters) {
                if (isGallery) {
                    order.place(chapter)
                    continue
                }
                if (!chapter.isRecognizedNumber) {
                    if (isTrunk) order.place(chapter)
                    continue
                }
                val existing = order.positionOf(chapter)
                if (existing >= 0) {
                    order.followTo(existing, chapter)
                    continue
                }
                order.place(chapter)
            }
        }
        val stitched = order.result()
        val merged = stampedReadingOrder(stitched.merged) { chapter, position -> chapter.copy(sourceOrder = position) }
        return MergedChapters(merged, stitched.units { it.id })
    }

    fun rankedMemberIds(
        chaptersBySource: Map<Long, List<Chapter>>,
        sourceIdByManga: Map<Long, Long> = emptyMap(),
        preferredSourceIds: List<Long> = emptyList(),
        memberRanking: List<Long> = emptyList(),
    ): List<Long> = rank(chaptersBySource, sourceIdByManga, preferredSourceIds, memberRanking).map { it.mangaId }

    fun trunkId(
        chaptersBySource: Map<Long, List<Chapter>>,
        sourceIdByManga: Map<Long, Long>,
        preferredSourceIds: List<Long>,
        memberRanking: List<Long>,
    ): Long? = walkOrder(chaptersBySource, sourceIdByManga, preferredSourceIds, memberRanking).firstOrNull()?.mangaId

    private fun walkOrder(
        chaptersBySource: Map<Long, List<Chapter>>,
        sourceIdByManga: Map<Long, Long>,
        preferredSourceIds: List<Long>,
        memberRanking: List<Long>,
    ): List<RankedSource> = stitchOrder(rank(chaptersBySource, sourceIdByManga, preferredSourceIds, memberRanking)) {
        it.chapters.isNotEmpty()
    }

    private fun rank(
        chaptersBySource: Map<Long, List<Chapter>>,
        sourceIdByManga: Map<Long, Long>,
        preferredSourceIds: List<Long>,
        memberRanking: List<Long>,
    ): List<RankedSource> = chaptersBySource.entries
        .map { (mangaId, chapters) ->
            val prefRank = sourcePriority(mangaId, sourceIdByManga[mangaId], preferredSourceIds, memberRanking)
            RankedSource(mangaId, chapters, recognizedChapterCount(chapters), prefRank)
        }
        .sortedWith(trunkOrder({ it.prefRank }, { it.distinctCount.toLong() }, { it.mangaId }))

    fun recognizedChapterCount(chapters: List<Chapter>): Int =
        distinctChapterNumberCount(chapters.filter { it.isRecognizedNumber }.map { it.chapterNumber })

    fun distinctChapterNumberCount(numbers: List<Double>): Int = numbers.distinctBy { it.toFloat() }.size

    private class RankedSource(
        val mangaId: Long,
        val chapters: List<Chapter>,
        val distinctCount: Int,
        val prefRank: Int,
    )
}
