package eu.kanade.tachiyomi.data.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class AchievementCategory(val icon: ImageVector) {
    READING(Icons.Outlined.MenuBook),
    COLLECTION(Icons.Outlined.LibraryBooks),
    DOWNLOADS(Icons.Outlined.CloudDownload),
    ANIME(Icons.Outlined.PlayCircleOutline),
    HARDCORE(Icons.Outlined.LocalFireDepartment),
    SPECIAL(Icons.Outlined.EmojiEvents)
}

enum class Tier(val color: Color, val isGradient: Boolean = false, val colorEnd: Color = Color.Unspecified) {
    BRONZE(Color(0xFFCD7F32)),
    PRATA(Color(0xFFC0C0C0)),
    OURO(Color(0xFFFFD700)),
    RUBI(Color(0xFFD50000), true, Color(0xFFFF1744)) // Vermelho Sangue para Fúcsia Neón
}

data class AchievementStats(
    val chaptersRead: Int = 0,
    val mangasInLibrary: Int = 0,
    val downloads: Int = 0,
    val episodesWatched: Int = 0,
    val animesInLibrary: Int = 0,
    val animeDownloads: Int = 0,
)

data class YomotsuAchievement(
    val id: String,
    val name: String,
    val description: String,
    val category: AchievementCategory,
    val tier: Tier,
    val isUnlocked: (stats: AchievementStats) -> Boolean
) {
    constructor(
        id: String,
        name: String,
        description: String,
        category: AchievementCategory,
        tier: Tier,
        legacyCheck: (chaptersRead: Int, mangasInLibrary: Int, downloads: Int) -> Boolean
    ) : this(
        id,
        name,
        description,
        category,
        tier,
        { stats -> legacyCheck(stats.chaptersRead, stats.mangasInLibrary, stats.downloads) }
    )

    fun isUnlocked(chaptersRead: Int, mangasInLibrary: Int, downloads: Int): Boolean {
        return isUnlocked(AchievementStats(chaptersRead = chaptersRead, mangasInLibrary = mangasInLibrary, downloads = downloads))
    }
}

object YomotsuAchievementManager {

    val ALL_ACHIEVEMENTS = listOf(
        YomotsuAchievement("read_1", "Leitor Nível 1", "Leia 1 capítulos.", AchievementCategory.READING, Tier.BRONZE) { r, _, _ -> r >= 1 },
        YomotsuAchievement("read_10", "Leitor Nível 2", "Leia 10 capítulos.", AchievementCategory.READING, Tier.BRONZE) { r, _, _ -> r >= 10 },
        YomotsuAchievement("read_50", "Leitor Nível 3", "Leia 50 capítulos.", AchievementCategory.READING, Tier.BRONZE) { r, _, _ -> r >= 50 },
        YomotsuAchievement("read_100", "Leitor Nível 4", "Leia 100 capítulos.", AchievementCategory.READING, Tier.BRONZE) { r, _, _ -> r >= 100 },
        YomotsuAchievement("read_250", "Leitor Nível 5", "Leia 250 capítulos.", AchievementCategory.READING, Tier.BRONZE) { r, _, _ -> r >= 250 },
        YomotsuAchievement("read_500", "Leitor Nível 6", "Leia 500 capítulos.", AchievementCategory.READING, Tier.BRONZE) { r, _, _ -> r >= 500 },
        YomotsuAchievement("read_1000", "Leitor Nível 7", "Leia 1000 capítulos.", AchievementCategory.READING, Tier.BRONZE) { r, _, _ -> r >= 1000 },
        YomotsuAchievement("read_1500", "Leitor Nível 8", "Leia 1500 capítulos.", AchievementCategory.READING, Tier.BRONZE) { r, _, _ -> r >= 1500 },
        YomotsuAchievement("read_2400", "Leitor Nível 9", "Leia 2400 capítulos.", AchievementCategory.READING, Tier.BRONZE) { r, _, _ -> r >= 2400 },
        YomotsuAchievement("read_3300", "Leitor Nível 10", "Leia 3300 capítulos.", AchievementCategory.READING, Tier.PRATA) { r, _, _ -> r >= 3300 },
        YomotsuAchievement("read_4200", "Leitor Nível 11", "Leia 4200 capítulos.", AchievementCategory.READING, Tier.PRATA) { r, _, _ -> r >= 4200 },
        YomotsuAchievement("read_5100", "Leitor Nível 12", "Leia 5100 capítulos.", AchievementCategory.READING, Tier.PRATA) { r, _, _ -> r >= 5100 },
        YomotsuAchievement("read_6000", "Leitor Nível 13", "Leia 6000 capítulos.", AchievementCategory.READING, Tier.PRATA) { r, _, _ -> r >= 6000 },
        YomotsuAchievement("read_6900", "Leitor Nível 14", "Leia 6900 capítulos.", AchievementCategory.READING, Tier.PRATA) { r, _, _ -> r >= 6900 },
        YomotsuAchievement("read_7800", "Leitor Nível 15", "Leia 7800 capítulos.", AchievementCategory.READING, Tier.PRATA) { r, _, _ -> r >= 7800 },
        YomotsuAchievement("read_8700", "Leitor Nível 16", "Leia 8700 capítulos.", AchievementCategory.READING, Tier.PRATA) { r, _, _ -> r >= 8700 },
        YomotsuAchievement("read_9600", "Leitor Nível 17", "Leia 9600 capítulos.", AchievementCategory.READING, Tier.PRATA) { r, _, _ -> r >= 9600 },
        YomotsuAchievement("read_10500", "Leitor Nível 18", "Leia 10500 capítulos.", AchievementCategory.READING, Tier.PRATA) { r, _, _ -> r >= 10500 },
        YomotsuAchievement("read_11400", "Leitor Nível 19", "Leia 11400 capítulos.", AchievementCategory.READING, Tier.PRATA) { r, _, _ -> r >= 11400 },
        YomotsuAchievement("read_12300", "Leitor Nível 20", "Leia 12300 capítulos.", AchievementCategory.READING, Tier.PRATA) { r, _, _ -> r >= 12300 },
        YomotsuAchievement("read_13200", "Leitor Nível 21", "Leia 13200 capítulos.", AchievementCategory.READING, Tier.PRATA) { r, _, _ -> r >= 13200 },
        YomotsuAchievement("read_14100", "Leitor Nível 22", "Leia 14100 capítulos.", AchievementCategory.READING, Tier.OURO) { r, _, _ -> r >= 14100 },
        YomotsuAchievement("read_15000", "Leitor Nível 23", "Leia 15000 capítulos.", AchievementCategory.READING, Tier.OURO) { r, _, _ -> r >= 15000 },
        YomotsuAchievement("read_15900", "Leitor Nível 24", "Leia 15900 capítulos.", AchievementCategory.READING, Tier.OURO) { r, _, _ -> r >= 15900 },
        YomotsuAchievement("read_16800", "Leitor Nível 25", "Leia 16800 capítulos.", AchievementCategory.READING, Tier.OURO) { r, _, _ -> r >= 16800 },
        YomotsuAchievement("read_17700", "Leitor Nível 26", "Leia 17700 capítulos.", AchievementCategory.READING, Tier.OURO) { r, _, _ -> r >= 17700 },
        YomotsuAchievement("read_18600", "Leitor Nível 27", "Leia 18600 capítulos.", AchievementCategory.READING, Tier.OURO) { r, _, _ -> r >= 18600 },
        YomotsuAchievement("read_19500", "Leitor Nível 28", "Leia 19500 capítulos.", AchievementCategory.READING, Tier.OURO) { r, _, _ -> r >= 19500 },
        YomotsuAchievement("read_20400", "Leitor Nível 29", "Leia 20400 capítulos.", AchievementCategory.READING, Tier.OURO) { r, _, _ -> r >= 20400 },
        YomotsuAchievement("read_21300", "Leitor Nível 30", "Leia 21300 capítulos.", AchievementCategory.READING, Tier.OURO) { r, _, _ -> r >= 21300 },
        YomotsuAchievement("read_22200", "Leitor Nível 31", "Leia 22200 capítulos.", AchievementCategory.READING, Tier.OURO) { r, _, _ -> r >= 22200 },
        YomotsuAchievement("read_23100", "Leitor Nível 32", "Leia 23100 capítulos.", AchievementCategory.READING, Tier.RUBI) { r, _, _ -> r >= 23100 },
        YomotsuAchievement("read_24000", "Leitor Nível 33", "Leia 24000 capítulos.", AchievementCategory.READING, Tier.RUBI) { r, _, _ -> r >= 24000 },
        YomotsuAchievement("read_24900", "Leitor Nível 34", "Leia 24900 capítulos.", AchievementCategory.READING, Tier.RUBI) { r, _, _ -> r >= 24900 },
        YomotsuAchievement("lib_1", "Colecionador Nível 1", "Tenha 1 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.BRONZE) { _, l, _ -> l >= 1 },
        YomotsuAchievement("lib_5", "Colecionador Nível 2", "Tenha 5 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.BRONZE) { _, l, _ -> l >= 5 },
        YomotsuAchievement("lib_10", "Colecionador Nível 3", "Tenha 10 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.BRONZE) { _, l, _ -> l >= 10 },
        YomotsuAchievement("lib_25", "Colecionador Nível 4", "Tenha 25 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.BRONZE) { _, l, _ -> l >= 25 },
        YomotsuAchievement("lib_50", "Colecionador Nível 5", "Tenha 50 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.BRONZE) { _, l, _ -> l >= 50 },
        YomotsuAchievement("lib_100", "Colecionador Nível 6", "Tenha 100 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.BRONZE) { _, l, _ -> l >= 100 },
        YomotsuAchievement("lib_200", "Colecionador Nível 7", "Tenha 200 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.BRONZE) { _, l, _ -> l >= 200 },
        YomotsuAchievement("lib_300", "Colecionador Nível 8", "Tenha 300 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.BRONZE) { _, l, _ -> l >= 300 },
        YomotsuAchievement("lib_400", "Colecionador Nível 9", "Tenha 400 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.BRONZE) { _, l, _ -> l >= 400 },
        YomotsuAchievement("lib_500", "Colecionador Nível 10", "Tenha 500 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.PRATA) { _, l, _ -> l >= 500 },
        YomotsuAchievement("lib_600", "Colecionador Nível 11", "Tenha 600 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.PRATA) { _, l, _ -> l >= 600 },
        YomotsuAchievement("lib_700", "Colecionador Nível 12", "Tenha 700 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.PRATA) { _, l, _ -> l >= 700 },
        YomotsuAchievement("lib_800", "Colecionador Nível 13", "Tenha 800 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.PRATA) { _, l, _ -> l >= 800 },
        YomotsuAchievement("lib_900", "Colecionador Nível 14", "Tenha 900 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.PRATA) { _, l, _ -> l >= 900 },
        YomotsuAchievement("lib_1000", "Colecionador Nível 15", "Tenha 1000 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.PRATA) { _, l, _ -> l >= 1000 },
        YomotsuAchievement("lib_1100", "Colecionador Nível 16", "Tenha 1100 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.PRATA) { _, l, _ -> l >= 1100 },
        YomotsuAchievement("lib_1200", "Colecionador Nível 17", "Tenha 1200 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.PRATA) { _, l, _ -> l >= 1200 },
        YomotsuAchievement("lib_1300", "Colecionador Nível 18", "Tenha 1300 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.PRATA) { _, l, _ -> l >= 1300 },
        YomotsuAchievement("lib_1400", "Colecionador Nível 19", "Tenha 1400 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.PRATA) { _, l, _ -> l >= 1400 },
        YomotsuAchievement("lib_1500", "Colecionador Nível 20", "Tenha 1500 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.PRATA) { _, l, _ -> l >= 1500 },
        YomotsuAchievement("lib_1600", "Colecionador Nível 21", "Tenha 1600 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.OURO) { _, l, _ -> l >= 1600 },
        YomotsuAchievement("lib_1700", "Colecionador Nível 22", "Tenha 1700 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.OURO) { _, l, _ -> l >= 1700 },
        YomotsuAchievement("lib_1800", "Colecionador Nível 23", "Tenha 1800 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.OURO) { _, l, _ -> l >= 1800 },
        YomotsuAchievement("lib_1900", "Colecionador Nível 24", "Tenha 1900 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.OURO) { _, l, _ -> l >= 1900 },
        YomotsuAchievement("lib_2000", "Colecionador Nível 25", "Tenha 2000 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.OURO) { _, l, _ -> l >= 2000 },
        YomotsuAchievement("lib_2100", "Colecionador Nível 26", "Tenha 2100 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.OURO) { _, l, _ -> l >= 2100 },
        YomotsuAchievement("lib_2200", "Colecionador Nível 27", "Tenha 2200 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.OURO) { _, l, _ -> l >= 2200 },
        YomotsuAchievement("lib_2300", "Colecionador Nível 28", "Tenha 2300 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.OURO) { _, l, _ -> l >= 2300 },
        YomotsuAchievement("lib_2400", "Colecionador Nível 29", "Tenha 2400 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.OURO) { _, l, _ -> l >= 2400 },
        YomotsuAchievement("lib_2500", "Colecionador Nível 30", "Tenha 2500 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.OURO) { _, l, _ -> l >= 2500 },
        YomotsuAchievement("lib_2600", "Colecionador Nível 31", "Tenha 2600 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.RUBI) { _, l, _ -> l >= 2600 },
        YomotsuAchievement("lib_2700", "Colecionador Nível 32", "Tenha 2700 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.RUBI) { _, l, _ -> l >= 2700 },
        YomotsuAchievement("lib_2800", "Colecionador Nível 33", "Tenha 2800 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.RUBI) { _, l, _ -> l >= 2800 },
        YomotsuAchievement("dl_1", "Arquivista Nível 1", "Baixe 1 capítulos.", AchievementCategory.DOWNLOADS, Tier.BRONZE) { _, _, d -> d >= 1 },
        YomotsuAchievement("dl_10", "Arquivista Nível 2", "Baixe 10 capítulos.", AchievementCategory.DOWNLOADS, Tier.BRONZE) { _, _, d -> d >= 10 },
        YomotsuAchievement("dl_50", "Arquivista Nível 3", "Baixe 50 capítulos.", AchievementCategory.DOWNLOADS, Tier.BRONZE) { _, _, d -> d >= 50 },
        YomotsuAchievement("dl_100", "Arquivista Nível 4", "Baixe 100 capítulos.", AchievementCategory.DOWNLOADS, Tier.BRONZE) { _, _, d -> d >= 100 },
        YomotsuAchievement("dl_250", "Arquivista Nível 5", "Baixe 250 capítulos.", AchievementCategory.DOWNLOADS, Tier.BRONZE) { _, _, d -> d >= 250 },
        YomotsuAchievement("dl_500", "Arquivista Nível 6", "Baixe 500 capítulos.", AchievementCategory.DOWNLOADS, Tier.BRONZE) { _, _, d -> d >= 500 },
        YomotsuAchievement("dl_1000", "Arquivista Nível 7", "Baixe 1000 capítulos.", AchievementCategory.DOWNLOADS, Tier.BRONZE) { _, _, d -> d >= 1000 },
        YomotsuAchievement("dl_1500", "Arquivista Nível 8", "Baixe 1500 capítulos.", AchievementCategory.DOWNLOADS, Tier.BRONZE) { _, _, d -> d >= 1500 },
        YomotsuAchievement("dl_2400", "Arquivista Nível 9", "Baixe 2400 capítulos.", AchievementCategory.DOWNLOADS, Tier.BRONZE) { _, _, d -> d >= 2400 },
        YomotsuAchievement("dl_3300", "Arquivista Nível 10", "Baixe 3300 capítulos.", AchievementCategory.DOWNLOADS, Tier.PRATA) { _, _, d -> d >= 3300 },
        YomotsuAchievement("dl_4200", "Arquivista Nível 11", "Baixe 4200 capítulos.", AchievementCategory.DOWNLOADS, Tier.PRATA) { _, _, d -> d >= 4200 },
        YomotsuAchievement("dl_5100", "Arquivista Nível 12", "Baixe 5100 capítulos.", AchievementCategory.DOWNLOADS, Tier.PRATA) { _, _, d -> d >= 5100 },
        YomotsuAchievement("dl_6000", "Arquivista Nível 13", "Baixe 6000 capítulos.", AchievementCategory.DOWNLOADS, Tier.PRATA) { _, _, d -> d >= 6000 },
        YomotsuAchievement("dl_6900", "Arquivista Nível 14", "Baixe 6900 capítulos.", AchievementCategory.DOWNLOADS, Tier.PRATA) { _, _, d -> d >= 6900 },
        YomotsuAchievement("dl_7800", "Arquivista Nível 15", "Baixe 7800 capítulos.", AchievementCategory.DOWNLOADS, Tier.PRATA) { _, _, d -> d >= 7800 },
        YomotsuAchievement("dl_8700", "Arquivista Nível 16", "Baixe 8700 capítulos.", AchievementCategory.DOWNLOADS, Tier.PRATA) { _, _, d -> d >= 8700 },
        YomotsuAchievement("dl_9600", "Arquivista Nível 17", "Baixe 9600 capítulos.", AchievementCategory.DOWNLOADS, Tier.PRATA) { _, _, d -> d >= 9600 },
        YomotsuAchievement("dl_10500", "Arquivista Nível 18", "Baixe 10500 capítulos.", AchievementCategory.DOWNLOADS, Tier.PRATA) { _, _, d -> d >= 10500 },
        YomotsuAchievement("dl_11400", "Arquivista Nível 19", "Baixe 11400 capítulos.", AchievementCategory.DOWNLOADS, Tier.PRATA) { _, _, d -> d >= 11400 },
        YomotsuAchievement("dl_12300", "Arquivista Nível 20", "Baixe 12300 capítulos.", AchievementCategory.DOWNLOADS, Tier.PRATA) { _, _, d -> d >= 12300 },
        YomotsuAchievement("dl_13200", "Arquivista Nível 21", "Baixe 13200 capítulos.", AchievementCategory.DOWNLOADS, Tier.OURO) { _, _, d -> d >= 13200 },
        YomotsuAchievement("dl_14100", "Arquivista Nível 22", "Baixe 14100 capítulos.", AchievementCategory.DOWNLOADS, Tier.OURO) { _, _, d -> d >= 14100 },
        YomotsuAchievement("dl_15000", "Arquivista Nível 23", "Baixe 15000 capítulos.", AchievementCategory.DOWNLOADS, Tier.OURO) { _, _, d -> d >= 15000 },
        YomotsuAchievement("dl_15900", "Arquivista Nível 24", "Baixe 15900 capítulos.", AchievementCategory.DOWNLOADS, Tier.OURO) { _, _, d -> d >= 15900 },
        YomotsuAchievement("dl_16800", "Arquivista Nível 25", "Baixe 16800 capítulos.", AchievementCategory.DOWNLOADS, Tier.OURO) { _, _, d -> d >= 16800 },
        YomotsuAchievement("dl_17700", "Arquivista Nível 26", "Baixe 17700 capítulos.", AchievementCategory.DOWNLOADS, Tier.OURO) { _, _, d -> d >= 17700 },
        YomotsuAchievement("dl_18600", "Arquivista Nível 27", "Baixe 18600 capítulos.", AchievementCategory.DOWNLOADS, Tier.OURO) { _, _, d -> d >= 18600 },
        YomotsuAchievement("dl_19500", "Arquivista Nível 28", "Baixe 19500 capítulos.", AchievementCategory.DOWNLOADS, Tier.OURO) { _, _, d -> d >= 19500 },
        YomotsuAchievement("dl_20400", "Arquivista Nível 29", "Baixe 20400 capítulos.", AchievementCategory.DOWNLOADS, Tier.OURO) { _, _, d -> d >= 20400 },
        YomotsuAchievement("dl_21300", "Arquivista Nível 30", "Baixe 21300 capítulos.", AchievementCategory.DOWNLOADS, Tier.OURO) { _, _, d -> d >= 21300 },
        YomotsuAchievement("dl_22200", "Arquivista Nível 31", "Baixe 22200 capítulos.", AchievementCategory.DOWNLOADS, Tier.RUBI) { _, _, d -> d >= 22200 },
        YomotsuAchievement("dl_23100", "Arquivista Nível 32", "Baixe 23100 capítulos.", AchievementCategory.DOWNLOADS, Tier.RUBI) { _, _, d -> d >= 23100 },
        YomotsuAchievement("dl_24000", "Arquivista Nível 33", "Baixe 24000 capítulos.", AchievementCategory.DOWNLOADS, Tier.RUBI) { _, _, d -> d >= 24000 },

        // ANIME - MARATONISTA (EPISÓDIOS ASSISTIDOS)
        YomotsuAchievement("anime_watch_1", "Maratonista Nível 1", "Assista 1 episódios de anime.", AchievementCategory.ANIME, Tier.BRONZE) { it.episodesWatched >= 1 },
        YomotsuAchievement("anime_watch_12", "Maratonista Nível 2", "Assista 12 episódios (1 Temporada).", AchievementCategory.ANIME, Tier.BRONZE) { it.episodesWatched >= 12 },
        YomotsuAchievement("anime_watch_24", "Maratonista Nível 3", "Assista 24 episódios.", AchievementCategory.ANIME, Tier.BRONZE) { it.episodesWatched >= 24 },
        YomotsuAchievement("anime_watch_50", "Maratonista Nível 4", "Assista 50 episódios.", AchievementCategory.ANIME, Tier.BRONZE) { it.episodesWatched >= 50 },
        YomotsuAchievement("anime_watch_100", "Maratonista Nível 5", "Assista 100 episódios.", AchievementCategory.ANIME, Tier.BRONZE) { it.episodesWatched >= 100 },
        YomotsuAchievement("anime_watch_250", "Maratonista Nível 6", "Assista 250 episódios.", AchievementCategory.ANIME, Tier.BRONZE) { it.episodesWatched >= 250 },
        YomotsuAchievement("anime_watch_500", "Maratonista Nível 7", "Assista 500 episódios.", AchievementCategory.ANIME, Tier.BRONZE) { it.episodesWatched >= 500 },
        YomotsuAchievement("anime_watch_1000", "Maratonista Nível 8", "Assista 1000 episódios.", AchievementCategory.ANIME, Tier.BRONZE) { it.episodesWatched >= 1000 },
        YomotsuAchievement("anime_watch_1500", "Maratonista Nível 9", "Assista 1500 episódios.", AchievementCategory.ANIME, Tier.BRONZE) { it.episodesWatched >= 1500 },
        YomotsuAchievement("anime_watch_2400", "Maratonista Nível 10", "Assista 2400 episódios.", AchievementCategory.ANIME, Tier.PRATA) { it.episodesWatched >= 2400 },
        YomotsuAchievement("anime_watch_3500", "Maratonista Nível 11", "Assista 3500 episódios.", AchievementCategory.ANIME, Tier.PRATA) { it.episodesWatched >= 3500 },
        YomotsuAchievement("anime_watch_5000", "Maratonista Nível 12", "Assista 5000 episódios.", AchievementCategory.ANIME, Tier.PRATA) { it.episodesWatched >= 5000 },
        YomotsuAchievement("anime_watch_6500", "Maratonista Nível 13", "Assista 6500 episódios.", AchievementCategory.ANIME, Tier.PRATA) { it.episodesWatched >= 6500 },
        YomotsuAchievement("anime_watch_8000", "Maratonista Nível 14", "Assista 8000 episódios.", AchievementCategory.ANIME, Tier.PRATA) { it.episodesWatched >= 8000 },
        YomotsuAchievement("anime_watch_10000", "Maratonista Nível 15", "Assista 10000 episódios.", AchievementCategory.ANIME, Tier.OURO) { it.episodesWatched >= 10000 },
        YomotsuAchievement("anime_watch_12500", "Maratonista Nível 16", "Assista 12500 episódios.", AchievementCategory.ANIME, Tier.OURO) { it.episodesWatched >= 12500 },
        YomotsuAchievement("anime_watch_15000", "Maratonista Nível 17", "Assista 15000 episódios.", AchievementCategory.ANIME, Tier.OURO) { it.episodesWatched >= 15000 },
        YomotsuAchievement("anime_watch_18000", "Maratonista Nível 18", "Assista 18000 episódios.", AchievementCategory.ANIME, Tier.OURO) { it.episodesWatched >= 18000 },
        YomotsuAchievement("anime_watch_21000", "Maratonista Nível 19", "Assista 21000 episódios.", AchievementCategory.ANIME, Tier.RUBI) { it.episodesWatched >= 21000 },
        YomotsuAchievement("anime_watch_25000", "Maratonista Nível 20", "Assista 25000 episódios.", AchievementCategory.ANIME, Tier.RUBI) { it.episodesWatched >= 25000 },

        // ANIME - OTAKU (ANIMES NA BIBLIOTECA)
        YomotsuAchievement("anime_lib_1", "Otaku Nível 1", "Tenha 1 animes na biblioteca.", AchievementCategory.ANIME, Tier.BRONZE) { it.animesInLibrary >= 1 },
        YomotsuAchievement("anime_lib_5", "Otaku Nível 2", "Tenha 5 animes na biblioteca.", AchievementCategory.ANIME, Tier.BRONZE) { it.animesInLibrary >= 5 },
        YomotsuAchievement("anime_lib_10", "Otaku Nível 3", "Tenha 10 animes na biblioteca.", AchievementCategory.ANIME, Tier.BRONZE) { it.animesInLibrary >= 10 },
        YomotsuAchievement("anime_lib_25", "Otaku Nível 4", "Tenha 25 animes na biblioteca.", AchievementCategory.ANIME, Tier.BRONZE) { it.animesInLibrary >= 25 },
        YomotsuAchievement("anime_lib_50", "Otaku Nível 5", "Tenha 50 animes na biblioteca.", AchievementCategory.ANIME, Tier.BRONZE) { it.animesInLibrary >= 50 },
        YomotsuAchievement("anime_lib_100", "Otaku Nível 6", "Tenha 100 animes na biblioteca.", AchievementCategory.ANIME, Tier.BRONZE) { it.animesInLibrary >= 100 },
        YomotsuAchievement("anime_lib_200", "Otaku Nível 7", "Tenha 200 animes na biblioteca.", AchievementCategory.ANIME, Tier.PRATA) { it.animesInLibrary >= 200 },
        YomotsuAchievement("anime_lib_350", "Otaku Nível 8", "Tenha 350 animes na biblioteca.", AchievementCategory.ANIME, Tier.PRATA) { it.animesInLibrary >= 350 },
        YomotsuAchievement("anime_lib_500", "Otaku Nível 9", "Tenha 500 animes na biblioteca.", AchievementCategory.ANIME, Tier.PRATA) { it.animesInLibrary >= 500 },
        YomotsuAchievement("anime_lib_750", "Otaku Nível 10", "Tenha 750 animes na biblioteca.", AchievementCategory.ANIME, Tier.OURO) { it.animesInLibrary >= 750 },
        YomotsuAchievement("anime_lib_1000", "Otaku Nível 11", "Tenha 1000 animes na biblioteca.", AchievementCategory.ANIME, Tier.OURO) { it.animesInLibrary >= 1000 },
        YomotsuAchievement("anime_lib_1500", "Otaku Nível 12", "Tenha 1500 animes na biblioteca.", AchievementCategory.ANIME, Tier.RUBI) { it.animesInLibrary >= 1500 },

        // ANIME - FANSUBBER (DOWNLOADS DE ANIME)
        YomotsuAchievement("anime_dl_1", "Fansubber Nível 1", "Baixe 1 episódios de anime.", AchievementCategory.ANIME, Tier.BRONZE) { it.animeDownloads >= 1 },
        YomotsuAchievement("anime_dl_10", "Fansubber Nível 2", "Baixe 10 episódios de anime.", AchievementCategory.ANIME, Tier.BRONZE) { it.animeDownloads >= 10 },
        YomotsuAchievement("anime_dl_25", "Fansubber Nível 3", "Baixe 25 episódios de anime.", AchievementCategory.ANIME, Tier.BRONZE) { it.animeDownloads >= 25 },
        YomotsuAchievement("anime_dl_50", "Fansubber Nível 4", "Baixe 50 episódios de anime.", AchievementCategory.ANIME, Tier.BRONZE) { it.animeDownloads >= 50 },
        YomotsuAchievement("anime_dl_100", "Fansubber Nível 5", "Baixe 100 episódios de anime.", AchievementCategory.ANIME, Tier.BRONZE) { it.animeDownloads >= 100 },
        YomotsuAchievement("anime_dl_250", "Fansubber Nível 6", "Baixe 250 episódios de anime.", AchievementCategory.ANIME, Tier.PRATA) { it.animeDownloads >= 250 },
        YomotsuAchievement("anime_dl_500", "Fansubber Nível 7", "Baixe 500 episódios de anime.", AchievementCategory.ANIME, Tier.PRATA) { it.animeDownloads >= 500 },
        YomotsuAchievement("anime_dl_1000", "Fansubber Nível 8", "Baixe 1000 episódios de anime.", AchievementCategory.ANIME, Tier.OURO) { it.animeDownloads >= 1000 },
        YomotsuAchievement("anime_dl_2000", "Fansubber Nível 9", "Baixe 2000 episódios de anime.", AchievementCategory.ANIME, Tier.OURO) { it.animeDownloads >= 2000 },
        YomotsuAchievement("anime_dl_5000", "Fansubber Nível 10", "Baixe 5000 episódios de anime.", AchievementCategory.ANIME, Tier.RUBI) { it.animeDownloads >= 5000 },
    )
}
