package eu.kanade.domain.merge

import tachiyomi.domain.merge.ChapterUnit

class GroupChapterFlags<T>(
    scope: MergeScope,
    pooled: List<T>,
    private val shown: List<T>,
    stitch: List<ChapterUnit>,
    private val id: (T) -> Long,
    private val read: (T) -> Boolean,
    private val bookmark: (T) -> Boolean,
    onDisk: () -> Set<Long>,
) {
    val marks = GroupMarks.of(pooled, shown, stitch, id, read, bookmark)
    private val copies by lazy { CopyToOpen(scope, pooled, stitch, id, onDisk()) }

    val downloadedIds: Set<Long> by lazy { shown.asSequence().filter(::isDownloaded).mapTo(HashSet(), id) }

    fun isRead(chapter: T): Boolean = marks.isRead(id(chapter), read(chapter))

    fun isBookmarked(chapter: T): Boolean = marks.isBookmarked(id(chapter), bookmark(chapter))

    fun isDownloaded(chapter: T): Boolean = copies.isOnDisk(id(chapter))
}

data class GroupMarks(
    private val readElsewhere: Set<Long> = emptySet(),
    private val bookmarkedElsewhere: Set<Long> = emptySet(),
) {
    fun isRead(id: Long, ownRead: Boolean): Boolean = ownRead || id in readElsewhere

    fun isBookmarked(id: Long, ownBookmark: Boolean): Boolean = ownBookmark || id in bookmarkedElsewhere

    companion object {
        val NONE = GroupMarks()

        fun <T> of(
            pooled: List<T>,
            shown: List<T>,
            stitch: List<ChapterUnit>,
            id: (T) -> Long,
            read: (T) -> Boolean,
            bookmark: (T) -> Boolean,
        ) = GroupMarks(
            flaggedOnAnotherSource(pooled, shown, stitch, id, read),
            flaggedOnAnotherSource(pooled, shown, stitch, id, bookmark),
        )
    }
}
