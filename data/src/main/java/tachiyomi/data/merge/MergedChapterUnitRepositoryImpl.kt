package tachiyomi.data.merge

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.manga.ChapterAggregation
import tachiyomi.domain.merge.ChapterCopyRow
import tachiyomi.domain.merge.ChapterUnit
import tachiyomi.domain.merge.DownloadUnitRow
import tachiyomi.domain.merge.MergedChapterUnitRepository
import tachiyomi.domain.merge.MergedChapterUnitRepository.GroupStitch
import tachiyomi.domain.merge.MergedGroupCounts
import tachiyomi.domain.merge.model.ContentType

class MergedChapterUnitRepositoryImpl(
    private val database: Database,
) : MergedChapterUnitRepository {

    private val queries = database.merged_chapter_unitQueries

    override suspend fun claimPendingGroups(contentType: ContentType): List<Long> =
        database.transactionWithResult {
            val type = contentType.id
            queries.dropSupersededClaims(type)
            queries.claimPending(type)
            queries.claimedGroups(type).awaitAsList()
        }

    override suspend fun releaseClaim(groupId: Long) {
        queries.deleteClaim(groupId)
    }

    override suspend fun isStale(contentType: ContentType, groupId: Long): Boolean =
        queries.staleMergedGroup(groupId).awaitAsOneOrNull() != null

    override suspend fun getRankings(): Map<Long, String> =
        queries.storedRankings().awaitAsList().associate { it.group_id to it.ranking }

    override suspend fun getStitch(contentType: ContentType, groupId: Long): List<ChapterUnit> =
        queries.stitchOfGroup(groupId) { chapterId, unit, copyOrder ->
            ChapterUnit(chapterId, unit!!.toInt(), copyOrder.toInt())
        }.awaitAsList()

    override suspend fun getGroupCounts(contentType: ContentType): Map<Long, MergedGroupCounts> =
        groupCountsQuery().awaitAsList().toMap()

    override fun getGroupCountsAsFlow(contentType: ContentType): Flow<Map<Long, MergedGroupCounts>> =
        groupCountsQuery().subscribeToList().map { it.toMap() }.distinctUntilChanged()

    private fun groupCountsQuery() =
        queries.countsByGroup { groupId, total, read, bookmarked ->
            groupId to MergedGroupCounts(total, read, bookmarked)
        }

    override suspend fun getDownloadUnits(
        contentType: ContentType,
        ownerIds: Set<Long>,
    ): Map<Long, List<DownloadUnitRow>> {
        if (ownerIds.isEmpty()) return emptyMap()
        val memberCount = queries.mergedLibraryMemberCount().awaitAsOne()
        val readAll = ownerIds.size >= memberCount * READ_ALL_SHARE
        return downloadUnitRows(ownerIds, readAll).groupBy { it.groupId }
    }

    internal suspend fun downloadUnitRows(
        ownerIds: Set<Long>,
        readAll: Boolean,
    ): List<DownloadUnitRow> =
        if (readAll) {
            downloadUnitsQuery(null).awaitAsList().filter { it.ownerId in ownerIds }
        } else {
            ownerIds.chunked(MAX_IDS_PER_QUERY).flatMap { downloadUnitsQuery(it).awaitAsList() }
        }

    private fun downloadUnitsQuery(ownerIds: Collection<Long>?) =
        when (ownerIds) {
            null -> queries.downloadUnitsByGroup(::mangaDownloadUnit)
            else -> queries.downloadUnitsOfOwners(ownerIds, ::mangaDownloadUnit)
        }

    private fun mangaDownloadUnit(
        groupId: Long,
        unit: Long?,
        ownerId: Long,
        name: String,
        scanlator: String?,
        url: String,
    ) =
        DownloadUnitRow(groupId, unit!!.toInt(), ownerId, name, scanlator, url)

    override fun getStitchWritesAsFlow(): Flow<Unit> =
        queries.storedRankings().asFlow().map { }

    override fun getCopiesAsFlow(
        contentType: ContentType,
        chapterIds: Collection<Long>,
    ): Flow<Map<Long, List<ChapterCopyRow>>> =
        queries.copiesOfChapters(chapterIds) {
                namedId,
                copyId,
                unit,
                copyOrder,
                _,
                ownerTitle,
                ownerSource,
                name,
                scanlator,
                url,
            ->
            ChapterCopyRow(
                namedId,
                ChapterUnit(copyId, unit!!.toInt(), copyOrder.toInt()),
                ownerTitle,
                ownerSource.toString(),
                name,
                scanlator,
                url,
            )
        }.subscribeToList().map { rows -> rows.groupBy { it.namedId } }

    override fun getRecognizedChapterCountsAsFlow(): Flow<Map<Long, Long>> =
        combine(
            queries.savedRecognizedCounts().subscribeToList(),
            queries.unsavedRecognizedChapterNumbers().subscribeToList(),
        ) { saved, unsaved ->
            saved.associate { it.mangaId to it.recognizedCount } +
                unsaved.groupBy({ it.mangaId }, { it.chapterNumber })
                    .mapValues { (_, numbers) -> ChapterAggregation.distinctChapterNumberCount(numbers).toLong() }
        }.distinctUntilChanged()

    override suspend fun replaceGroup(
        contentType: ContentType,
        groupId: Long,
        stitch: GroupStitch,
        ranking: String?,
    ) {
        database.transaction {
            queries.deleteGroup(groupId)
            queries.deleteMemberCounts(groupId)
            stitch.recognizedCounts.forEach { (mangaId, count) ->
                queries.insertMemberCount(mangaId, groupId, count)
            }
            stitch.units.forEach {
                val unit = it.unit?.toLong()
                val copyOrder = it.copyOrder.toLong()
                queries.insert(it.chapterId, groupId, unit, copyOrder, it.chapterNumber, it.sourceOrder)
            }
            when {
                ranking == null -> queries.deleteRanking(groupId)
                else -> queries.insertRanking(groupId, ranking)
            }
        }
    }

    private companion object {
        const val READ_ALL_SHARE = 0.3
        const val MAX_IDS_PER_QUERY = 500
    }
}
