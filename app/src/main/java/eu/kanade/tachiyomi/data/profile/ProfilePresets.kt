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
    val unlockLevel: Int = 1,
    val rarity: YomotsuRarity = YomotsuRarity.fromLevel(unlockLevel),
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
            id = "banner_your_name",
            name = "Cometa Tiamat",
            group = "Your Name",
            drawableRes = R.drawable.banner_your_name
        ),
        ProfilePresetBanner(
            id = "banner_weathering",
            name = "Céu de Tóquio",
            group = "Weathering With You",
            drawableRes = R.drawable.banner_weathering
        ),
        ProfilePresetBanner(
            id = "banner_solo_leveling",
            name = "Portão das Sombras",
            group = "Solo Leveling",
            drawableRes = R.drawable.banner_solo_leveling
        ),
        ProfilePresetBanner(
            id = "banner_jujutsu_kaisen",
            name = "Santuário Malevolente",
            group = "Jujutsu Kaisen",
            drawableRes = R.drawable.banner_jujutsu_kaisen
        ),
        ProfilePresetBanner(
            id = "banner_demon_slayer_infinity",
            name = "Castelo Infinito",
            group = "Demon Slayer",
            drawableRes = R.drawable.banner_demon_slayer_infinity
        ),
        ProfilePresetBanner(
            id = "banner_one_piece_sunny",
            name = "Thousand Sunny",
            group = "One Piece",
            drawableRes = R.drawable.banner_one_piece_sunny
        ),
        ProfilePresetBanner(
            id = "banner_naruto_konoha",
            name = "Vila da Folha",
            group = "Naruto",
            drawableRes = R.drawable.banner_naruto_konoha
        ),
        ProfilePresetBanner(
            id = "banner_naruto_valley",
            name = "Vale do Fim",
            group = "Naruto",
            drawableRes = R.drawable.banner_naruto_valley
        ),
        ProfilePresetBanner(
            id = "banner_aot_shinganshina",
            name = "Muralha de Shinganshina",
            group = "Attack on Titan",
            drawableRes = R.drawable.banner_aot_shinganshina
        ),
        ProfilePresetBanner(
            id = "banner_frieren_meadow",
            name = "Colina das Flores",
            group = "Sousou no Frieren",
            drawableRes = R.drawable.banner_frieren_meadow
        ),
        ProfilePresetBanner(
            id = "banner_ghibli_spirited",
            name = "Trilhos no Mar",
            group = "A Viagem de Chihiro",
            drawableRes = R.drawable.banner_ghibli_spirited
        ),
        ProfilePresetBanner(
            id = "banner_ghibli_howl",
            name = "Jardim Secreto",
            group = "O Castelo Animado",
            drawableRes = R.drawable.banner_ghibli_howl
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
        // === COMUM (Nível 1 - 100) ===
        ProfileAvatarBorder(
            id = "border_none",
            name = "Sem Borda",
            description = "Avatar limpo sem moldura ao redor",
            colors = listOf(Color.Transparent, Color.Transparent),
            unlockLevel = 1,
            rarity = YomotsuRarity.COMMON
        ),
        ProfileAvatarBorder(
            id = "border_auto",
            name = "Rank Dinâmico",
            description = "Evolui e reflete a cor do seu nível atual",
            colors = emptyList(),
            isDynamicRank = true,
            unlockLevel = 1,
            rarity = YomotsuRarity.COMMON
        ),
        ProfileAvatarBorder(
            id = "border_white",
            name = "Branco Minimalista",
            description = "Borda limpa, elegante e moderna em tom branco puro",
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFE0E0E0), Color(0xFFF5F5F5), Color(0xFFFFFFFF)),
            unlockLevel = 10,
            rarity = YomotsuRarity.COMMON
        ),
        ProfileAvatarBorder(
            id = "border_copper",
            name = "Bronze do Aprendiz",
            description = "Tons quentes de cobre polido e bronze envelhecido",
            colors = listOf(Color(0xFFCD7F32), Color(0xFFB87333), Color(0xFF8D6E63), Color(0xFFCD7F32)),
            unlockLevel = 20,
            rarity = YomotsuRarity.COMMON
        ),
        ProfileAvatarBorder(
            id = "border_black",
            name = "Preto Obsidiana",
            description = "Minimalista escuro sofisticado em ônix e obsidiana",
            colors = listOf(Color(0xFF2C2D30), Color(0xFF141414), Color(0xFF383A3F), Color(0xFF2C2D30)),
            unlockLevel = 30,
            rarity = YomotsuRarity.COMMON
        ),
        ProfileAvatarBorder(
            id = "border_slate",
            name = "Ardósia Cinzenta",
            description = "Tom neutro escuro com textura sóbria de rocha",
            colors = listOf(Color(0xFF78909C), Color(0xFF546E7A), Color(0xFF37474F), Color(0xFF78909C)),
            unlockLevel = 45,
            rarity = YomotsuRarity.COMMON
        ),
        ProfileAvatarBorder(
            id = "border_steel",
            name = "Aço Forjado",
            description = "Gradiente metálico de lâmina forjada a frio",
            colors = listOf(Color(0xFFB0BEC5), Color(0xFF78909C), Color(0xFFCFD8DC), Color(0xFFB0BEC5)),
            unlockLevel = 60,
            rarity = YomotsuRarity.COMMON
        ),
        ProfileAvatarBorder(
            id = "border_brass",
            name = "Latão do Veterano",
            description = "Reflexos dourados envelhecidos de armadura clássica",
            colors = listOf(Color(0xFFD4AF37), Color(0xFFAA771C), Color(0xFF85580F), Color(0xFFD4AF37)),
            unlockLevel = 75,
            rarity = YomotsuRarity.COMMON
        ),
        ProfileAvatarBorder(
            id = "border_ironclad",
            name = "Couraça de Ferro",
            description = "Escudo de ferro temperado pronto para ascender",
            colors = listOf(Color(0xFF90A4AE), Color(0xFF607D8B), Color(0xFF455A64), Color(0xFF90A4AE)),
            unlockLevel = 90,
            rarity = YomotsuRarity.COMMON
        ),

        // === RARO (Nível 101 - 300) ===
        ProfileAvatarBorder(
            id = "border_cyan",
            name = "Pulso Elétrico",
            description = "Energia ciano e azul elétrico cintilante",
            colors = listOf(Color(0xFF80D8FF), Color(0xFF00E5FF), Color(0xFF0091EA), Color(0xFF80D8FF)),
            unlockLevel = 101,
            rarity = YomotsuRarity.RARE
        ),
        ProfileAvatarBorder(
            id = "border_sakura",
            name = "Flor de Cerejeira",
            description = "Pétalas suaves de sakura em rosa pastel e magenta",
            colors = listOf(Color(0xFFFFB7B2), Color(0xFFFF8DA1), Color(0xFFFF4081), Color(0xFFFFB7B2)),
            unlockLevel = 125,
            rarity = YomotsuRarity.RARE
        ),
        ProfileAvatarBorder(
            id = "border_emerald",
            name = "Esmeralda Ancestral",
            description = "Verde místico puro da floresta primordial",
            colors = listOf(Color(0xFF69F0AE), Color(0xFF00E676), Color(0xFF1B5E20), Color(0xFF69F0AE)),
            unlockLevel = 150,
            rarity = YomotsuRarity.RARE
        ),
        ProfileAvatarBorder(
            id = "border_topaz",
            name = "Topázio Radiante",
            description = "Brilho âmbar alaranjado de gema lapidada ao sol",
            colors = listOf(Color(0xFFFFB74D), Color(0xFFFFA726), Color(0xFFFF6F00), Color(0xFFFFB74D)),
            unlockLevel = 175,
            rarity = YomotsuRarity.RARE
        ),
        ProfileAvatarBorder(
            id = "border_amethyst",
            name = "Ametista Noturna",
            description = "Brilho cristalino púrpura e violeta profundo",
            colors = listOf(Color(0xFFBA68C8), Color(0xFF7B1FA2), Color(0xFF4A148C), Color(0xFFBA68C8)),
            unlockLevel = 200,
            rarity = YomotsuRarity.RARE
        ),
        ProfileAvatarBorder(
            id = "border_aqua",
            name = "Mar Profundo",
            description = "Ondas azuis abissais de tempestade oceânica",
            colors = listOf(Color(0xFF00B4D8), Color(0xFF0077B6), Color(0xFF03045E), Color(0xFF00B4D8)),
            unlockLevel = 225,
            rarity = YomotsuRarity.RARE
        ),
        ProfileAvatarBorder(
            id = "border_glacier",
            name = "Gelo Eterno",
            description = "Aura congelada de nevasca polar ártica",
            colors = listOf(Color(0xFFE0F7FA), Color(0xFF80DEEA), Color(0xFF00ACC1), Color(0xFFE0F7FA)),
            unlockLevel = 250,
            rarity = YomotsuRarity.RARE
        ),
        ProfileAvatarBorder(
            id = "border_sandstorm",
            name = "Areias do Deserto",
            description = "Vórtice dourado e terracota das dunas antigas",
            colors = listOf(Color(0xFFEDC988), Color(0xFFD79922), Color(0xFFC56D1A), Color(0xFFEDC988)),
            unlockLevel = 275,
            rarity = YomotsuRarity.RARE
        ),

        // === ÉPICO (Nível 301 - 500) ===
        ProfileAvatarBorder(
            id = "border_crimson",
            name = "Chama Carmesim",
            description = "Fogo escarlate vibrante e rubi apaixonado",
            colors = listOf(Color(0xFFFF5252), Color(0xFFFF1744), Color(0xFF880E4F), Color(0xFFFF5252)),
            unlockLevel = 301,
            rarity = YomotsuRarity.EPIC
        ),
        ProfileAvatarBorder(
            id = "border_susanoo",
            name = "Chamas Índigo",
            description = "Fogo espectral azul royal e índigo impenetrável",
            colors = listOf(Color(0xFF3F51B5), Color(0xFF303F9F), Color(0xFF1A237E), Color(0xFF3F51B5)),
            unlockLevel = 325,
            rarity = YomotsuRarity.EPIC
        ),
        ProfileAvatarBorder(
            id = "border_cyber",
            name = "Cyberpunk Neón",
            description = "Gradiente futurista entre rosa choque, violeta e ciano neon",
            colors = listOf(Color(0xFFFF007F), Color(0xFF9B51E0), Color(0xFF00F5D4), Color(0xFFFF007F)),
            unlockLevel = 350,
            rarity = YomotsuRarity.EPIC
        ),
        ProfileAvatarBorder(
            id = "border_blood_moon",
            name = "Lua de Sangue",
            description = "Carmesim escuro sombrio e vinho carmesim místico",
            colors = listOf(Color(0xFF880E4F), Color(0xFF4A000E), Color(0xFFD50000), Color(0xFF880E4F)),
            unlockLevel = 375,
            rarity = YomotsuRarity.EPIC
        ),
        ProfileAvatarBorder(
            id = "border_void",
            name = "Vazio Abissal",
            description = "Energia cósmica sombria que distorce o espaço",
            colors = listOf(Color(0xFF7C4DFF), Color(0xFF311B92), Color(0xFF12005E), Color(0xFF7C4DFF)),
            unlockLevel = 400,
            rarity = YomotsuRarity.EPIC
        ),
        ProfileAvatarBorder(
            id = "border_poison",
            name = "Miasma Venenoso",
            description = "Névoa tóxica verde-limão e púrpura necromântica",
            colors = listOf(Color(0xFF76FF03), Color(0xFF64DD17), Color(0xFF4A148C), Color(0xFF76FF03)),
            unlockLevel = 425,
            rarity = YomotsuRarity.EPIC
        ),
        ProfileAvatarBorder(
            id = "border_aurora",
            name = "Aurora Boreal",
            description = "Danças etéreas entre verde esmeralda e turquesa cintilante",
            colors = listOf(Color(0xFF00FFA3), Color(0xFF00D2FF), Color(0xFF7A00FF), Color(0xFF00FFA3)),
            unlockLevel = 450,
            rarity = YomotsuRarity.EPIC
        ),
        ProfileAvatarBorder(
            id = "border_amaterasu",
            name = "Chamas Negras",
            description = "Fogo negro inextinguível com borda violeta espectral",
            colors = listOf(Color(0xFF212121), Color(0xFF4A148C), Color(0xFF000000), Color(0xFF212121)),
            unlockLevel = 475,
            rarity = YomotsuRarity.EPIC
        ),

        // === LENDÁRIO (Nível 501 - 700) ===
        ProfileAvatarBorder(
            id = "border_monarch",
            name = "Monarca das Sombras",
            description = "Aura mística púrpura e violeta dos soberanos",
            colors = listOf(Color(0xFFC77DFF), Color(0xFF7209B7), Color(0xFF3A0CA3), Color(0xFFC77DFF)),
            unlockLevel = 501,
            rarity = YomotsuRarity.LEGENDARY
        ),
        ProfileAvatarBorder(
            id = "border_phoenix",
            name = "Renascimento da Fênix",
            description = "Chamas douradas, escarlates e penas ardentes imortais",
            colors = listOf(Color(0xFFFF3D00), Color(0xFFFF9100), Color(0xFFFFD600), Color(0xFFFF3D00)),
            unlockLevel = 525,
            rarity = YomotsuRarity.LEGENDARY
        ),
        ProfileAvatarBorder(
            id = "border_gold",
            name = "Aura Dourada Suprema",
            description = "Resplendor dourado celestial lendário dos campeões",
            colors = listOf(Color(0xFFFFE082), Color(0xFFFFD700), Color(0xFFFF8F00), Color(0xFFFFE082)),
            unlockLevel = 550,
            rarity = YomotsuRarity.LEGENDARY
        ),
        ProfileAvatarBorder(
            id = "border_domain",
            name = "Expansão de Domínio",
            description = "Vazio infinito cerúleo, ciano brilhante e branco puro",
            colors = listOf(Color(0xFF00F5D4), Color(0xFF00BBF9), Color(0xFFFFFFFF), Color(0xFF00F5D4)),
            unlockLevel = 575,
            rarity = YomotsuRarity.LEGENDARY
        ),
        ProfileAvatarBorder(
            id = "border_solar",
            name = "Inferno Solar",
            description = "Chamas solares ardentes em fusão de plasma radiante",
            colors = listOf(Color(0xFFFF9100), Color(0xFFFF3D00), Color(0xFFDD2C00), Color(0xFFFF9100)),
            unlockLevel = 600,
            rarity = YomotsuRarity.LEGENDARY
        ),
        ProfileAvatarBorder(
            id = "border_tensa",
            name = "Bankai Sombrio",
            description = "Preto carvão absoluto com raios carmesins cortantes",
            colors = listOf(Color(0xFF121212), Color(0xFFD50000), Color(0xFF000000), Color(0xFF121212)),
            unlockLevel = 625,
            rarity = YomotsuRarity.LEGENDARY
        ),
        ProfileAvatarBorder(
            id = "border_supernova",
            name = "Supernova Cósmica",
            description = "Explosão estelar em tons de rosa cósmico e azul profundo",
            colors = listOf(Color(0xFFFF4081), Color(0xFFE040FB), Color(0xFF536DFE), Color(0xFFFF4081)),
            unlockLevel = 650,
            rarity = YomotsuRarity.LEGENDARY
        ),
        ProfileAvatarBorder(
            id = "border_valkyrie",
            name = "Asas de Valquíria",
            description = "Prata celestial e ouro etéreo dos salões sagrados",
            colors = listOf(Color(0xFFECEFF1), Color(0xFFFFD700), Color(0xFFCFD8DC), Color(0xFFECEFF1)),
            unlockLevel = 675,
            rarity = YomotsuRarity.LEGENDARY
        ),

        // === MÍTICO (Nível 701 - 900) ===
        ProfileAvatarBorder(
            id = "border_dragon",
            name = "Fúria Dracônica",
            description = "Chamas de dragão ancestral com escamas de sangue e ouro",
            colors = listOf(Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFFFFEA00), Color(0xFFFF1744)),
            unlockLevel = 701,
            rarity = YomotsuRarity.MYTHIC
        ),
        ProfileAvatarBorder(
            id = "border_singularity",
            name = "Buraco Negro",
            description = "Horizonte gravitacional com distorção ultravioleta profunda",
            colors = listOf(Color(0xFF7B2CBF), Color(0xFF10002B), Color(0xFF3C096C), Color(0xFF7B2CBF)),
            unlockLevel = 725,
            rarity = YomotsuRarity.MYTHIC
        ),
        ProfileAvatarBorder(
            id = "border_nebula",
            name = "Nebulosa Celestial",
            description = "Poeira interestelar pulsando com gravidade quântica",
            colors = listOf(Color(0xFFEA80FC), Color(0xFFB388FF), Color(0xFF8C9EFF), Color(0xFFEA80FC)),
            unlockLevel = 750,
            rarity = YomotsuRarity.MYTHIC
        ),
        ProfileAvatarBorder(
            id = "border_time_warp",
            name = "Fluxo Temporal",
            description = "Ampulheta quântica de ouro cromado e ciano etéreo",
            colors = listOf(Color(0xFFFFD54F), Color(0xFF00E5FF), Color(0xFFFFB300), Color(0xFFFFD54F)),
            unlockLevel = 775,
            rarity = YomotsuRarity.MYTHIC
        ),
        ProfileAvatarBorder(
            id = "border_celestial",
            name = "Ruptura Dimensional",
            description = "Fenda no espaço-tempo emitida por relâmpagos prismáticos",
            colors = listOf(Color(0xFF00E5FF), Color(0xFFD500F9), Color(0xFF651FFF), Color(0xFF00E5FF)),
            unlockLevel = 800,
            rarity = YomotsuRarity.MYTHIC
        ),
        ProfileAvatarBorder(
            id = "border_chaos",
            name = "Caos Primordial",
            description = "Turbilhão cósmico desestabilizado de magenta, rubi e ônix",
            colors = listOf(Color(0xFFFF0055), Color(0xFF7928CA), Color(0xFF2A0845), Color(0xFFFF0055)),
            unlockLevel = 825,
            rarity = YomotsuRarity.MYTHIC
        ),
        ProfileAvatarBorder(
            id = "border_eclipse",
            name = "Eclipse da Coroa",
            description = "A sombra da lua cercada pela coroa incandescente violeta",
            colors = listOf(Color(0xFFFF0055), Color(0xFF5A189A), Color(0xFF10002B), Color(0xFFFF0055)),
            unlockLevel = 850,
            rarity = YomotsuRarity.MYTHIC
        ),
        ProfileAvatarBorder(
            id = "border_hyperdrive",
            name = "Dobra Espacial",
            description = "Relâmpagos azuis hiper-luminares e neon estelar veloz",
            colors = listOf(Color(0xFF00F5D4), Color(0xFF7B2CBF), Color(0xFF0077B6), Color(0xFF00F5D4)),
            unlockLevel = 875,
            rarity = YomotsuRarity.MYTHIC
        ),
        ProfileAvatarBorder(
            id = "border_apocalypse",
            name = "Fim dos Tempos",
            description = "Céu rubro flamejante de fim de mundo e cinzas sombrias",
            colors = listOf(Color(0xFFFF1744), Color(0xFFBF360C), Color(0xFF212121), Color(0xFFFF1744)),
            unlockLevel = 900,
            rarity = YomotsuRarity.MYTHIC
        ),

        // === DIVINO (Nível 901 - 1000) ===
        ProfileAvatarBorder(
            id = "border_godly",
            name = "Ascensão Divina",
            description = "Ouro sagrado, luz celestial perolada e brilho imortal",
            colors = listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFF00E5FF), Color(0xFFFFF9C4)),
            unlockLevel = 901,
            rarity = YomotsuRarity.DIVINE
        ),
        ProfileAvatarBorder(
            id = "border_nika",
            name = "Deus do Sol Nika",
            description = "Branco libertador puro resplandecente com chamas douradas",
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFF176), Color(0xFFFFD54F), Color(0xFFFFFFFF)),
            unlockLevel = 920,
            rarity = YomotsuRarity.DIVINE
        ),
        ProfileAvatarBorder(
            id = "border_genesis",
            name = "Gênese Cósmica",
            description = "O momento da criação em prisma estelar ultravioleta",
            colors = listOf(Color(0xFFF72585), Color(0xFF7209B7), Color(0xFF4CC9F0), Color(0xFFF72585)),
            unlockLevel = 940,
            rarity = YomotsuRarity.DIVINE
        ),
        ProfileAvatarBorder(
            id = "border_creator",
            name = "Divindade Primordial",
            description = "Prisma omnipotente da gênese do universo",
            colors = listOf(Color(0xFFFF1361), Color(0xFFFFF800), Color(0xFF00FFC2), Color(0xFFFF1361)),
            unlockLevel = 950,
            rarity = YomotsuRarity.DIVINE
        ),
        ProfileAvatarBorder(
            id = "border_eternity",
            name = "Trono da Eternidade",
            description = "Ouro puro fundido com obsidiana estelar e diamante",
            colors = listOf(Color(0xFFFFD700), Color(0xFF212121), Color(0xFFE0E0E0), Color(0xFFFFD700)),
            unlockLevel = 970,
            rarity = YomotsuRarity.DIVINE
        ),
        ProfileAvatarBorder(
            id = "border_omniscient",
            name = "Olho Onisciente",
            description = "Íris divina de luz prismática absoluta que tudo vê",
            colors = listOf(Color(0xFF00FFFF), Color(0xFFFF00FF), Color(0xFFFFFF00), Color(0xFF00FFFF)),
            unlockLevel = 985,
            rarity = YomotsuRarity.DIVINE
        ),
        ProfileAvatarBorder(
            id = "border_singularity_core",
            name = "Coração do Multiverso",
            description = "Singularidade cósmica radiante em harmonia transcendental",
            colors = listOf(Color(0xFFE040FB), Color(0xFF00E5FF), Color(0xFFFFEA00), Color(0xFFE040FB)),
            unlockLevel = 995,
            rarity = YomotsuRarity.DIVINE
        ),
        ProfileAvatarBorder(
            id = "border_infinity",
            name = "Soberano do Yomotsu",
            description = "O pináculo supremo do Yomotsu: poder infinito e absoluto",
            colors = listOf(Color(0xFFFFD700), Color(0xFF9400D3), Color(0xFF00FFFF), Color(0xFFFFD700)),
            unlockLevel = 1000,
            rarity = YomotsuRarity.DIVINE
        )
    )

    fun getPresetAvatar(id: String?): ProfilePresetAvatar {
        val mappedId = when (id) {
            "preset_yomotsu" -> "preset_sung_jinwoo"
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
        val mappedId = when (id) {
            "banner_starry_sky" -> "banner_your_name"
            "banner_shadow_castle" -> "banner_solo_leveling"
            "banner_fuji_sunset", "banner_one_piece_wano" -> "banner_one_piece_sunny"
            "banner_sakura_village", "banner_demon_slayer_village" -> "banner_ghibli_howl"
            "banner_sakura_night", "banner_demon_slayer_wisteria" -> "banner_demon_slayer_infinity"
            "banner_tokyo_night" -> "banner_jujutsu_kaisen"
            "banner_torii_sea" -> "banner_ghibli_spirited"
            "banner_bamboo_zen" -> "banner_naruto_valley"
            "banner_vibrant_blossom" -> "banner_frieren_meadow"
            "banner_cyber_city" -> "banner_weathering"
            "banner_lantern_shrine" -> "banner_naruto_konoha"
            "banner_synth_neon" -> "banner_aot_shinganshina"
            else -> id
        }
        return PRESET_BANNERS.find { it.id == mappedId || it.id == id } ?: PRESET_BANNERS.first()
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
