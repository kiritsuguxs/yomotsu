package tachiyomi.domain.anime.model

import tachiyomi.domain.library.model.LibraryManga

data class SeasonAnime(
    val anime: Anime,
    val totalCount: Long,
    val seenCount: Long,
    val bookmarkCount: Long,
    val latestUpload: Long,
    val fetchedAt: Long,
    val lastSeen: Long,
) {
    val id: Long = anime.id

    val seen
        get() = totalCount == seenCount

    val unseenCount
        get() = totalCount - seenCount

    val hasStarted = seenCount > 0

    val hasBookmarks
        get() = bookmarkCount > 0

    fun toLibraryAnime(): LibraryManga {
        return LibraryManga(
            manga = anime,
            categories = emptyList(),
            totalChapters = totalCount,
            readCount = seenCount,
            bookmarkCount = bookmarkCount,
            latestUpload = latestUpload,
            chapterFetchedAt = fetchedAt,
            lastRead = lastSeen,
        )
    }
}
