import re

with open('app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaViewModel.kt', 'r') as f:
    content = f.read()

# Add animeDownloadManager observers
observe_repl = '''    private fun observeDownloads() {
        viewModelScope.launchIO {
            downloadManager.statusFlow()
                .filter { it.manga.id == successState?.manga?.id }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }

        viewModelScope.launchIO {
            downloadManager.progressFlow()
                .filter { it.manga.id == successState?.manga?.id }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }

        viewModelScope.launchIO {
            animeDownloadManager.statusFlow()
                .filter { it.manga.id == successState?.manga?.id }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .collect {
                    withUIContext {
                        updateAnimeDownloadState(it)
                    }
                }
        }

        viewModelScope.launchIO {
            animeDownloadManager.progressFlow()
                .filter { it.manga.id == successState?.manga?.id }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .collect {
                    withUIContext {
                        updateAnimeDownloadState(it)
                    }
                }
        }'''
content = re.sub(
    r'    private fun observeDownloads\(\) \{.*?viewModelScope\.launchIO \{\s*downloadManager\.progressFlow\(\).*?\}\s*\}',
    observe_repl,
    content,
    flags=re.DOTALL
)

# Add updateAnimeDownloadState
update_state_repl = '''    private fun updateDownloadState(download: Download) {
        updateSuccessState { successState ->
            val modifiedIndex = successState.chapters.indexOfFirst { it.id == download.chapter.id }
            if (modifiedIndex < 0) return@updateSuccessState successState

            val newChapters = successState.chapters.toMutableList().apply {
                val item = removeAt(modifiedIndex)
                    .copy(downloadState = download.status, downloadProgress = download.progress)
                add(modifiedIndex, item)
            }
            successState.copy(chapters = newChapters)
        }
    }

    private fun updateAnimeDownloadState(download: eu.kanade.tachiyomi.data.animedownload.model.AnimeDownload) {
        updateSuccessState { successState ->
            val modifiedIndex = successState.chapters.indexOfFirst { it.id == download.chapter.id }
            if (modifiedIndex < 0) return@updateSuccessState successState

            val newChapters = successState.chapters.toMutableList().apply {
                val item = removeAt(modifiedIndex)
                    // We map AnimeDownload.State to Download.State 
                    .copy(downloadState = eu.kanade.tachiyomi.data.download.model.Download.State.valueOf(download.status.name), downloadProgress = download.progress)
                add(modifiedIndex, item)
            }
            successState.copy(chapters = newChapters)
        }
    }'''
content = re.sub(
    r'    private fun updateDownloadState\(download: Download\) \{.*?    \}',
    update_state_repl,
    content,
    flags=re.DOTALL
)

# Fix cancelDownload
cancel_repl = '''    private fun cancelDownload(chapterId: Long) {
        val state = successState ?: return
        if (state.source is eu.kanade.tachiyomi.animesource.AnimeSource) {
            val activeDownload = animeDownloadManager.getQueuedDownloadOrNull(chapterId) ?: return
            animeDownloadManager.cancelQueuedDownloads(listOf(activeDownload))
            updateAnimeDownloadState(activeDownload.apply { status = eu.kanade.tachiyomi.data.animedownload.model.AnimeDownload.State.NOT_DOWNLOADED })
        } else {
            val activeDownload = downloadManager.getQueuedDownloadOrNull(chapterId) ?: return
            downloadManager.cancelQueuedDownloads(listOf(activeDownload))
            updateDownloadState(activeDownload.apply { status = Download.State.NOT_DOWNLOADED })
        }
    }'''
content = re.sub(
    r'    private fun cancelDownload\(chapterId: Long\) \{.*?    \}',
    cancel_repl,
    content,
    flags=re.DOTALL
)

with open('app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaViewModel.kt', 'w') as f:
    f.write(content)

