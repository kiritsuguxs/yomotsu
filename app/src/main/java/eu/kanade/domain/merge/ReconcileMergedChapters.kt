package eu.kanade.domain.merge

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.merge.MergedChapterUnitRepository
import tachiyomi.domain.merge.model.ContentType

class ReconcileMergedChapters(
    private val repository: MergedChapterUnitRepository,
    private val stitchers: Set<MergedGroupStitcher>,
) {

    private val rebuildLock = Mutex()

    suspend fun await() {
        stitchers.forEach { stitcher ->
            val rankings = stitcher.rankings()
            val stored = repository.getRankings()
            val reranked = rankings.filter { (groupId, ranking) -> stored[groupId] != ranking }.keys
            (repository.claimPendingGroups(stitcher.contentType) + reranked).distinct().forEach { groupId ->
                rebuildIfStale(stitcher, groupId, rankings[groupId])
            }
        }
    }

    suspend fun <T> afterPass(pass: suspend () -> T): T =
        try {
            pass()
        } finally {
            withContext(NonCancellable) {
                try {
                    await()
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Merged chapters were not reconciled after a pass" }
                }
            }
        }

    suspend fun currentStitch(contentType: ContentType, groupId: Long): List<tachiyomi.domain.merge.ChapterUnit> {
        stitchers.firstOrNull { it.contentType == contentType }?.let { stitcher ->
            rebuildIfStale(stitcher, groupId, stitcher.rankings()[groupId])
        }
        return repository.getStitch(contentType, groupId)
    }

    private suspend fun rebuildIfStale(stitcher: MergedGroupStitcher, groupId: Long, ranking: String?) =
        rebuildLock.withLock {
            val contentType = stitcher.contentType
            if (repository.isStale(contentType, groupId) || repository.getRankings()[groupId] != ranking) {
                repository.replaceGroup(contentType, groupId, stitcher.stitch(groupId), ranking)
            }
            repository.releaseClaim(groupId)
        }
}
