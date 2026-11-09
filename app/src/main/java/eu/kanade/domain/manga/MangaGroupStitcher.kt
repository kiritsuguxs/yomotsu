package eu.kanade.domain.manga

import eu.kanade.domain.merge.MergedGroupStitcher
import eu.kanade.domain.merge.storedUnitsOf
import tachiyomi.domain.merge.rankingStamps
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.ChapterAggregation
import tachiyomi.domain.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.merge.MergeGroupRepository
import tachiyomi.domain.merge.MergedChapterUnitRepository.GroupStitch
import tachiyomi.domain.merge.model.ContentType
import tachiyomi.domain.source.service.SourceManager

open class MangaGroupStitcher(
    private val mergeGroupRepository: MergeGroupRepository,
    private val getMangaWithChapters: GetMangaWithChapters,
    private val mergeManager: MangaMergeManager,
    private val sourceManager: SourceManager,
    private val libraryPreferences: LibraryPreferences,
    override val contentType: ContentType = ContentType.MANGA,
) : MergedGroupStitcher {

    override suspend fun stitch(groupId: Long): GroupStitch {
        val group = load(groupId) ?: return GroupStitch(emptyList(), emptyMap())
        val merged = ChapterAggregation.merge(
            group.chaptersBySource.mapValues { (_, chapters) -> chapters.sortedBy { it.sourceOrder } },
            group.sourceIdByManga,
            libraryPreferences.preferredMangaSources.get(),
            emptySet(),
            group.memberRanking,
        )
        val units = storedUnitsOf(
            chapters = group.chaptersBySource.values.flatten(),
            merged = merged,
            id = { it.id },
            name = { it.name },
            number = { it.chapterNumber },
            order = { it.sourceOrder },
        )
        val counts = group.chaptersBySource.mapValues { (_, chapters) ->
            ChapterAggregation.recognizedChapterCount(chapters).toLong()
        }
        return GroupStitch(units, counts)
    }

    override suspend fun trunkNumbers(groupId: Long): Map<Long, Double> {
        val group = load(groupId) ?: return emptyMap()
        val trunk = ChapterAggregation.trunkId(
            group.chaptersBySource,
            group.sourceIdByManga,
            libraryPreferences.preferredMangaSources.get(),
            group.memberRanking,
        )
        return group.chaptersBySource[trunk].orEmpty().associate { it.id to it.chapterNumber }
    }

    override suspend fun rankings(): Map<Long, String> =
        rankingStamps(
            mergeGroupRepository.getRankedMangaMembers(),
            libraryPreferences.preferredMangaSources.get(),
        )

    private class Inputs(
        val chaptersBySource: Map<Long, List<Chapter>>,
        val sourceIdByManga: Map<Long, Long>,
        val memberRanking: List<Long>,
    )

    private suspend fun load(groupId: Long): Inputs? {
        val members = mergeGroupRepository.getFavoriteMembers(contentType, groupId)
        if (members.isEmpty()) return null
        return Inputs(
            chaptersBySource = members.associateWith { getMangaWithChapters.awaitChapters(it) },
            sourceIdByManga = members.associateWith { getMangaWithChapters.awaitManga(it).source },
            memberRanking = mergeManager.overrideRankingMemberIds(members.first()),
        )
    }
}

class NovelGroupStitcher(
    mergeGroupRepository: MergeGroupRepository,
    getMangaWithChapters: GetMangaWithChapters,
    mergeManager: MangaMergeManager,
    sourceManager: SourceManager,
    libraryPreferences: LibraryPreferences,
) : MangaGroupStitcher(
    mergeGroupRepository,
    getMangaWithChapters,
    mergeManager,
    sourceManager,
    libraryPreferences,
    ContentType.NOVEL,
)

class AnimeGroupStitcher(
    mergeGroupRepository: MergeGroupRepository,
    getMangaWithChapters: GetMangaWithChapters,
    mergeManager: MangaMergeManager,
    sourceManager: SourceManager,
    libraryPreferences: LibraryPreferences,
) : MangaGroupStitcher(
    mergeGroupRepository,
    getMangaWithChapters,
    mergeManager,
    sourceManager,
    libraryPreferences,
    ContentType.ANIME,
)
