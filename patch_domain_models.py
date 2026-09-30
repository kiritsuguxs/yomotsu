import re

with open('domain/src/main/java/tachiyomi/domain/chapter/model/Chapter.kt', 'r') as f:
    chapter = f.read()

chapter = chapter.replace('val memo: JsonObject,\n) {', '''val memo: JsonObject,
) {
    val animeId: Long get() = mangaId
    val episodeNumber: Double get() = chapterNumber
    val seen: Boolean get() = read
    val lastSecondSeen: Long get() = lastPageRead
    val totalSeconds: Long get() = 0L // TODO: Store in memo if needed
    val fillermark: Boolean get() = false
    val summary: String? get() = null
    val previewUrl: String? get() = null
''')

with open('domain/src/main/java/tachiyomi/domain/chapter/model/Chapter.kt', 'w') as f:
    f.write(chapter)

with open('domain/src/main/java/tachiyomi/domain/manga/model/Manga.kt', 'r') as f:
    manga = f.read()

manga = manga.replace('val favoriteModifiedAt: Long?,\n    val version: Long,\n) {', '''val favoriteModifiedAt: Long?,
    val version: Long,
) {
    val seasonFlags: Long get() = 0L
    val fetchType: eu.kanade.tachiyomi.animesource.model.FetchType get() = eu.kanade.tachiyomi.animesource.model.FetchType.Episodes
    val viewerFlags: Long get() = 0L
''')

with open('domain/src/main/java/tachiyomi/domain/manga/model/Manga.kt', 'w') as f:
    f.write(manga)

