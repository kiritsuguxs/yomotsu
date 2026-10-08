package eu.kanade.tachiyomi.ui.more

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.core.preference.asState
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.more.MoreScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.animedownload.AnimeDownloadManager
import eu.kanade.tachiyomi.ui.animedownload.AnimeDownloadQueueScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.stats.StatsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import androidx.compose.ui.util.fastDistinctBy
import eu.kanade.tachiyomi.data.profile.ProfilePreferences
import eu.kanade.tachiyomi.data.profile.YomotsuLevelManager
import eu.kanade.tachiyomi.data.profile.YomotsuTitle
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class HunterProfileSummary(
    val username: String = "Veterano Yomotsu",
    val level: Int = 1,
    val title: YomotsuTitle = YomotsuLevelManager.ALL_TITLES.first(),
    val avatarUri: String? = null,
    val avatarPreset: String = "preset_yomotsu",
    val avatarType: String = "preset",
    val avatarBorder: String = "border_auto",
    val progress: Float = 0f,
)

data object MoreTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_more_enter)
            return TabOptions(
                index = 4u,
                title = stringResource(MR.strings.label_more),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(SettingsScreen())
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = viewModel<MoreViewModel>()
        val downloadQueueState by viewModel.downloadQueueState.collectAsState()
        val animeDownloadQueueState by viewModel.animeDownloadQueueState.collectAsState()
        val profileSummary by viewModel.profileSummary.collectAsState()

        androidx.compose.runtime.LaunchedEffect(Unit) {
            viewModel.loadProfile()
        }

        MoreScreen(
            profileSummaryProvider = { profileSummary },
            downloadQueueStateProvider = { downloadQueueState },
            animeDownloadQueueStateProvider = { animeDownloadQueueState },
            downloadedOnly = viewModel.downloadedOnly,
            onDownloadedOnlyChange = { viewModel.downloadedOnly = it },
            incognitoMode = viewModel.incognitoMode,
            onIncognitoModeChange = { viewModel.incognitoMode = it },
            onClickDownloadQueue = { navigator.push(DownloadQueueScreen) },
            onClickAnimeDownloadQueue = { navigator.push(AnimeDownloadQueueScreen) },
            onClickCategories = { navigator.push(CategoryScreen()) },
            onClickStats = { navigator.push(eu.kanade.tachiyomi.ui.profile.YomotsuProfileScreen()) },
            onClickDataAndStorage = { navigator.push(SettingsScreen(SettingsScreen.Destination.DataAndStorage)) },
            onClickSettings = { navigator.push(SettingsScreen()) },
            onClickAbout = { navigator.push(SettingsScreen(SettingsScreen.Destination.About)) },
        )
    }
}

class MoreViewModel(
    private val downloadManager: DownloadManager = Injekt.get(),
    private val animeDownloadManager: AnimeDownloadManager = Injekt.get(),
    private val profilePreferences: ProfilePreferences = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    preferences: BasePreferences = Injekt.get(),
) : ViewModel() {

    var downloadedOnly by preferences.downloadedOnly.asState(viewModelScope)
    var incognitoMode by preferences.incognitoMode.asState(viewModelScope)

    private var _downloadQueueState: MutableStateFlow<DownloadQueueState> = MutableStateFlow(DownloadQueueState.Stopped)
    val downloadQueueState: StateFlow<DownloadQueueState> = _downloadQueueState.asStateFlow()

    private var _animeDownloadQueueState: MutableStateFlow<DownloadQueueState> = MutableStateFlow(DownloadQueueState.Stopped)
    val animeDownloadQueueState: StateFlow<DownloadQueueState> = _animeDownloadQueueState.asStateFlow()

    private var _profileSummary: MutableStateFlow<HunterProfileSummary?> = MutableStateFlow(null)
    val profileSummary: StateFlow<HunterProfileSummary?> = _profileSummary.asStateFlow()

    init {
        loadProfile()
        // Anime Handle running/paused status change and queue progress updating
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
        // Handle running/paused status change and queue progress updating
        viewModelScope.launchIO {
            combine(
                downloadManager.isDownloaderRunning,
                downloadManager.queueState,
            ) { isRunning, downloadQueue -> Pair(isRunning, downloadQueue.size) }
                .collectLatest { (isDownloading, downloadQueueSize) ->
                    val pendingDownloadExists = downloadQueueSize != 0
                    _downloadQueueState.value = when {
                        !pendingDownloadExists -> DownloadQueueState.Stopped
                        !isDownloading -> DownloadQueueState.Paused(downloadQueueSize)
                        else -> DownloadQueueState.Downloading(downloadQueueSize)
                    }
                }
        }
    }

    fun loadProfile() {
        viewModelScope.launchIO {
            val libraryManga = getLibraryManga.await()
            val distinctLibraryManga = libraryManga.fastDistinctBy { it.id }

            val animeList = distinctLibraryManga.filter { item ->
                val s = sourceManager.get(item.manga.source)
                s is eu.kanade.tachiyomi.animesource.AnimeSource || s?.javaClass?.name?.contains("anime", ignoreCase = true) == true
            }
            val mangaList = distinctLibraryManga.filter { item ->
                val s = sourceManager.get(item.manga.source)
                !(s is eu.kanade.tachiyomi.animesource.AnimeSource || s?.javaClass?.name?.contains("anime", ignoreCase = true) == true)
            }

            val readChapterCount = mangaList.sumOf { it.readCount }.toInt()
            val downloadCount = downloadManager.getDownloadCount()
            val watchedEpisodesCount = animeList.sumOf { it.readCount }.toInt()
            val animeDownloadCount = animeDownloadManager.getDownloadCount()

            val totalXp = (readChapterCount * YomotsuLevelManager.XP_PER_CHAPTER_READ.toLong()) +
                          (downloadCount * YomotsuLevelManager.XP_PER_CHAPTER_DOWNLOAD.toLong()) +
                          (watchedEpisodesCount * YomotsuLevelManager.XP_PER_EPISODE_WATCHED.toLong()) +
                          (animeDownloadCount * YomotsuLevelManager.XP_PER_EPISODE_DOWNLOAD.toLong())

            val currentLevel = YomotsuLevelManager.calculateLevelFromXp(totalXp)
            val currentLevelXp = YomotsuLevelManager.getXpRequiredForLevel(currentLevel)
            val nextLevelXp = YomotsuLevelManager.getXpRequiredForLevel(currentLevel + 1)
            val progress = if (nextLevelXp > currentLevelXp) {
                (totalXp - currentLevelXp).toFloat() / (nextLevelXp - currentLevelXp).toFloat()
            } else {
                1f
            }

            val unlockedTitles = YomotsuLevelManager.getUnlockedTitles(currentLevel)
            val savedTitleId = profilePreferences.getEquippedTitleId()
            val equippedTitle = unlockedTitles.find { it.name == savedTitleId }
                ?: unlockedTitles.firstOrNull()
                ?: YomotsuLevelManager.ALL_TITLES.first()

            _profileSummary.value = HunterProfileSummary(
                username = profilePreferences.getUsername(),
                level = currentLevel,
                title = equippedTitle,
                avatarUri = profilePreferences.getAvatarUri(),
                avatarPreset = profilePreferences.getAvatarPreset(),
                avatarType = profilePreferences.getAvatarType(),
                avatarBorder = profilePreferences.getAvatarBorder(),
                progress = progress,
            )
        }
    }
}

sealed interface DownloadQueueState {
    data object Stopped : DownloadQueueState
    data class Paused(val pending: Int) : DownloadQueueState
    data class Downloading(val pending: Int) : DownloadQueueState
}
