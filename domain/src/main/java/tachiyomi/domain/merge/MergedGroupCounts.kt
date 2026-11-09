package tachiyomi.domain.merge

data class MergedGroupCounts(
    val total: Long,
    val read: Long,
    val bookmarked: Long,
) {
    val unread: Long get() = total - read
}
