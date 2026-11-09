package eu.kanade.domain.merge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.merge.MergeGroupRepository
import tachiyomi.domain.merge.model.ContentType

open class EntryMergeManager(
    private val contentType: ContentType,
    private val repository: MergeGroupRepository,
    private val preferences: LibraryPreferences,
    private val sameTitlePreference: Preference<Boolean>,
    private val onMerged: suspend (memberIds: List<Long>) -> Unit,
    private val onBeforeDissolve: suspend (memberIds: List<Long>) -> Unit,
) : MergeManager {

    suspend fun computeRelatedIds(targetId: Long): LongArray {
        val members = groupLibraryMembers(targetId)
        return if (targetId in members) members.toLongArray() else longArrayOf(targetId)
    }

    suspend fun groupLibraryMembers(targetId: Long): List<Long> {
        if (!preferences.seriesMergingEnabled.get()) return listOf(targetId)
        val groupId = repository.getGroupId(contentType, targetId) ?: return listOf(targetId)
        return repository.getFavoriteMembers(contentType, groupId)
    }

    suspend fun libraryGroups(): List<List<Long>> {
        if (!preferences.seriesMergingEnabled.get()) return emptyList()
        return repository.getAllMemberships(contentType).values.distinct()
            .map { repository.getFavoriteMembers(contentType, it) }
    }

    fun relatedIdsChanges(): Flow<Unit> {
        val libraryMemberships = repository.getLibraryMembershipsAsFlow(contentType)
        return combine(libraryMemberships, preferences.seriesMergingEnabled.changes()) { memberships, enabled ->
            memberships to enabled
        }
            .distinctUntilChanged()
            .map { }
    }

    suspend fun relatedIdsList(targetId: Long): List<Long> = computeRelatedIds(targetId).toList()

    suspend fun groupIdOf(targetId: Long): Long? =
        if (preferences.seriesMergingEnabled.get()) repository.getGroupId(contentType, targetId) else null

    override suspend fun merge(ids: List<Long>) {
        merge(contentType, ids)
    }

    suspend fun merge(targetContentType: ContentType, ids: List<Long>) {
        if (!preferences.seriesMergingEnabled.get()) return
        val groupId = repository.merge(targetContentType, ids) ?: return
        onMerged(repository.getFavoriteMembers(targetContentType, groupId))
    }

    suspend fun unmerge(targetContentType: ContentType = contentType, ids: List<Long>) {
        if (!preferences.seriesMergingEnabled.get()) return
        ids.forEach { repository.dissolve(targetContentType, it) }
    }

    suspend fun replaceInGroup(oldId: Long, newId: Long) {
        repository.replaceInGroup(contentType, oldId, newId, onBeforeDissolve)
    }

    override suspend fun captureGroup(anchorId: Long): GroupSnapshot {
        val groupId = repository.getGroupId(contentType, anchorId) ?: return GroupSnapshot.EMPTY
        return GroupSnapshot(
            orderedMemberIds = repository.getMembers(contentType, groupId),
            overrideSourceRanking = repository.getGroup(groupId)?.overrideSourceRanking == true,
        )
    }

    override suspend fun restoreGroup(snapshot: GroupSnapshot) {
        repository.materializeGroup(contentType, snapshot.orderedMemberIds, snapshot.overrideSourceRanking)
    }

    override fun membershipChanges(): Flow<Map<Long, Long>> =
        repository.getAllMembershipsAsFlow(contentType)

    override suspend fun removeFromGroup(relatedIds: LongArray, targetIds: List<Long>): LongArray {
        if (targetIds.isEmpty()) return relatedIds
        onBeforeDissolve(relatedIds.toList())
        return repository.removeFromGroup(contentType, targetIds).toLongArray()
    }

    val suggestGroupingOnAdd: Boolean
        get() = preferences.seriesMergingEnabled.get() && sameTitlePreference.get()

    suspend fun groupIdsFor(ids: List<Long>): Map<Long, Long> {
        val memberships = memberships()
        return ids.mapNotNull { id -> memberships[id]?.let { id to it } }.toMap()
    }

    suspend fun memberships(): Map<Long, Long> =
        if (preferences.seriesMergingEnabled.get()) repository.getAllMemberships(contentType) else emptyMap()

    suspend fun <T> seriesBuckets(items: List<T>, id: (T) -> Long): List<MergeBucket<T>> {
        val sorted = items.distinctBy(id).sortedBy(id)
        return sorted.bucketByMergeGroup(groupIdsFor(sorted.map(id)), mergingEnabled = true, id)
    }

    suspend fun <T> rankedSeriesBuckets(
        items: List<T>,
        id: (T) -> Long,
        rank: (members: List<T>, memberRanking: List<Long>) -> List<T>,
    ): List<MergeBucket<T>> = seriesBuckets(items, id).map { bucket ->
        if (bucket.members.size == 1) return@map bucket
        MergeBucket(bucket.groupId, rank(bucket.members, overrideRankingMemberIds(id(bucket.members.first()))))
    }

    suspend fun overrideRankingMemberIds(anchorId: Long): List<Long> {
        val groupId = repository.getGroupId(contentType, anchorId) ?: return emptyList()
        if (repository.getGroup(groupId)?.overrideSourceRanking != true) return emptyList()
        return repository.getMembers(contentType, groupId)
    }

    override suspend fun setSourceOrder(orderedMemberIds: List<Long>) {
        val groupId = orderedMemberIds.firstNotNullOfOrNull { repository.getGroupId(contentType, it) } ?: return
        repository.setSourceOrder(contentType, groupId, orderedMemberIds)
    }

    override suspend fun clearSourceOrder(anchorId: Long) {
        val groupId = repository.getGroupId(contentType, anchorId) ?: return
        repository.clearSourceOrder(contentType, groupId)
    }

    suspend fun handOutTrackersBeforeRemoval(entryIds: List<Long>) {
        if (!preferences.seriesMergingEnabled.get()) return
        val handled = HashSet<Long>()
        entryIds.distinct().forEach { id ->
            val groupId = repository.getGroupId(contentType, id) ?: return@forEach
            if (!handled.add(groupId)) return@forEach
            onBeforeDissolve(repository.getFavoriteMembers(contentType, groupId))
        }
    }

    suspend fun unmerge(targetIds: List<Long>) {
        targetIds.distinct().forEach { targetId ->
            onBeforeDissolve(computeRelatedIds(targetId).toList())
            repository.dissolve(contentType, targetId)
        }
    }

    suspend fun clearAllMergesIncludingAuto() {
        repository.getAllMemberships(contentType)
            .entries.groupBy({ it.value }, { it.key })
            .values.forEach { members -> onBeforeDissolve(members) }
        repository.clearAll(contentType)
    }
}
