package tachiyomi.domain.merge

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.merge.model.ContentType
import tachiyomi.domain.merge.model.MemberCover
import tachiyomi.domain.merge.model.MergeGroup

interface MergeGroupRepository {

    suspend fun getGroup(groupId: Long): MergeGroup?

    suspend fun getGroupId(contentType: ContentType, entryId: Long): Long?

    suspend fun getMembers(contentType: ContentType, groupId: Long): List<Long>

    suspend fun getFavoriteMembers(contentType: ContentType, groupId: Long): List<Long>

    suspend fun createGroup(contentType: ContentType, entryIds: List<Long>): Long?

    suspend fun getAllMemberships(contentType: ContentType): Map<Long, Long>

    fun getAllMembershipsAsFlow(contentType: ContentType): Flow<Map<Long, Long>>

    fun getLibraryMembershipsAsFlow(contentType: ContentType): Flow<Map<Long, Long>>

    fun getOverrideRankingsAsFlow(contentType: ContentType): Flow<Map<Long, List<Long>>>

    suspend fun getRankedMangaMembers(): List<RankedMember<Long>>

    fun getMangaMemberCoversAsFlow(): Flow<List<MemberCover<Long>>>

    suspend fun merge(contentType: ContentType, ids: List<Long>): Long?

    suspend fun materializeGroup(
        contentType: ContentType,
        orderedMemberIds: List<Long>,
        overrideSourceRanking: Boolean,
    ): Long?

    suspend fun removeFromGroup(contentType: ContentType, targetIds: List<Long>): List<Long>

    suspend fun replaceInGroup(
        contentType: ContentType,
        oldId: Long,
        newId: Long,
        onDissolve: suspend (memberIds: List<Long>) -> Unit = {},
    )

    suspend fun dissolve(contentType: ContentType, entryId: Long)

    suspend fun clearAll(contentType: ContentType)

    suspend fun setSourceOrder(contentType: ContentType, groupId: Long, orderedMemberIds: List<Long>)

    suspend fun clearSourceOrder(contentType: ContentType, groupId: Long)
}
