import re

with open('app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaViewModel.kt', 'r') as f:
    content = f.read()

repl = '''    private fun cancelDownload(chapterId: Long) {
        val state = successState ?: return
        if (state.source is eu.kanade.tachiyomi.animesource.AnimeSource) {
            val activeDownload = animeDownloadManager.getQueuedDownloadOrNull(chapterId) ?: return
            animeDownloadManager.cancelQueuedDownloads(listOf(activeDownload))
            updateDownloadState(activeDownload.apply { status = eu.kanade.tachiyomi.data.animedownload.model.AnimeDownload.State.NOT_DOWNLOADED } as eu.kanade.tachiyomi.data.download.model.Download) // Wait! The updateDownloadState takes Download!
        } else {
            val activeDownload = downloadManager.getQueuedDownloadOrNull(chapterId) ?: return
            downloadManager.cancelQueuedDownloads(listOf(activeDownload))
            updateDownloadState(activeDownload.apply { status = eu.kanade.tachiyomi.data.download.model.Download.State.NOT_DOWNLOADED })
        }
    }'''

# Wait! If `updateDownloadState` takes a `Download`, passing an `AnimeDownload` will break!
# Let's check `updateDownloadState`!
