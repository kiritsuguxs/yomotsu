package eu.kanade.domain.merge

import kotlinx.coroutines.flow.Flow

interface MergeManager {

    suspend fun setSourceOrder(orderedMemberIds: List<Long>)

    suspend fun clearSourceOrder(anchorId: Long)

    suspend fun removeFromGroup(relatedIds: LongArray, targetIds: List<Long>): LongArray

    suspend fun merge(ids: List<Long>)

    suspend fun captureGroup(anchorId: Long): GroupSnapshot

    suspend fun restoreGroup(snapshot: GroupSnapshot)

    fun membershipChanges(): Flow<Map<Long, Long>>
}

data class GroupSnapshot(
    val orderedMemberIds: List<Long>,
    val overrideSourceRanking: Boolean,
) {
    companion object {
        val EMPTY = GroupSnapshot(emptyList(), overrideSourceRanking = false)
    }
}
