import re

with open('app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace('private val downloadManager: DownloadManager = Injekt.get(),', '''private val downloadManager: DownloadManager = Injekt.get(),
    private val animeDownloadManager: eu.kanade.tachiyomi.data.animedownload.AnimeDownloadManager = Injekt.get(),''')

content = content.replace('downloadManager.deleteManga(manga, source)', '''if (source is eu.kanade.tachiyomi.animesource.AnimeSource) {
                            animeDownloadManager.deleteManga(manga, source)
                        } else {
                            downloadManager.deleteManga(manga, source)
                        }''')

with open('app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryViewModel.kt', 'w') as f:
    f.write(content)
