package eu.kanade.tachiyomi.ui.profile

import android.app.Application
import android.net.Uri
import androidx.compose.ui.util.fastDistinctBy
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.data.animedownload.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.profile.AchievementStats
import eu.kanade.tachiyomi.data.profile.ProfilePreferences
import eu.kanade.tachiyomi.data.profile.YomotsuAchievement
import eu.kanade.tachiyomi.data.profile.YomotsuAchievementManager
import eu.kanade.tachiyomi.data.profile.YomotsuLevelManager
import eu.kanade.tachiyomi.data.profile.YomotsuTitle
import kotlinx.coroutines.flow.update
import mihon.core.viewmodel.StateViewModel
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileOutputStream

sealed interface UserProfileState {
    data object Loading : UserProfileState
    data class Success(
        val username: String,
        val totalXp: Long,
        val totalChaptersRead: Int,
        val totalMangas: Int,
        val totalEpisodesWatched: Int,
        val totalAnimes: Int,
        val totalDownloads: Int,
        val unlockedAchievements: List<YomotsuAchievement>,
        val lockedAchievements: List<YomotsuAchievement>,
        val equippedTitle: YomotsuTitle,
        val unlockedTitles: List<YomotsuTitle>,
        val avatarUri: String?,
        val avatarPreset: String,
        val avatarType: String,
        val bannerUri: String?,
        val bannerPreset: String,
        val bannerType: String,
    ) : UserProfileState
}

class UserProfileViewModel(
    private val downloadManager: DownloadManager = Injekt.get(),
    private val animeDownloadManager: AnimeDownloadManager = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val profilePreferences: ProfilePreferences = Injekt.get(),
    private val context: Application = Injekt.get()
) : StateViewModel<UserProfileState>(UserProfileState.Loading) {

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launchIO {
            val libraryManga = getLibraryManga.await()
            val distinctLibraryManga = libraryManga.fastDistinctBy { it.id }

            val animeList = distinctLibraryManga.filter { item ->
                val s = sourceManager.get(item.manga.source)
                s is eu.kanade.tachiyomi.animesource.AnimeSource || s?.isAnime == true
            }
            val mangaList = distinctLibraryManga.filter { item ->
                val s = sourceManager.get(item.manga.source)
                !(s is eu.kanade.tachiyomi.animesource.AnimeSource || s?.isAnime == true)
            }

            val totalMangas = mangaList.size
            val readChapterCount = mangaList.sumOf { it.readCount }.toInt()
            val downloadCount = downloadManager.getDownloadCount()

            val totalAnimes = animeList.size
            val watchedEpisodesCount = animeList.sumOf { it.readCount }.toInt()
            val animeDownloadCount = animeDownloadManager.getDownloadCount()

            val totalDownloads = downloadCount + animeDownloadCount

            val totalXp = (readChapterCount * YomotsuLevelManager.XP_PER_CHAPTER_READ.toLong()) +
                          (downloadCount * YomotsuLevelManager.XP_PER_CHAPTER_DOWNLOAD.toLong()) +
                          (watchedEpisodesCount * YomotsuLevelManager.XP_PER_EPISODE_WATCHED.toLong()) +
                          (animeDownloadCount * YomotsuLevelManager.XP_PER_EPISODE_DOWNLOAD.toLong())

            val currentLevel = YomotsuLevelManager.calculateLevelFromXp(totalXp)
            val unlockedTitles = YomotsuLevelManager.getUnlockedTitles(currentLevel)

            val savedTitleId = profilePreferences.getEquippedTitleId()
            val equippedTitle = unlockedTitles.find { it.name == savedTitleId }
                ?: unlockedTitles.firstOrNull()
                ?: YomotsuLevelManager.ALL_TITLES.first()

            val stats = AchievementStats(
                chaptersRead = readChapterCount,
                mangasInLibrary = totalMangas,
                downloads = downloadCount,
                episodesWatched = watchedEpisodesCount,
                animesInLibrary = totalAnimes,
                animeDownloads = animeDownloadCount
            )

            val unlocked = YomotsuAchievementManager.ALL_ACHIEVEMENTS.filter {
                it.isUnlocked(stats)
            }
            val locked = YomotsuAchievementManager.ALL_ACHIEVEMENTS.filterNot {
                it.isUnlocked(stats)
            }

            mutableState.update {
                UserProfileState.Success(
                    username = profilePreferences.getUsername(),
                    totalXp = totalXp,
                    totalChaptersRead = readChapterCount,
                    totalMangas = totalMangas,
                    totalEpisodesWatched = watchedEpisodesCount,
                    totalAnimes = totalAnimes,
                    totalDownloads = totalDownloads,
                    unlockedAchievements = unlocked,
                    lockedAchievements = locked,
                    equippedTitle = equippedTitle,
                    unlockedTitles = unlockedTitles,
                    avatarUri = profilePreferences.getAvatarUri(),
                    avatarPreset = profilePreferences.getAvatarPreset(),
                    avatarType = profilePreferences.getAvatarType(),
                    bannerUri = profilePreferences.getBannerUri(),
                    bannerPreset = profilePreferences.getBannerPreset(),
                    bannerType = profilePreferences.getBannerType(),
                )
            }
        }
    }

    fun setEquippedTitle(title: YomotsuTitle) {
        profilePreferences.setEquippedTitleId(title.name)
        loadProfile()
    }

    fun setUsername(name: String) {
        profilePreferences.setUsername(name)
        loadProfile()
    }

    fun setAvatarPreset(presetId: String) {
        profilePreferences.setAvatarPreset(presetId)
        loadProfile()
    }

    fun setBannerPreset(presetId: String) {
        profilePreferences.setBannerPreset(presetId)
        loadProfile()
    }

    fun setAvatarCustom(uriString: String?) {
        viewModelScope.launchIO {
            if (uriString == null) {
                profilePreferences.setAvatarPreset("preset_yomotsu")
                loadProfile()
                return@launchIO
            }
            val oldUri = profilePreferences.getAvatarUri()
            if (oldUri != null && oldUri.contains("profile_images")) {
                try { File(oldUri).delete() } catch (_: Exception) {}
            }
            val localPath = copyUriToLocal(uriString, "avatar_${System.currentTimeMillis()}.jpg")
            if (localPath != null) {
                profilePreferences.setAvatarCustom(localPath)
            }
            loadProfile()
        }
    }

    fun setBannerCustom(uriString: String?) {
        viewModelScope.launchIO {
            if (uriString == null) {
                profilePreferences.setBannerPreset("banner_abyss")
                loadProfile()
                return@launchIO
            }
            val oldUri = profilePreferences.getBannerUri()
            if (oldUri != null && oldUri.contains("profile_images")) {
                try { File(oldUri).delete() } catch (_: Exception) {}
            }
            val localPath = copyUriToLocal(uriString, "banner_${System.currentTimeMillis()}.jpg")
            if (localPath != null) {
                profilePreferences.setBannerCustom(localPath)
            }
            loadProfile()
        }
    }

    // Compatibility aliases
    fun setAvatarUri(uriString: String?) = setAvatarCustom(uriString)
    fun setBannerUri(uriString: String?) = setBannerCustom(uriString)

    private fun copyUriToLocal(uriString: String?, filename: String): String? {
        if (uriString == null) return null
        if (!uriString.startsWith("content://")) return uriString

        return try {
            val uri = Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val profileDir = File(context.filesDir, "profile_images")
            if (!profileDir.exists()) profileDir.mkdirs()
            val destFile = File(profileDir, filename)

            FileOutputStream(destFile).use { output ->
                inputStream.copyTo(output)
            }
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
