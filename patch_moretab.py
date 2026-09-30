import re

with open('app/src/main/java/eu/kanade/tachiyomi/ui/more/MoreTab.kt', 'r') as f:
    content = f.read()

content = content.replace('import eu.kanade.tachiyomi.data.download.DownloadManager', '''import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.animedownload.AnimeDownloadManager
import eu.kanade.tachiyomi.ui.animedownload.AnimeDownloadQueueScreen''')

content = content.replace('val downloadQueueState by viewModel.downloadQueueState.collectAsState()', '''val downloadQueueState by viewModel.downloadQueueState.collectAsState()
        val animeDownloadQueueState by viewModel.animeDownloadQueueState.collectAsState()''')

content = content.replace('downloadQueueStateProvider = { downloadQueueState },', '''downloadQueueStateProvider = { downloadQueueState },
            animeDownloadQueueStateProvider = { animeDownloadQueueState },''')

content = content.replace('onClickDownloadQueue = { navigator.push(DownloadQueueScreen) },', '''onClickDownloadQueue = { navigator.push(DownloadQueueScreen) },
            onClickAnimeDownloadQueue = { navigator.push(AnimeDownloadQueueScreen) },''')

content = content.replace('private val downloadManager: DownloadManager = Injekt.get(),', '''private val downloadManager: DownloadManager = Injekt.get(),
    private val animeDownloadManager: AnimeDownloadManager = Injekt.get(),''')

content = content.replace('val downloadQueueState: StateFlow<DownloadQueueState> = _downloadQueueState.asStateFlow()', '''val downloadQueueState: StateFlow<DownloadQueueState> = _downloadQueueState.asStateFlow()

    private var _animeDownloadQueueState: MutableStateFlow<DownloadQueueState> = MutableStateFlow(DownloadQueueState.Stopped)
    val animeDownloadQueueState: StateFlow<DownloadQueueState> = _animeDownloadQueueState.asStateFlow()''')


init_logic = '''        // Anime Handle running/paused status change and queue progress updating
        viewModelScope.launchIO {
            combine(
                animeDownloadManager.isDownloaderRunning,
                animeDownloadManager.queueState,
            ) { isRunning, downloadQueue -> Pair(isRunning, downloadQueue.size) }
                .collectLatest { (isDownloading, downloadQueueSize) ->
                    val pendingDownloadExists = downloadQueueSize != 0
                    _animeDownloadQueueState.value = when {
                        !pendingDownloadExists -> DownloadQueueState.Stopped
                        !isDownloading -> DownloadQueueState.Paused(downloadQueueSize)
                        else -> DownloadQueueState.Downloading(downloadQueueSize)
                    }
                }
        }
'''
content = content.replace('init {\n        // Handle running/paused', 'init {\n' + init_logic + '        // Handle running/paused')

with open('app/src/main/java/eu/kanade/tachiyomi/ui/more/MoreTab.kt', 'w') as f:
    f.write(content)
