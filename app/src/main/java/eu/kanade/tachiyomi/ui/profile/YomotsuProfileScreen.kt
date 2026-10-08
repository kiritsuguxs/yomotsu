package eu.kanade.tachiyomi.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.profile.UserProfileScreen
import eu.kanade.presentation.util.Screen
import tachiyomi.presentation.core.screens.LoadingScreen

class YomotsuProfileScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = viewModel<UserProfileViewModel>()
        val state by viewModel.state.collectAsState()

        when (val currentState = state) {
            is UserProfileState.Loading -> {
                LoadingScreen()
            }
            is UserProfileState.Success -> {
                UserProfileScreen(
                    navigateUp = { navigator.pop() },
                    username = currentState.username,
                    totalXp = currentState.totalXp,
                    totalChaptersRead = currentState.totalChaptersRead,
                    totalChapters = currentState.totalChapters,
                    totalMangas = currentState.totalMangas,
                    totalEpisodesWatched = currentState.totalEpisodesWatched,
                    totalEpisodes = currentState.totalEpisodes,
                    totalAnimes = currentState.totalAnimes,
                    totalDownloads = currentState.totalDownloads,
                    mangaDownloads = currentState.mangaDownloads,
                    animeDownloads = currentState.animeDownloads,
                    completedMangas = currentState.completedMangas,
                    completedAnimes = currentState.completedAnimes,
                    totalReadDurationMs = currentState.totalReadDurationMs,
                    totalWatchDurationMs = currentState.totalWatchDurationMs,
                    unlockedAchievements = currentState.unlockedAchievements,
                    lockedAchievements = currentState.lockedAchievements,
                    equippedTitle = currentState.equippedTitle,
                    unlockedTitles = currentState.unlockedTitles,
                    avatarUri = currentState.avatarUri,
                    avatarPreset = currentState.avatarPreset,
                    avatarType = currentState.avatarType,
                    avatarBorder = currentState.avatarBorder,
                    bannerUri = currentState.bannerUri,
                    bannerPreset = currentState.bannerPreset,
                    bannerType = currentState.bannerType,
                    onUsernameChanged = { viewModel.setUsername(it) },
                    onTitleSelected = { viewModel.setEquippedTitle(it) },
                    onAvatarSelected = { viewModel.setAvatarCustom(it) },
                    onAvatarPresetSelected = { viewModel.setAvatarPreset(it) },
                    onAvatarBorderSelected = { viewModel.setAvatarBorder(it) },
                    onBannerSelected = { viewModel.setBannerCustom(it) },
                    onBannerPresetSelected = { viewModel.setBannerPreset(it) }
                )
            }
        }
    }
}
