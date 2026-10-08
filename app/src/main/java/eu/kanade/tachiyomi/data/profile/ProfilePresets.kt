package eu.kanade.tachiyomi.data.profile

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import eu.kanade.tachiyomi.R

data class ProfileAvatarBorder(
    val id: String,
    val name: String,
    val description: String,
    val colors: List<Color>,
    val isDynamicRank: Boolean = false,
)

data class ProfilePresetAvatar(
    val id: String,
    val name: String,
    val group: String,
    val icon: ImageVector? = null,
    val drawableRes: Int? = null,
    val backgroundGradient: List<Color> = listOf(Color(0xFF212121), Color(0xFF121212)),
    val iconTint: Color = Color.White,
    val isCharacter: Boolean = false,
)

data class ProfilePresetBanner(
    val id: String,
    val name: String,
    val group: String = "Paisagem",
    val drawableRes: Int? = null,
    val gradient: List<Color> = listOf(Color(0xFF1A1A1A), Color(0xFF000000))
)

object ProfilePresets {

    val PRESET_AVATARS: List<ProfilePresetAvatar> = listOf(
        ProfilePresetAvatar(
            id = "preset_yomotsu",
            name = "Yomotsu Original",
            group = "Yomotsu",
            drawableRes = R.drawable.ic_yomotsu_logo,
            backgroundGradient = listOf(Color(0xFF1E1E1E), Color(0xFF0A0A0A)),
            iconTint = Color.Unspecified,
            isCharacter = false
        ),
        ProfilePresetAvatar(
            id = "preset_sung_jinwoo",
            name = "Sung Jin-woo",
            group = "Solo Leveling",
            drawableRes = R.drawable.avatar_sung_jinwoo,
            backgroundGradient = listOf(Color(0xFF2E0249), Color(0xFF0F0E0E)),
            isCharacter = true
        ),
        ProfilePresetAvatar(
            id = "preset_gojo",
            name = "Satoru Gojo",
            group = "Jujutsu Kaisen",
            drawableRes = R.drawable.avatar_gojo,
            backgroundGradient = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
            isCharacter = true
        ),
        ProfilePresetAvatar(
            id = "preset_sukuna",
            name = "Ryomen Sukuna",
            group = "Jujutsu Kaisen",
            drawableRes = R.drawable.avatar_sukuna,
            backgroundGradient = listOf(Color(0xFF5B0014), Color(0xFF1A0005)),
            isCharacter = true
        ),
        ProfilePresetAvatar(
            id = "preset_luffy",
            name = "Monkey D. Luffy",
            group = "One Piece",
            drawableRes = R.drawable.avatar_luffy,
            backgroundGradient = listOf(Color(0xFFD32F2F), Color(0xFF7B1FA2)),
            isCharacter = true
        ),
        ProfilePresetAvatar(
            id = "preset_zoro",
            name = "Roronoa Zoro",
            group = "One Piece",
            drawableRes = R.drawable.avatar_zoro,
            backgroundGradient = listOf(Color(0xFF1B5E20), Color(0xFF004D40)),
            isCharacter = true
        ),
        ProfilePresetAvatar(
            id = "preset_naruto",
            name = "Naruto Uzumaki",
            group = "Naruto",
            drawableRes = R.drawable.avatar_naruto,
            backgroundGradient = listOf(Color(0xFFE65100), Color(0xFFBF360C)),
            isCharacter = true
        ),
        ProfilePresetAvatar(
            id = "preset_tanjiro",
            name = "Tanjiro Kamado",
            group = "Demon Slayer",
            drawableRes = R.drawable.avatar_tanjiro,
            backgroundGradient = listOf(Color(0xFF004D40), Color(0xFF00251A)),
            isCharacter = true
        ),
        ProfilePresetAvatar(
            id = "preset_nezuko",
            name = "Nezuko Kamado",
            group = "Demon Slayer",
            drawableRes = R.drawable.avatar_nezuko,
            backgroundGradient = listOf(Color(0xFFFF4081), Color(0xFF880E4F)),
            isCharacter = true
        ),
        ProfilePresetAvatar(
            id = "preset_ichigo",
            name = "Ichigo Kurosaki",
            group = "Bleach",
            drawableRes = R.drawable.avatar_ichigo,
            backgroundGradient = listOf(Color(0xFF212121), Color(0xFFE65100)),
            isCharacter = true
        ),
        ProfilePresetAvatar(
            id = "preset_saitama",
            name = "Saitama",
            group = "One Punch Man",
            drawableRes = R.drawable.avatar_saitama,
            backgroundGradient = listOf(Color(0xFFFFD700), Color(0xFFB71C1C)),
            isCharacter = true
        ),
        ProfilePresetAvatar(
            id = "preset_eren",
            name = "Eren Yeager",
            group = "Attack on Titan",
            drawableRes = R.drawable.avatar_eren,
            backgroundGradient = listOf(Color(0xFF3E2723), Color(0xFF1B0000)),
            isCharacter = true
        ),
        ProfilePresetAvatar(
            id = "preset_levi",
            name = "Levi Ackerman",
            group = "Attack on Titan",
            drawableRes = R.drawable.avatar_levi,
            backgroundGradient = listOf(Color(0xFF263238), Color(0xFF37474F)),
            isCharacter = true
        ),
        ProfilePresetAvatar(
            id = "preset_frieren",
            name = "Frieren",
            group = "Sousou no Frieren",
            drawableRes = R.drawable.avatar_frieren,
            backgroundGradient = listOf(Color(0xFF80DEEA), Color(0xFF006064)),
            isCharacter = true
        ),
        ProfilePresetAvatar(
            id = "preset_anya",
            name = "Anya Forger",
            group = "Spy x Family",
            drawableRes = R.drawable.avatar_anya,
            backgroundGradient = listOf(Color(0xFFFF80AB), Color(0xFFFF4081)),
            isCharacter = true
        ),
        ProfilePresetAvatar(
            id = "preset_killua",
            name = "Killua Zoldyck",
            group = "Hunter x Hunter",
            drawableRes = R.drawable.avatar_killua,
            backgroundGradient = listOf(Color(0xFF7C4DFF), Color(0xFF311B92)),
            isCharacter = true
        ),
        ProfilePresetAvatar(
            id = "preset_goku",
            name = "Son Goku",
            group = "Dragon Ball",
            drawableRes = R.drawable.avatar_goku,
            backgroundGradient = listOf(Color(0xFFFF6D00), Color(0xFF0D47A1)),
            isCharacter = true
        ),
        ProfilePresetAvatar(
            id = "preset_guts",
            name = "Guts",
            group = "Berserk",
            drawableRes = R.drawable.avatar_guts,
            backgroundGradient = listOf(Color(0xFF212121), Color(0xFF000000)),
            isCharacter = true
        ),
        ProfilePresetAvatar(
            id = "preset_rimuru",
            name = "Rimuru Tempest",
            group = "Slime Isekai",
            drawableRes = R.drawable.avatar_rimuru,
            backgroundGradient = listOf(Color(0xFF00B0FF), Color(0xFF0091EA)),
            isCharacter = true
        ),
        ProfilePresetAvatar(
            id = "preset_megumin",
            name = "Megumin",
            group = "KonoSuba",
            drawableRes = R.drawable.avatar_megumin,
            backgroundGradient = listOf(Color(0xFFD50000), Color(0xFFFF6D00)),
            isCharacter = true
        ),
        ProfilePresetAvatar(
            id = "preset_alucard",
            name = "Alucard",
            group = "Hellsing",
            drawableRes = R.drawable.avatar_alucard,
            backgroundGradient = listOf(Color(0xFFB71C1C), Color(0xFF000000)),
            isCharacter = true
        )
    )

    val PRESET_BANNERS: List<ProfilePresetBanner> = listOf(
        ProfilePresetBanner(
            id = "banner_sakura_night",
            name = "Cerejeiras ao Luar",
            group = "Japão / Shinto",
            drawableRes = R.drawable.banner_sakura_night
        ),
        ProfilePresetBanner(
            id = "banner_torii_sea",
            name = "Portal Torii Místico",
            group = "Japão / Místico",
            drawableRes = R.drawable.banner_torii_sea
        ),
        ProfilePresetBanner(
            id = "banner_starry_sky",
            name = "Céu Estrelado",
            group = "Your Name",
            drawableRes = R.drawable.banner_starry_sky
        ),
        ProfilePresetBanner(
            id = "banner_shadow_castle",
            name = "Castelo das Sombras",
            group = "Solo Leveling",
            drawableRes = R.drawable.banner_shadow_castle
        ),
        ProfilePresetBanner(
            id = "banner_cyber_city",
            name = "Neo Tokyo Cyberpunk",
            group = "Cyberpunk",
            drawableRes = R.drawable.banner_cyber_city
        ),
        ProfilePresetBanner(
            id = "banner_fuji_sunset",
            name = "Monte Fuji ao Entardecer",
            group = "Paisagem",
            drawableRes = R.drawable.banner_fuji_sunset
        ),
        ProfilePresetBanner(
            id = "banner_sakura_village",
            name = "Vila das Cerejeiras",
            group = "Paisagem",
            drawableRes = R.drawable.banner_sakura_village
        ),
        ProfilePresetBanner(
            id = "banner_tokyo_night",
            name = "Tóquio Iluminada",
            group = "Metrópole",
            drawableRes = R.drawable.banner_tokyo_night
        ),
        ProfilePresetBanner(
            id = "banner_bamboo_zen",
            name = "Floresta de Bambu",
            group = "Zen / Samurai",
            drawableRes = R.drawable.banner_bamboo_zen
        ),
        ProfilePresetBanner(
            id = "banner_synth_neon",
            name = "Horizonte Synthwave",
            group = "Retrowave",
            drawableRes = R.drawable.banner_synth_neon
        ),
        ProfilePresetBanner(
            id = "banner_lantern_shrine",
            name = "Templo das Lanternas",
            group = "Tradicional",
            drawableRes = R.drawable.banner_lantern_shrine
        ),
        ProfilePresetBanner(
            id = "banner_vibrant_blossom",
            name = "Primavera Imperial",
            group = "Paisagem",
            drawableRes = R.drawable.banner_vibrant_blossom
        ),
        ProfilePresetBanner(
            id = "banner_monarch",
            name = "Monarca das Sombras (Gradiente)",
            group = "Gradiente",
            gradient = listOf(Color(0xFF3A0CA3), Color(0xFF10002B), Color(0xFF000000))
        ),
        ProfilePresetBanner(
            id = "banner_abyss",
            name = "Abismo do Submundo (Gradiente)",
            group = "Gradiente",
            gradient = listOf(Color(0xFF1A1A1A), Color(0xFF000000))
        ),
        ProfilePresetBanner(
            id = "banner_flame",
            name = "Chama do Dragão (Gradiente)",
            group = "Gradiente",
            gradient = listOf(Color(0xFF900C3F), Color(0xFFC70039), Color(0xFF110000))
        ),
        ProfilePresetBanner(
            id = "banner_deep_blue",
            name = "Oceano Profundo (Gradiente)",
            group = "Gradiente",
            gradient = listOf(Color(0xFF002244), Color(0xFF004080), Color(0xFF000C1A))
        ),
        ProfilePresetBanner(
            id = "banner_emerald",
            name = "Esmeralda Ancestral (Gradiente)",
            group = "Gradiente",
            gradient = listOf(Color(0xFF0B3C1D), Color(0xFF1A532C), Color(0xFF05170B))
        ),
        ProfilePresetBanner(
            id = "banner_cyberpunk",
            name = "Cyberpunk Neón (Gradiente)",
            group = "Gradiente",
            gradient = listOf(Color(0xFFFF007F), Color(0xFF7928CA), Color(0xFF000000))
        ),
        ProfilePresetBanner(
            id = "banner_gold",
            name = "Ouro Real (Gradiente)",
            group = "Gradiente",
            gradient = listOf(Color(0xFFB8860B), Color(0xFF5E4503), Color(0xFF1E1700))
        ),
        ProfilePresetBanner(
            id = "banner_void",
            name = "Vazio Absoluto (Gradiente)",
            group = "Gradiente",
            gradient = listOf(Color(0xFF050505), Color(0xFF000000))
        )
    )

    val PRESET_BORDERS: List<ProfileAvatarBorder> = listOf(
        ProfileAvatarBorder(
            id = "border_white",
            name = "Branco Minimalista",
            description = "Borda limpa, elegante e moderna em tom branco puro",
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFEBEBEB), Color(0xFFF5F5F5), Color(0xFFFFFFFF))
        ),
        ProfileAvatarBorder(
            id = "border_black",
            name = "Preto Obsidiana",
            description = "Minimalista escuro sofisticado em ônix e obsidiana",
            colors = listOf(Color(0xFF2C2D30), Color(0xFF141414), Color(0xFF383A3F), Color(0xFF2C2D30))
        ),
        ProfileAvatarBorder(
            id = "border_auto",
            name = "Rank Dinâmico (Automático)",
            description = "Evolui e reflete a cor da sua patente atual de caçador",
            colors = emptyList(),
            isDynamicRank = true
        ),
        ProfileAvatarBorder(
            id = "border_monarch",
            name = "Monarca das Sombras",
            description = "Aura mística púrpura e violeta neón dos soberanos",
            colors = listOf(Color(0xFFC77DFF), Color(0xFF7209B7), Color(0xFF3A0CA3), Color(0xFFC77DFF))
        ),
        ProfileAvatarBorder(
            id = "border_gold",
            name = "Aura Dourada",
            description = "Resplendor dourado celestial dos caçadores lendários",
            colors = listOf(Color(0xFFFFE082), Color(0xFFFFD700), Color(0xFFFF8F00), Color(0xFFFFE082))
        ),
        ProfileAvatarBorder(
            id = "border_crimson",
            name = "Chama Carmesim",
            description = "Fogo escarlate vibrante e rubi apaixonado",
            colors = listOf(Color(0xFFFF5252), Color(0xFFFF1744), Color(0xFF880E4F), Color(0xFFFF5252))
        ),
        ProfileAvatarBorder(
            id = "border_cyber",
            name = "Cyberpunk Neón",
            description = "Gradiente futurista entre rosa choque e violeta neon",
            colors = listOf(Color(0xFFFF007F), Color(0xFF9B51E0), Color(0xFF00F5D4), Color(0xFFFF007F))
        ),
        ProfileAvatarBorder(
            id = "border_cyan",
            name = "Pulso Elétrico",
            description = "Energia ciano e azul elétrico cintilante",
            colors = listOf(Color(0xFF80D8FF), Color(0xFF00E5FF), Color(0xFF0091EA), Color(0xFF80D8FF))
        ),
        ProfileAvatarBorder(
            id = "border_emerald",
            name = "Esmeralda Ancestral",
            description = "Verde místico puro da floresta primordial",
            colors = listOf(Color(0xFF69F0AE), Color(0xFF00E676), Color(0xFF1B5E20), Color(0xFF69F0AE))
        ),
        ProfileAvatarBorder(
            id = "border_none",
            name = "Sem Borda",
            description = "Avatar limpo sem moldura ao redor",
            colors = listOf(Color.Transparent, Color.Transparent)
        )
    )

    fun getPresetAvatar(id: String?): ProfilePresetAvatar {
        val mappedId = when (id) {
            "preset_monarch" -> "preset_sung_jinwoo"
            "preset_shonen" -> "preset_luffy"
            "preset_shinigami" -> "preset_ichigo"
            "preset_hunter_s" -> "preset_gojo"
            "preset_marathoner" -> "preset_tanjiro"
            "preset_otaku_king" -> "preset_sukuna"
            "preset_explorer" -> "preset_zoro"
            "preset_mecha" -> "preset_saitama"
            "preset_detective" -> "preset_killua"
            "preset_sensei" -> "preset_goku"
            "preset_romance" -> "preset_frieren"
            "preset_awakened" -> "preset_eren"
            "preset_archivist" -> "preset_anya"
            "preset_librarian" -> "preset_nezuko"
            "preset_scholar" -> "preset_rimuru"
            else -> id
        }
        return PRESET_AVATARS.find { it.id == mappedId || it.id == id } ?: PRESET_AVATARS.first()
    }

    fun getPresetBanner(id: String?): ProfilePresetBanner {
        return PRESET_BANNERS.find { it.id == id } ?: PRESET_BANNERS.first()
    }

    fun getPresetBorder(id: String?): ProfileAvatarBorder {
        return PRESET_BORDERS.find { it.id == id } ?: PRESET_BORDERS.first { it.id == "border_auto" }
    }

    fun getRankBorderColors(level: Int): List<Color> {
        return when {
            level >= 200 -> listOf(Color(0xFFC77DFF), Color(0xFF7209B7), Color(0xFF3A0CA3), Color(0xFFC77DFF))
            level >= 160 -> listOf(Color(0xFFFFE082), Color(0xFFFFD700), Color(0xFFFF8F00), Color(0xFFFFE082))
            level >= 120 -> listOf(Color(0xFFFF5252), Color(0xFFFF1744), Color(0xFF880E4F), Color(0xFFFF5252))
            level >= 80 -> listOf(Color(0xFF80D8FF), Color(0xFF00E5FF), Color(0xFF0097A7), Color(0xFF80D8FF))
            level >= 50 -> listOf(Color(0xFFECEFF1), Color(0xFFB0BEC5), Color(0xFF78909C), Color(0xFFECEFF1))
            level >= 20 -> listOf(Color(0xFFD7CCC8), Color(0xFFCD7F32), Color(0xFF8D6E63), Color(0xFFD7CCC8))
            else -> listOf(Color(0xFF9E9E9E), Color(0xFF616161), Color(0xFF757575), Color(0xFF9E9E9E))
        }
    }

    fun getBorderBrush(borderId: String?, currentLevel: Int): Brush {
        if (borderId == "border_none") {
            return Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
        }
        val border = getPresetBorder(borderId)
        val colors = if (border.isDynamicRank) {
            getRankBorderColors(currentLevel)
        } else {
            border.colors
        }
        return Brush.sweepGradient(colors)
    }
}
