import re

with open('app/src/main/java/eu/kanade/tachiyomi/data/library/LibraryUpdateJob.kt', 'r') as f:
    content = f.read()

content = content.replace('private val downloadManager: DownloadManager = Injekt.get()', '''private val downloadManager: DownloadManager = Injekt.get()
    private val animeDownloadManager: eu.kanade.tachiyomi.data.animedownload.AnimeDownloadManager = Injekt.get()''')

dl_repl = '''    private fun downloadChapters(manga: Manga, chapters: List<Chapter>) {
        // We don't want to start downloading while the library is updating, because websites
        // may don't like it and they could ban the user.
        val source = sourceManager.getOrStub(manga.source)
        if (source is eu.kanade.tachiyomi.animesource.AnimeSource) {
            animeDownloadManager.downloadChapters(manga, chapters, false)
        } else {
            downloadManager.downloadChapters(manga, chapters, false)
        }
    }'''

content = re.sub(r'    private fun downloadChapters\(manga: Manga, chapters: List<Chapter>\) \{.*?    \}', dl_repl, content, flags=re.DOTALL)

with open('app/src/main/java/eu/kanade/tachiyomi/data/library/LibraryUpdateJob.kt', 'w') as f:
    f.write(content)
