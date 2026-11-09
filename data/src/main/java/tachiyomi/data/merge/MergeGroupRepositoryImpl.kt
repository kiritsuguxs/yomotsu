package tachiyomi.data.merge

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.merge.MergeGroupRepository
import tachiyomi.domain.merge.RankedMember
import tachiyomi.domain.merge.model.ContentType
import tachiyomi.domain.merge.model.MemberCover
import tachiyomi.domain.merge.model.MergeGroup

class MergeGroupRepositoryImpl(
    private val database: Database,
) : MergeGroupRepository {

    private val queries = database.merge_groupQueries

    override suspend fun getGroup(groupId: Long): MergeGroup? =
        queries.getGroup(groupId, ::mapGroup).awaitAsOneOrNull()

    override suspend fun getGroupId(contentType: ContentType, entryId: Long): Long? =
        queries.mangaGroupId(entryId).awaitAsOneOrNull()

    override suspend fun getMembers(contentType: ContentType, groupId: Long): List<Long> =
        queries.mangaMembers(groupId).awaitAsList()

    override suspend fun getFavoriteMembers(contentType: ContentType, groupId: Long): List<Long> =
        queries.mangaFavoriteMembers(groupId).awaitAsList()

    override suspend fun createGroup(contentType: ContentType, entryIds: List<Long>): Long? {
        val distinct = entryIds.distinct()
        if (distinct.size < 2) return null
        return database.transactionWithResult {
            queries.insertGroup(contentType.id)
            val groupId = queries.selectLastInsertedRowId().awaitAsOne()
            distinct.forEach { insertMember(contentType, groupId, it) }
            groupId
        }
    }

    override suspend fun getAllMemberships(contentType: ContentType): Map<Long, Long> =
        queries.allMangaMemberships { id, groupId -> id to groupId }.awaitAsList().toMap()

    override fun getAllMembershipsAsFlow(contentType: ContentType): Flow<Map<Long, Long>> =
        queries.allMangaMemberships { id, groupId -> id to groupId }
            .subscribeToList().map { it.toMap() }

    override fun getLibraryMembershipsAsFlow(contentType: ContentType): Flow<Map<Long, Long>> =
        queries.libraryMangaMemberships { id, groupId -> id to groupId }
            .subscribeToList().map { it.toMap() }

    override fun getOverrideRankingsAsFlow(contentType: ContentType): Flow<Map<Long, List<Long>>> =
        queries.mangaOverrideRankings { groupId, mangaId -> groupId to mangaId }
            .subscribeToList().map { rows -> rows.groupBy({ it.first }, { it.second }) }

    override suspend fun getRankedMangaMembers(): List<RankedMember<Long>> =
        queries.rankedMangaMembers { groupId, mangaId, source, override ->
            RankedMember(groupId, mangaId, source, override == 1L)
        }.awaitAsList()

    override fun getMangaMemberCoversAsFlow(): Flow<List<MemberCover<Long>>> =
        queries.libraryMangaMemberCovers { id, groupId, source, url, lastModified ->
            MemberCover(id, groupId, source, url, lastModified)
        }.subscribeToList()

    override suspend fun merge(contentType: ContentType, ids: List<Long>): Long? {
        val distinct = ids.distinct()
        if (distinct.size < 2) return null
        return database.transactionWithResult { absorb(contentType, distinct) }
    }

    override suspend fun materializeGroup(
        contentType: ContentType,
        orderedMemberIds: List<Long>,
        overrideSourceRanking: Boolean,
    ): Long? {
        val distinct = orderedMemberIds.distinct()
        if (distinct.size < 2) return null
        return database.transactionWithResult {
            groupIdsForMembers(contentType, distinct).forEach { queries.deleteGroup(it) }
            queries.insertGroup(contentType.id)
            val groupId = queries.selectLastInsertedRowId().awaitAsOne()
            distinct.forEachIndexed { index, id -> insertMember(contentType, groupId, id, index.toLong()) }
            if (overrideSourceRanking) queries.setOverrideSourceRanking(override = 1L, groupId = groupId)
            groupId
        }
    }

    private suspend fun absorb(contentType: ContentType, ids: List<Long>): Long {
        val survivorId = ids.firstNotNullOfOrNull { getGroupId(contentType, it) }
        val kept = survivorId?.let { getMembers(contentType, it) }.orEmpty()
        val keptSet = kept.toHashSet()
        val order = kept + arrivalOrder(contentType, ids).filterNot { it in keptSet }

        val groupId = survivorId ?: run {
            queries.insertGroup(contentType.id)
            queries.selectLastInsertedRowId().awaitAsOne()
        }
        absorbedGroups(contentType, ids, into = groupId).forEach { queries.deleteGroup(it) }
        order.forEachIndexed { index, id ->
            if (id in keptSet) {
                setMemberPriority(contentType, id, index.toLong())
            } else {
                insertMember(contentType, groupId, id, index.toLong())
            }
        }
        return groupId
    }

    private suspend fun arrivalOrder(contentType: ContentType, ids: List<Long>): List<Long> {
        val named = ids.toHashSet()
        val ordered = LinkedHashSet<Long>()
        ids.forEach { id ->
            if (!ordered.add(id)) return@forEach
            val groupId = getGroupId(contentType, id) ?: return@forEach
            getMembers(contentType, groupId).forEach { member ->
                if (member !in named) ordered.add(member)
            }
        }
        return ordered.toList()
    }

    private suspend fun absorbedGroups(contentType: ContentType, ids: List<Long>, into: Long): List<Long> =
        groupIdsForMembers(contentType, ids).filterNot { it == into }

    override suspend fun removeFromGroup(contentType: ContentType, targetIds: List<Long>): List<Long> {
        if (targetIds.isEmpty()) return emptyList()
        return database.transactionWithResult {
            val groupId = targetIds.firstNotNullOfOrNull { getGroupId(contentType, it) }
                ?: return@transactionWithResult emptyList()
            val targetSet = targetIds.toHashSet()
            val survivors = getMembers(contentType, groupId).filter { it !in targetSet }
            targetIds.forEach { deleteMember(contentType, it) }
            if (survivors.size < 2) queries.deleteGroup(groupId)
            survivors
        }
    }

    override suspend fun replaceInGroup(
        contentType: ContentType,
        oldId: Long,
        newId: Long,
        onDissolve: suspend (memberIds: List<Long>) -> Unit,
    ) {
        database.transaction {
            val groupId = getGroupId(contentType, oldId) ?: return@transaction
            val before = getMembers(contentType, groupId)
            deleteMember(contentType, oldId)
            when (val newGroupId = getGroupId(contentType, newId)) {
                groupId -> {}
                null -> insertMember(contentType, groupId, newId)
                else -> {
                    getMembers(contentType, newGroupId).forEach { member ->
                        deleteMember(contentType, member)
                        insertMember(contentType, groupId, member)
                    }
                    queries.deleteGroup(newGroupId)
                }
            }
            val remaining = getMembers(contentType, groupId)
            if (remaining.size < 2) {
                onDissolve(before)
                queries.deleteGroup(groupId)
                return@transaction
            }
            val beforeSet = before.toHashSet()
            val arrived = remaining.filterNot { it in beforeSet }
            val remainingSet = remaining.toHashSet()
            writeOrder(
                contentType,
                before.flatMap { member ->
                    when {
                        member == oldId -> arrived
                        member in remainingSet -> listOf(member)
                        else -> emptyList()
                    }
                },
            )
        }
    }

    override suspend fun dissolve(contentType: ContentType, entryId: Long) {
        database.transaction {
            val groupId = getGroupId(contentType, entryId) ?: return@transaction
            queries.deleteGroup(groupId)
        }
    }

    override suspend fun clearAll(contentType: ContentType) {
        queries.deleteGroupsByContentType(contentType.id)
    }

    override suspend fun setSourceOrder(contentType: ContentType, groupId: Long, orderedMemberIds: List<Long>) {
        database.transaction {
            val members = getMembers(contentType, groupId).toHashSet()
            writeOrder(contentType, orderedMemberIds.filter { it in members })
            queries.setOverrideSourceRanking(override = 1L, groupId = groupId)
        }
    }

    private suspend fun writeOrder(contentType: ContentType, orderedMemberIds: List<Long>) {
        orderedMemberIds.forEachIndexed { index, id -> setMemberPriority(contentType, id, index.toLong()) }
    }

    override suspend fun clearSourceOrder(contentType: ContentType, groupId: Long) {
        database.transaction {
            getMembers(contentType, groupId).forEach { setMemberPriority(contentType, it, DEFAULT_SOURCE_PRIORITY) }
            queries.setOverrideSourceRanking(override = 0L, groupId = groupId)
        }
    }

    private suspend fun setMemberPriority(contentType: ContentType, entryId: Long, priority: Long) {
        queries.setMangaMemberPriority(priority, entryId)
    }

    private suspend fun groupIdsForMembers(contentType: ContentType, ids: List<Long>): List<Long> =
        queries.mangaGroupIdsForMembers(ids).awaitAsList()

    private suspend fun deleteMember(contentType: ContentType, entryId: Long) {
        queries.deleteMangaMember(entryId)
    }

    private suspend fun insertMember(
        contentType: ContentType,
        groupId: Long,
        entryId: Long,
        priority: Long = DEFAULT_SOURCE_PRIORITY,
    ) {
        queries.insertMangaMember(groupId, entryId, priority)
    }

    private fun mapGroup(
        id: Long,
        contentType: Long,
        overrideSourceRanking: Long,
    ) = MergeGroup(
        id = id,
        contentType = ContentType.fromId(contentType),
        overrideSourceRanking = overrideSourceRanking != 0L,
    )

    companion object {
        private const val DEFAULT_SOURCE_PRIORITY = 0L
    }
}
