import re

with open('source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/AnimeSource.kt', 'r') as f:
    content = f.read()

content = content.replace('interface AnimeSource {', '''interface AnimeSource : eu.kanade.tachiyomi.source.Source {
    override val supportsLatest: Boolean get() = false
    override suspend fun getPopularManga(page: Int): eu.kanade.tachiyomi.source.model.MangasPage = throw UnsupportedOperationException()
    override suspend fun getLatestUpdates(page: Int): eu.kanade.tachiyomi.source.model.MangasPage = throw UnsupportedOperationException()
    override suspend fun getSearchManga(page: Int, query: String, filters: eu.kanade.tachiyomi.source.model.FilterList): eu.kanade.tachiyomi.source.model.MangasPage = throw UnsupportedOperationException()
    override suspend fun getMangaUpdate(manga: eu.kanade.tachiyomi.source.model.SManga, chapters: List<eu.kanade.tachiyomi.source.model.SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): eu.kanade.tachiyomi.source.model.SMangaUpdate = throw UnsupportedOperationException()
    override suspend fun getPageList(chapter: eu.kanade.tachiyomi.source.model.SChapter): List<eu.kanade.tachiyomi.source.model.Page> = throw UnsupportedOperationException()
''')

with open('source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/AnimeSource.kt', 'w') as f:
    f.write(content)
