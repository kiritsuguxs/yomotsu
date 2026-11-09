package tachiyomi.domain.merge

data class DownloadUnitRow(
    val groupId: Long,
    val unit: Int,
    val ownerId: Long,
    val chapterName: String,
    val scanlator: String?,
    val chapterUrl: String,
)

data class ChapterCopyRow(
    val namedId: Long,
    val copy: ChapterUnit,
    val ownerTitle: String,
    val ownerSource: String,
    val chapterName: String,
    val scanlator: String?,
    val chapterUrl: String,
)

fun downloadedUnitsByGroup(
    rowsByGroup: Map<Long, List<DownloadUnitRow>>,
    ownersWithDownloads: Set<Long>,
    isDownloaded: (DownloadUnitRow) -> Boolean,
): Map<Long, Int> {
    if (ownersWithDownloads.isEmpty()) return emptyMap()
    return rowsByGroup.mapValues { (_, rows) ->
        rows.asSequence()
            .filter { it.ownerId in ownersWithDownloads && isDownloaded(it) }
            .mapTo(HashSet()) { it.unit }
            .size
    }
}

suspend fun <T> downloadedUnitsByGroup(
    entries: List<T>,
    membership: Map<Long, Long>,
    idOf: (T) -> Long,
    downloadCountOf: (T) -> Long,
    rowsFor: suspend (owners: Set<Long>) -> Map<Long, List<DownloadUnitRow>>,
    isDownloaded: (owner: T, row: DownloadUnitRow) -> Boolean,
): Map<Long, Int> {
    val owners = entries.filter { downloadCountOf(it) > 0 && idOf(it) in membership }.associateBy(idOf)
    if (owners.isEmpty()) return emptyMap()
    return downloadedUnitsByGroup(rowsFor(owners.keys), owners.keys) { isDownloaded(owners.getValue(it.ownerId), it) }
}
