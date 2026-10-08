package eu.kanade.tachiyomi.data.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import eu.kanade.tachiyomi.R

data class ProfilePresetAvatar(
    val id: String,
    val name: String,
    val group: String,
    val icon: ImageVector? = null,
    val drawableRes: Int? = null,
    val backgroundGradient: List<Color> = listOf(Color(0xFF212121), Color(0xFF121212)),
    val iconTint: Color = Color.White
)

data class ProfilePresetBanner(
    val id: String,
    val name: String,
    val gradient: List<Color>
)

object ProfilePresets {

    val PRESET_AVATARS: List<ProfilePresetAvatar> = listOf(
        ProfilePresetAvatar(
            id = "preset_yomotsu",
            name = "Yomotsu Original",
            group = "Yomotsu",
            drawableRes = R.drawable.ic_yomotsu_logo,
            backgroundGradient = listOf(Color(0xFF1E1E1E), Color(0xFF0A0A0A)),
            iconTint = Color.Unspecified
        ),
        ProfilePresetAvatar(
            id = "preset_monarch",
            name = "Monarca das Sombras",
            group = "Caçadores",
            icon = Icons.Outlined.LocalFireDepartment,
            backgroundGradient = listOf(Color(0xFF2E0249), Color(0xFF0F0E0E)),
            iconTint = Color(0xFFC77DFF)
        ),
        ProfilePresetAvatar(
            id = "preset_hunter_s",
            name = "Caçador Rank S",
            group = "Caçadores",
            icon = Icons.Outlined.Shield,
            backgroundGradient = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
            iconTint = Color(0xFFFFD700)
        ),
        ProfilePresetAvatar(
            id = "preset_shinigami",
            name = "Ceifador de Almas",
            group = "Místicos",
            icon = Icons.Outlined.DarkMode,
            backgroundGradient = listOf(Color(0xFF000000), Color(0xFF212121)),
            iconTint = Color(0xFFEEEEEE)
        ),
        ProfilePresetAvatar(
            id = "preset_shonen",
            name = "Protagonista Shonen",
            group = "Anime",
            icon = Icons.Outlined.Star,
            backgroundGradient = listOf(Color(0xFFFF512F), Color(0xFFDD2476)),
            iconTint = Color(0xFFFFF176)
        ),
        ProfilePresetAvatar(
            id = "preset_marathoner",
            name = "Maratonista Noturno",
            group = "Anime",
            icon = Icons.Outlined.PlayCircleOutline,
            backgroundGradient = listOf(Color(0xFF141E30), Color(0xFF243B55)),
            iconTint = Color(0xFF00E5FF)
        ),
        ProfilePresetAvatar(
            id = "preset_otaku_king",
            name = "Rei Otaku",
            group = "Anime",
            icon = Icons.Outlined.EmojiEvents,
            backgroundGradient = listOf(Color(0xFF5A189A), Color(0xFF3C096C)),
            iconTint = Color(0xFFFFD700)
        ),
        ProfilePresetAvatar(
            id = "preset_explorer",
            name = "Explorador de Dungeons",
            group = "Caçadores",
            icon = Icons.Outlined.TravelExplore,
            backgroundGradient = listOf(Color(0xFF134E5E), Color(0xFF71B280)),
            iconTint = Color.White
        ),
        ProfilePresetAvatar(
            id = "preset_mecha",
            name = "Ciborgue / Mecha",
            group = "Ficção",
            icon = Icons.Outlined.SmartToy,
            backgroundGradient = listOf(Color(0xFF1F1C2C), Color(0xFF928DAB)),
            iconTint = Color(0xFF00F5D4)
        ),
        ProfilePresetAvatar(
            id = "preset_detective",
            name = "Detetive Genial",
            group = "Manga",
            icon = Icons.Outlined.Search,
            backgroundGradient = listOf(Color(0xFF3E2723), Color(0xFF1B0000)),
            iconTint = Color(0xFFFFB74D)
        ),
        ProfilePresetAvatar(
            id = "preset_sensei",
            name = "Grande Mestre",
            group = "Manga",
            icon = Icons.Outlined.AutoStories,
            backgroundGradient = listOf(Color(0xFF4A148C), Color(0xFF1A237E)),
            iconTint = Color(0xFFE1BEE7)
        ),
        ProfilePresetAvatar(
            id = "preset_romance",
            name = "Amante de Shoujo",
            group = "Anime",
            icon = Icons.Outlined.Favorite,
            backgroundGradient = listOf(Color(0xFFFF758C), Color(0xFFFF7EB3)),
            iconTint = Color.White
        ),
        ProfilePresetAvatar(
            id = "preset_awakened",
            name = "Recém-Desperto",
            group = "Caçadores",
            icon = Icons.Outlined.NewReleases,
            backgroundGradient = listOf(Color(0xFFE65100), Color(0xFFBF360C)),
            iconTint = Color.White
        ),
        ProfilePresetAvatar(
            id = "preset_archivist",
            name = "Grande Arquivista",
            group = "Yomotsu",
            icon = Icons.Outlined.CloudDownload,
            backgroundGradient = listOf(Color(0xFF0D47A1), Color(0xFF01579B)),
            iconTint = Color(0xFF80D8FF)
        ),
        ProfilePresetAvatar(
            id = "preset_librarian",
            name = "Guardião da Biblioteca",
            group = "Yomotsu",
            icon = Icons.Outlined.LibraryBooks,
            backgroundGradient = listOf(Color(0xFF1B5E20), Color(0xFF004D40)),
            iconTint = Color(0xFFA7FFEB)
        ),
        ProfilePresetAvatar(
            id = "preset_scholar",
            name = "Estudioso dos Tomos",
            group = "Manga",
            icon = Icons.Outlined.MenuBook,
            backgroundGradient = listOf(Color(0xFF263238), Color(0xFF37474F)),
            iconTint = Color(0xFFECEFF1)
        )
    )

    val PRESET_BANNERS: List<ProfilePresetBanner> = listOf(
        ProfilePresetBanner(
            id = "banner_abyss",
            name = "Abismo do Submundo",
            gradient = listOf(Color(0xFF1A1A1A), Color(0xFF000000))
        ),
        ProfilePresetBanner(
            id = "banner_monarch",
            name = "Monarca das Sombras",
            gradient = listOf(Color(0xFF3A0CA3), Color(0xFF10002B), Color(0xFF000000))
        ),
        ProfilePresetBanner(
            id = "banner_flame",
            name = "Chama do Dragão",
            gradient = listOf(Color(0xFF900C3F), Color(0xFFC70039), Color(0xFF110000))
        ),
        ProfilePresetBanner(
            id = "banner_deep_blue",
            name = "Oceano Profundo",
            gradient = listOf(Color(0xFF002244), Color(0xFF004080), Color(0xFF000C1A))
        ),
        ProfilePresetBanner(
            id = "banner_emerald",
            name = "Esmeralda Ancestral",
            gradient = listOf(Color(0xFF0B3C1D), Color(0xFF1A532C), Color(0xFF05170B))
        ),
        ProfilePresetBanner(
            id = "banner_cyberpunk",
            name = "Cyberpunk Neón",
            gradient = listOf(Color(0xFFFF007F), Color(0xFF7928CA), Color(0xFF000000))
        ),
        ProfilePresetBanner(
            id = "banner_gold",
            name = "Ouro Real",
            gradient = listOf(Color(0xFFB8860B), Color(0xFF5E4503), Color(0xFF1E1700))
        ),
        ProfilePresetBanner(
            id = "banner_void",
            name = "Vazio Absoluto",
            gradient = listOf(Color(0xFF050505), Color(0xFF000000))
        )
    )

    fun getPresetAvatar(id: String?): ProfilePresetAvatar {
        return PRESET_AVATARS.find { it.id == id } ?: PRESET_AVATARS.first()
    }

    fun getPresetBanner(id: String?): ProfilePresetBanner {
        return PRESET_BANNERS.find { it.id == id } ?: PRESET_BANNERS.first()
    }
}
