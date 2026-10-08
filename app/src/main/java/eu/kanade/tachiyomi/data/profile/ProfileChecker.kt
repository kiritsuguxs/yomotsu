package eu.kanade.tachiyomi.data.profile

import android.content.Context
import androidx.compose.ui.util.fastDistinctBy
import eu.kanade.tachiyomi.data.animedownload.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.DownloadManager
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.Injekt

object ProfileChecker {

    suspend fun checkAchievements(
        context: Context,
        getLibraryManga: GetLibraryManga = Injekt.get(),
        downloadManager: DownloadManager = Injekt.get(),
        animeDownloadManager: AnimeDownloadManager = Injekt.get(),
        sourceManager: SourceManager = Injekt.get(),
        profilePreferences: ProfilePreferences = Injekt.get()
    ) {
        val notifiedSet = profilePreferences.getNotifiedAchievements()

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

        val stats = AchievementStats(
            chaptersRead = mangaList.sumOf { it.readCount }.toInt(),
            mangasInLibrary = mangaList.size,
            downloads = downloadManager.getDownloadCount(),
            episodesWatched = animeList.sumOf { it.readCount }.toInt(),
            animesInLibrary = animeList.size,
            animeDownloads = animeDownloadManager.getDownloadCount(),
        )

        val unlockedNow = YomotsuAchievementManager.ALL_ACHIEVEMENTS.filter {
            it.isUnlocked(stats)
        }

        val newlyUnlocked = unlockedNow.filter { it.id !in notifiedSet }

        if (newlyUnlocked.isNotEmpty()) {
            val notifier = ProfileNotifier(context)
            val updatedSet = notifiedSet.toMutableSet()

            for (achievement in newlyUnlocked) {
                notifier.showAchievementUnlocked(achievement)
                updatedSet.add(achievement.id)
            }

            profilePreferences.setNotifiedAchievements(updatedSet)
        }
    }
}
