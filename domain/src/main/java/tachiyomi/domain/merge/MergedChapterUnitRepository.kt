package tachiyomi.domain.merge

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.merge.model.ContentType

interface MergedChapterUnitRepository {

    suspend fun claimPendingGroups(contentType: ContentType): List<Long>

    suspend fun releaseClaim(groupId: Long)

    suspend fun isStale(contentType: ContentType, groupId: Long): Boolean

    suspend fun getRankings(): Map<Long, String>

    suspend fun getStitch(contentType: ContentType, groupId: Long): List<ChapterUnit>

    suspend fun replaceGroup(contentType: ContentType, groupId: Long, stitch: GroupStitch, ranking: String?)

    suspend fun getGroupCounts(contentType: ContentType): Map<Long, MergedGroupCounts>

    fun getGroupCountsAsFlow(contentType: ContentType): Flow<Map<Long, MergedGroupCounts>>

    suspend fun getDownloadUnits(contentType: ContentType, ownerIds: Set<Long>): Map<Long, List<DownloadUnitRow>>

    fun getStitchWritesAsFlow(): Flow<Unit>

    fun getCopiesAsFlow(contentType: ContentType, chapterIds: Collection<Long>): Flow<Map<Long, List<ChapterCopyRow>>>

    fun getRecognizedChapterCountsAsFlow(): Flow<Map<Long, Long>>

    data class GroupStitch(
        val units: List<StoredUnit>,
        val recognizedCounts: Map<Long, Long>,
    )

    data class StoredUnit(
        val chapterId: Long,
        val unit: Int?,
        val copyOrder: Int,
        val chapterName: String,
        val chapterNumber: Double,
        val sourceOrder: Long,
    )
}
