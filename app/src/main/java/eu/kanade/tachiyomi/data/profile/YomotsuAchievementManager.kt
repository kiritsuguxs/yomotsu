package eu.kanade.tachiyomi.data.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.AutoStories
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
    NOVEL(Icons.Outlined.AutoStories),
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
    val novelsInLibrary: Int = 0,
    val novelChaptersRead: Int = 0,
    val novelDownloads: Int = 0,
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
        // === MANGÁ - LEITURA (10 Níveis Equilibrados) ===
        YomotsuAchievement("read_1", "Leitor Nível 1", "Leia 1 capítulos.", AchievementCategory.READING, Tier.BRONZE) { r, _, _ -> r >= 1 },
        YomotsuAchievement("read_25", "Leitor Nível 2", "Leia 25 capítulos.", AchievementCategory.READING, Tier.BRONZE) { r, _, _ -> r >= 25 },
        YomotsuAchievement("read_50", "Leitor Nível 3", "Leia 50 capítulos.", AchievementCategory.READING, Tier.BRONZE) { r, _, _ -> r >= 50 },
        YomotsuAchievement("read_100", "Leitor Nível 4", "Leia 100 capítulos.", AchievementCategory.READING, Tier.BRONZE) { r, _, _ -> r >= 100 },
        YomotsuAchievement("read_250", "Leitor Nível 5", "Leia 250 capítulos.", AchievementCategory.READING, Tier.PRATA) { r, _, _ -> r >= 250 },
        YomotsuAchievement("read_500", "Leitor Nível 6", "Leia 500 capítulos.", AchievementCategory.READING, Tier.PRATA) { r, _, _ -> r >= 500 },
        YomotsuAchievement("read_1000", "Leitor Nível 7", "Leia 1000 capítulos.", AchievementCategory.READING, Tier.PRATA) { r, _, _ -> r >= 1000 },
        YomotsuAchievement("read_2000", "Leitor Nível 8", "Leia 2000 capítulos.", AchievementCategory.READING, Tier.OURO) { r, _, _ -> r >= 2000 },
        YomotsuAchievement("read_3500", "Leitor Nível 9", "Leia 3500 capítulos.", AchievementCategory.READING, Tier.OURO) { r, _, _ -> r >= 3500 },
        YomotsuAchievement("read_5000", "Leitor Nível 10", "Leia 5000 capítulos.", AchievementCategory.READING, Tier.RUBI) { r, _, _ -> r >= 5000 },

        // === MANGÁ - COLEÇÃO (10 Níveis Equilibrados) ===
        YomotsuAchievement("lib_1", "Colecionador Nível 1", "Tenha 1 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.BRONZE) { _, l, _ -> l >= 1 },
        YomotsuAchievement("lib_10", "Colecionador Nível 2", "Tenha 10 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.BRONZE) { _, l, _ -> l >= 10 },
        YomotsuAchievement("lib_25", "Colecionador Nível 3", "Tenha 25 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.BRONZE) { _, l, _ -> l >= 25 },
        YomotsuAchievement("lib_50", "Colecionador Nível 4", "Tenha 50 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.BRONZE) { _, l, _ -> l >= 50 },
        YomotsuAchievement("lib_100", "Colecionador Nível 5", "Tenha 100 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.PRATA) { _, l, _ -> l >= 100 },
        YomotsuAchievement("lib_200", "Colecionador Nível 6", "Tenha 200 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.PRATA) { _, l, _ -> l >= 200 },
        YomotsuAchievement("lib_350", "Colecionador Nível 7", "Tenha 350 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.PRATA) { _, l, _ -> l >= 350 },
        YomotsuAchievement("lib_500", "Colecionador Nível 8", "Tenha 500 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.OURO) { _, l, _ -> l >= 500 },
        YomotsuAchievement("lib_750", "Colecionador Nível 9", "Tenha 750 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.OURO) { _, l, _ -> l >= 750 },
        YomotsuAchievement("lib_1000", "Colecionador Nível 10", "Tenha 1000 obras na biblioteca.", AchievementCategory.COLLECTION, Tier.RUBI) { _, l, _ -> l >= 1000 },

        // === MANGÁ - DOWNLOADS (10 Níveis Equilibrados) ===
        YomotsuAchievement("dl_1", "Arquivista Nível 1", "Baixe 1 capítulos.", AchievementCategory.DOWNLOADS, Tier.BRONZE) { _, _, d -> d >= 1 },
        YomotsuAchievement("dl_25", "Arquivista Nível 2", "Baixe 25 capítulos.", AchievementCategory.DOWNLOADS, Tier.BRONZE) { _, _, d -> d >= 25 },
        YomotsuAchievement("dl_50", "Arquivista Nível 3", "Baixe 50 capítulos.", AchievementCategory.DOWNLOADS, Tier.BRONZE) { _, _, d -> d >= 50 },
        YomotsuAchievement("dl_100", "Arquivista Nível 4", "Baixe 100 capítulos.", AchievementCategory.DOWNLOADS, Tier.BRONZE) { _, _, d -> d >= 100 },
        YomotsuAchievement("dl_250", "Arquivista Nível 5", "Baixe 250 capítulos.", AchievementCategory.DOWNLOADS, Tier.PRATA) { _, _, d -> d >= 250 },
        YomotsuAchievement("dl_500", "Arquivista Nível 6", "Baixe 500 capítulos.", AchievementCategory.DOWNLOADS, Tier.PRATA) { _, _, d -> d >= 500 },
        YomotsuAchievement("dl_1000", "Arquivista Nível 7", "Baixe 1000 capítulos.", AchievementCategory.DOWNLOADS, Tier.PRATA) { _, _, d -> d >= 1000 },
        YomotsuAchievement("dl_2000", "Arquivista Nível 8", "Baixe 2000 capítulos.", AchievementCategory.DOWNLOADS, Tier.OURO) { _, _, d -> d >= 2000 },
        YomotsuAchievement("dl_3500", "Arquivista Nível 9", "Baixe 3500 capítulos.", AchievementCategory.DOWNLOADS, Tier.OURO) { _, _, d -> d >= 3500 },
        YomotsuAchievement("dl_5000", "Arquivista Nível 10", "Baixe 5000 capítulos.", AchievementCategory.DOWNLOADS, Tier.RUBI) { _, _, d -> d >= 5000 },

        // === NOVEL - LEITURA (10 Níveis Equilibrados) ===
        YomotsuAchievement("novel_read_1", "Leitor de Novel Nível 1", "Leia 1 capítulos de novel.", AchievementCategory.NOVEL, Tier.BRONZE) { it.novelChaptersRead >= 1 },
        YomotsuAchievement("novel_read_25", "Leitor de Novel Nível 2", "Leia 25 capítulos de novel.", AchievementCategory.NOVEL, Tier.BRONZE) { it.novelChaptersRead >= 25 },
        YomotsuAchievement("novel_read_50", "Leitor de Novel Nível 3", "Leia 50 capítulos de novel.", AchievementCategory.NOVEL, Tier.BRONZE) { it.novelChaptersRead >= 50 },
        YomotsuAchievement("novel_read_100", "Leitor de Novel Nível 4", "Leia 100 capítulos de novel.", AchievementCategory.NOVEL, Tier.BRONZE) { it.novelChaptersRead >= 100 },
        YomotsuAchievement("novel_read_250", "Leitor de Novel Nível 5", "Leia 250 capítulos de novel.", AchievementCategory.NOVEL, Tier.PRATA) { it.novelChaptersRead >= 250 },
        YomotsuAchievement("novel_read_500", "Leitor de Novel Nível 6", "Leia 500 capítulos de novel.", AchievementCategory.NOVEL, Tier.PRATA) { it.novelChaptersRead >= 500 },
        YomotsuAchievement("novel_read_1000", "Leitor de Novel Nível 7", "Leia 1000 capítulos de novel.", AchievementCategory.NOVEL, Tier.PRATA) { it.novelChaptersRead >= 1000 },
        YomotsuAchievement("novel_read_2000", "Leitor de Novel Nível 8", "Leia 2000 capítulos de novel.", AchievementCategory.NOVEL, Tier.OURO) { it.novelChaptersRead >= 2000 },
        YomotsuAchievement("novel_read_3500", "Leitor de Novel Nível 9", "Leia 3500 capítulos de novel.", AchievementCategory.NOVEL, Tier.OURO) { it.novelChaptersRead >= 3500 },
        YomotsuAchievement("novel_read_5000", "Leitor de Novel Nível 10", "Leia 5000 capítulos de novel.", AchievementCategory.NOVEL, Tier.RUBI) { it.novelChaptersRead >= 5000 },

        // === NOVEL - BIBLIOTECA (10 Níveis Equilibrados) ===
        YomotsuAchievement("novel_lib_1", "Bibliotecário Nível 1", "Tenha 1 novels na biblioteca.", AchievementCategory.NOVEL, Tier.BRONZE) { it.novelsInLibrary >= 1 },
        YomotsuAchievement("novel_lib_5", "Bibliotecário Nível 2", "Tenha 5 novels na biblioteca.", AchievementCategory.NOVEL, Tier.BRONZE) { it.novelsInLibrary >= 5 },
        YomotsuAchievement("novel_lib_15", "Bibliotecário Nível 3", "Tenha 15 novels na biblioteca.", AchievementCategory.NOVEL, Tier.BRONZE) { it.novelsInLibrary >= 15 },
        YomotsuAchievement("novel_lib_30", "Bibliotecário Nível 4", "Tenha 30 novels na biblioteca.", AchievementCategory.NOVEL, Tier.BRONZE) { it.novelsInLibrary >= 30 },
        YomotsuAchievement("novel_lib_50", "Bibliotecário Nível 5", "Tenha 50 novels na biblioteca.", AchievementCategory.NOVEL, Tier.PRATA) { it.novelsInLibrary >= 50 },
        YomotsuAchievement("novel_lib_100", "Bibliotecário Nível 6", "Tenha 100 novels na biblioteca.", AchievementCategory.NOVEL, Tier.PRATA) { it.novelsInLibrary >= 100 },
        YomotsuAchievement("novel_lib_150", "Bibliotecário Nível 7", "Tenha 150 novels na biblioteca.", AchievementCategory.NOVEL, Tier.PRATA) { it.novelsInLibrary >= 150 },
        YomotsuAchievement("novel_lib_200", "Bibliotecário Nível 8", "Tenha 200 novels na biblioteca.", AchievementCategory.NOVEL, Tier.OURO) { it.novelsInLibrary >= 200 },
        YomotsuAchievement("novel_lib_300", "Bibliotecário Nível 9", "Tenha 300 novels na biblioteca.", AchievementCategory.NOVEL, Tier.OURO) { it.novelsInLibrary >= 300 },
        YomotsuAchievement("novel_lib_500", "Bibliotecário Nível 10", "Tenha 500 novels na biblioteca.", AchievementCategory.NOVEL, Tier.RUBI) { it.novelsInLibrary >= 500 },

        // === NOVEL - DOWNLOADS (10 Níveis Equilibrados) ===
        YomotsuAchievement("novel_dl_1", "Acumulador de Novel Nível 1", "Baixe 1 capítulos de novel.", AchievementCategory.NOVEL, Tier.BRONZE) { it.novelDownloads >= 1 },
        YomotsuAchievement("novel_dl_25", "Acumulador de Novel Nível 2", "Baixe 25 capítulos de novel.", AchievementCategory.NOVEL, Tier.BRONZE) { it.novelDownloads >= 25 },
        YomotsuAchievement("novel_dl_50", "Acumulador de Novel Nível 3", "Baixe 50 capítulos de novel.", AchievementCategory.NOVEL, Tier.BRONZE) { it.novelDownloads >= 50 },
        YomotsuAchievement("novel_dl_100", "Acumulador de Novel Nível 4", "Baixe 100 capítulos de novel.", AchievementCategory.NOVEL, Tier.BRONZE) { it.novelDownloads >= 100 },
        YomotsuAchievement("novel_dl_250", "Acumulador de Novel Nível 5", "Baixe 250 capítulos de novel.", AchievementCategory.NOVEL, Tier.PRATA) { it.novelDownloads >= 250 },
        YomotsuAchievement("novel_dl_500", "Acumulador de Novel Nível 6", "Baixe 500 capítulos de novel.", AchievementCategory.NOVEL, Tier.PRATA) { it.novelDownloads >= 500 },
        YomotsuAchievement("novel_dl_1000", "Acumulador de Novel Nível 7", "Baixe 1000 capítulos de novel.", AchievementCategory.NOVEL, Tier.PRATA) { it.novelDownloads >= 1000 },
        YomotsuAchievement("novel_dl_2000", "Acumulador de Novel Nível 8", "Baixe 2000 capítulos de novel.", AchievementCategory.NOVEL, Tier.OURO) { it.novelDownloads >= 2000 },
        YomotsuAchievement("novel_dl_3500", "Acumulador de Novel Nível 9", "Baixe 3500 capítulos de novel.", AchievementCategory.NOVEL, Tier.OURO) { it.novelDownloads >= 3500 },
        YomotsuAchievement("novel_dl_5000", "Acumulador de Novel Nível 10", "Baixe 5000 capítulos de novel.", AchievementCategory.NOVEL, Tier.RUBI) { it.novelDownloads >= 5000 },

        // === ANIME - EPISÓDIOS ASSISTIDOS (10 Níveis Equilibrados) ===
        YomotsuAchievement("anime_watch_1", "Maratonista Nível 1", "Assista 1 episódios de anime.", AchievementCategory.ANIME, Tier.BRONZE) { it.episodesWatched >= 1 },
        YomotsuAchievement("anime_watch_12", "Maratonista Nível 2", "Assista 12 episódios (1 Temporada).", AchievementCategory.ANIME, Tier.BRONZE) { it.episodesWatched >= 12 },
        YomotsuAchievement("anime_watch_24", "Maratonista Nível 3", "Assista 24 episódios.", AchievementCategory.ANIME, Tier.BRONZE) { it.episodesWatched >= 24 },
        YomotsuAchievement("anime_watch_50", "Maratonista Nível 4", "Assista 50 episódios.", AchievementCategory.ANIME, Tier.BRONZE) { it.episodesWatched >= 50 },
        YomotsuAchievement("anime_watch_100", "Maratonista Nível 5", "Assista 100 episódios.", AchievementCategory.ANIME, Tier.PRATA) { it.episodesWatched >= 100 },
        YomotsuAchievement("anime_watch_250", "Maratonista Nível 6", "Assista 250 episódios.", AchievementCategory.ANIME, Tier.PRATA) { it.episodesWatched >= 250 },
        YomotsuAchievement("anime_watch_500", "Maratonista Nível 7", "Assista 500 episódios.", AchievementCategory.ANIME, Tier.PRATA) { it.episodesWatched >= 500 },
        YomotsuAchievement("anime_watch_750", "Maratonista Nível 8", "Assista 750 episódios.", AchievementCategory.ANIME, Tier.OURO) { it.episodesWatched >= 750 },
        YomotsuAchievement("anime_watch_1000", "Maratonista Nível 9", "Assista 1000 episódios.", AchievementCategory.ANIME, Tier.OURO) { it.episodesWatched >= 1000 },
        YomotsuAchievement("anime_watch_1500", "Maratonista Nível 10", "Assista 1500 episódios.", AchievementCategory.ANIME, Tier.RUBI) { it.episodesWatched >= 1500 },

        // === ANIME - BIBLIOTECA (10 Níveis Equilibrados) ===
        YomotsuAchievement("anime_lib_1", "Otaku Nível 1", "Tenha 1 animes na biblioteca.", AchievementCategory.ANIME, Tier.BRONZE) { it.animesInLibrary >= 1 },
        YomotsuAchievement("anime_lib_5", "Otaku Nível 2", "Tenha 5 animes na biblioteca.", AchievementCategory.ANIME, Tier.BRONZE) { it.animesInLibrary >= 5 },
        YomotsuAchievement("anime_lib_15", "Otaku Nível 3", "Tenha 15 animes na biblioteca.", AchievementCategory.ANIME, Tier.BRONZE) { it.animesInLibrary >= 15 },
        YomotsuAchievement("anime_lib_30", "Otaku Nível 4", "Tenha 30 animes na biblioteca.", AchievementCategory.ANIME, Tier.BRONZE) { it.animesInLibrary >= 30 },
        YomotsuAchievement("anime_lib_50", "Otaku Nível 5", "Tenha 50 animes na biblioteca.", AchievementCategory.ANIME, Tier.PRATA) { it.animesInLibrary >= 50 },
        YomotsuAchievement("anime_lib_100", "Otaku Nível 6", "Tenha 100 animes na biblioteca.", AchievementCategory.ANIME, Tier.PRATA) { it.animesInLibrary >= 100 },
        YomotsuAchievement("anime_lib_150", "Otaku Nível 7", "Tenha 150 animes na biblioteca.", AchievementCategory.ANIME, Tier.PRATA) { it.animesInLibrary >= 150 },
        YomotsuAchievement("anime_lib_200", "Otaku Nível 8", "Tenha 200 animes na biblioteca.", AchievementCategory.ANIME, Tier.OURO) { it.animesInLibrary >= 200 },
        YomotsuAchievement("anime_lib_300", "Otaku Nível 9", "Tenha 300 animes na biblioteca.", AchievementCategory.ANIME, Tier.OURO) { it.animesInLibrary >= 300 },
        YomotsuAchievement("anime_lib_500", "Otaku Nível 10", "Tenha 500 animes na biblioteca.", AchievementCategory.ANIME, Tier.RUBI) { it.animesInLibrary >= 500 },

        // === ANIME - DOWNLOADS (10 Níveis Equilibrados) ===
        YomotsuAchievement("anime_dl_1", "Fansubber Nível 1", "Baixe 1 episódios de anime.", AchievementCategory.ANIME, Tier.BRONZE) { it.animeDownloads >= 1 },
        YomotsuAchievement("anime_dl_6", "Fansubber Nível 2", "Baixe 6 episódios de anime.", AchievementCategory.ANIME, Tier.BRONZE) { it.animeDownloads >= 6 },
        YomotsuAchievement("anime_dl_12", "Fansubber Nível 3", "Baixe 12 episódios de anime.", AchievementCategory.ANIME, Tier.BRONZE) { it.animeDownloads >= 12 },
        YomotsuAchievement("anime_dl_24", "Fansubber Nível 4", "Baixe 24 episódios de anime.", AchievementCategory.ANIME, Tier.BRONZE) { it.animeDownloads >= 24 },
        YomotsuAchievement("anime_dl_50", "Fansubber Nível 5", "Baixe 50 episódios de anime.", AchievementCategory.ANIME, Tier.PRATA) { it.animeDownloads >= 50 },
        YomotsuAchievement("anime_dl_100", "Fansubber Nível 6", "Baixe 100 episódios de anime.", AchievementCategory.ANIME, Tier.PRATA) { it.animeDownloads >= 100 },
        YomotsuAchievement("anime_dl_200", "Fansubber Nível 7", "Baixe 200 episódios de anime.", AchievementCategory.ANIME, Tier.PRATA) { it.animeDownloads >= 200 },
        YomotsuAchievement("anime_dl_350", "Fansubber Nível 8", "Baixe 350 episódios de anime.", AchievementCategory.ANIME, Tier.OURO) { it.animeDownloads >= 350 },
        YomotsuAchievement("anime_dl_500", "Fansubber Nível 9", "Baixe 500 episódios de anime.", AchievementCategory.ANIME, Tier.OURO) { it.animeDownloads >= 500 },
        YomotsuAchievement("anime_dl_1000", "Fansubber Nível 10", "Baixe 1000 episódios de anime.", AchievementCategory.ANIME, Tier.RUBI) { it.animeDownloads >= 1000 },
    )
}
