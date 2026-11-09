package eu.kanade.domain.manga

import eu.kanade.domain.merge.GroupChapterFlags
import eu.kanade.domain.merge.MergeScope
import eu.kanade.domain.merge.ReconcileMergedChapters
import eu.kanade.domain.merge.renderMergedReadingOrder
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.ChapterAggregation
import tachiyomi.domain.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.merge.ChapterUnit
import tachiyomi.domain.merge.model.ContentType
import tachiyomi.domain.source.service.SourceManager

class MergedChapterProvider(
    private val getMangaWithChapters: GetMangaWithChapters,
    private val mergeManager: MangaMergeManager,
    private val sourceManager: SourceManager,
    private val libraryPreferences: LibraryPreferences,
    private val reconcile: ReconcileMergedChapters,
) {

    class Group(
        val mangaById: Map<Long, Manga>,
        val chapters: List<Chapter>,
        val sourceNameByMangaId: Map<Long, String>,
        val stitch: List<ChapterUnit> = emptyList(),
        val pooledChapters: List<Chapter> = chapters,
    ) {
        val isMerged: Boolean get() = mangaById.size > 1
    }

    suspend fun load(anchor: Manga): Group {
        val ids = mergeManager.computeRelatedIds(anchor.id)
        if (ids.size <= 1) {
            return Group(
                mangaById = mapOf(anchor.id to anchor),
                chapters = getMangaWithChapters.awaitChapters(anchor.id, applyScanlatorFilter = true),
                sourceNameByMangaId = emptyMap(),
            )
        }
        val mangaById = ids.associateWith { getMangaWithChapters.awaitManga(it) }
        val chaptersBySource = ids.associateWith { getMangaWithChapters.awaitChapters(it, applyScanlatorFilter = true) }
        val sourceNameByMangaId = mangaById.mapValues { sourceManager.getOrStub(it.value.source).name }
        val pooled = chaptersBySource.values.flatten()
        val stitch = stitchOf(anchor.id)
        val merged = merged(pooled, stitch)
        return Group(
            mangaById = mangaById,
            chapters = merged,
            sourceNameByMangaId = sourceNameByMangaId,
            stitch = stitch,
            pooledChapters = pooled,
        )
    }

    suspend fun stitchOf(anchorId: Long): List<ChapterUnit> {
        val manga = getMangaWithChapters.awaitManga(anchorId)
        val source = sourceManager.get(manga.source)
        val contentType = when {
            source is eu.kanade.tachiyomi.animesource.AnimeSource || source?.isAnime == true -> ContentType.ANIME
            source is eu.kanade.tachiyomi.source.INovelSource || source?.isNovel == true -> ContentType.NOVEL
            else -> ContentType.MANGA
        }
        return mergeManager.groupIdOf(anchorId)?.let { reconcile.currentStitch(contentType, it) }.orEmpty()
    }

    fun merged(chapters: List<Chapter>, stitch: List<ChapterUnit>): List<Chapter> =
        renderMergedReadingOrder(chapters, stitch, { it.id }) { chapter, order -> chapter.copy(sourceOrder = order) }

    fun rankedMemberIds(
        chaptersBySource: Map<Long, List<Chapter>>,
        sourceIdByManga: Map<Long, Long>,
        memberRanking: List<Long>,
    ): List<Long> = ChapterAggregation.rankedMemberIds(
        chaptersBySource,
        sourceIdByManga,
        libraryPreferences.preferredMangaSources.get(),
        memberRanking,
    )
}

fun MergedChapterProvider.Group?.groupFlags(
    shown: List<Chapter>,
    onDisk: (pooled: List<Chapter>) -> Set<Long>,
): GroupChapterFlags<Chapter> {
    val pooled = this?.pooledChapters ?: shown
    return GroupChapterFlags(
        MergeScope.Group,
        pooled,
        shown,
        this?.stitch.orEmpty(),
        { it.id },
        { it.read },
        { it.bookmark },
    ) { onDisk(pooled) }
}
