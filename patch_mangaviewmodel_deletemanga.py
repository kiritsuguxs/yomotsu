import re

with open('app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace('downloadManager.deleteManga(state.manga, state.source)', '''if (state.source is eu.kanade.tachiyomi.animesource.AnimeSource) {
            animeDownloadManager.deleteManga(state.manga, state.source)
        } else {
            downloadManager.deleteManga(state.manga, state.source)
        }''')

with open('app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaViewModel.kt', 'w') as f:
    f.write(content)
