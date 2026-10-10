package eu.kanade.tachiyomi.data.profile

import androidx.compose.ui.graphics.Color

data class YomotsuTitle(
    val unlockLevel: Int,
    val name: String,
    val colorStart: Color,
    val colorEnd: Color
)

object YomotsuLevelManager {

    const val MAX_LEVEL = 500
    const val XP_PER_CHAPTER_READ = 90
    const val XP_PER_CHAPTER_DOWNLOAD = 90
    const val XP_PER_EPISODE_WATCHED = 90
    const val XP_PER_EPISODE_DOWNLOAD = 90

    // Lista de Títulos baseada na evolução do Nível 1 ao 500
    val ALL_TITLES = listOf(
        // Fase 1: O Despertar (Nível 1 ao 100)
        YomotsuTitle(1, "Mortal Comum", Color(0xFFBDBDBD), Color(0xFF757575)),
        YomotsuTitle(10, "Desperto", Color(0xFF8D6E63), Color(0xFF5D4037)),
        YomotsuTitle(20, "Leitor Novato", Color(0xFF81D4FA), Color(0xFF0288D1)),
        YomotsuTitle(30, "Caçador Rank E", Color(0xFF90A4AE), Color(0xFF546E7A)),
        YomotsuTitle(40, "Caçador Rank D", Color(0xFFAED581), Color(0xFF558B2F)),
        YomotsuTitle(50, "Caçador Rank C", Color(0xFF4FC3F7), Color(0xFF0277BD)),
        YomotsuTitle(60, "Caçador Rank B", Color(0xFF4DB6AC), Color(0xFF00695C)),
        YomotsuTitle(70, "Caçador Rank A", Color(0xFFF06292), Color(0xFFC2185B)),
        YomotsuTitle(80, "Caçador Rank S", Color(0xFFFFF176), Color(0xFFFBC02D)),
        YomotsuTitle(90, "Caçador Nacional", Color(0xFFFFB74D), Color(0xFFEF6C00)),
        YomotsuTitle(100, "Autoridade Suprema", Color(0xFFE57373), Color(0xFFC62828)),

        // Fase 2: O Caminho Marcial (Nível 110 ao 200)
        YomotsuTitle(110, "Refinador de Qi", Color(0xFFE0F7FA), Color(0xFF80DEEA)),
        YomotsuTitle(120, "Discípulo Externo", Color(0xFFFFCC80), Color(0xFFFF8F00)),
        YomotsuTitle(130, "Formação do Núcleo", Color(0xFFA5D6A7), Color(0xFF2E7D32)),
        YomotsuTitle(140, "Alma Nascente", Color(0xFF18FFFF), Color(0xFF00B8D4)),
        YomotsuTitle(150, "Senhor de Domínio", Color(0xFFFF9E80), Color(0xFFDD2C00)),
        YomotsuTitle(160, "Especialista Marcial", Color(0xFFFF5252), Color(0xFFD50000)),
        YomotsuTitle(170, "Soberano Marcial", Color(0xFFB388FF), Color(0xFF6200EA)),
        YomotsuTitle(180, "Monarca", Color(0xFFECEFF1), Color(0xFF90A4AE)),
        YomotsuTitle(190, "Imortal", Color(0xFFFFF59D), Color(0xFFF57F17)),
        YomotsuTitle(200, "Divindade Marcial", Color(0xFFFFD54F), Color(0xFF1A1A1A)),

        // Fase 3: O Buscador do Conhecimento (Nível 210 ao 300)
        YomotsuTitle(210, "Rato de Biblioteca", Color(0xFFBCAAA4), Color(0xFF6D4C41)),
        YomotsuTitle(220, "Colecionador de Tomos", Color(0xFFFFE082), Color(0xFFF57F17)),
        YomotsuTitle(230, "Devorador de Páginas", Color(0xFF9FA8DA), Color(0xFF283593)),
        YomotsuTitle(240, "Erudito das Sombras", Color(0xFF78909C), Color(0xFF263238)),
        YomotsuTitle(250, "Guardião dos Pergaminhos", Color(0xFFCFD8DC), Color(0xFF455A64)),
        YomotsuTitle(260, "Bibliotecário do Abismo", Color(0xFF1A237E), Color(0xFF000000)),
        YomotsuTitle(270, "Arquivista Akáshico", Color(0xFFE040FB), Color(0xFF4A148C)),
        YomotsuTitle(280, "Senhor das Crônicas", Color(0xFFCE93D8), Color(0xFF6A1B9A)),
        YomotsuTitle(290, "Leitor Onisciente", Color(0xFFFFFFFF), Color(0xFF424242)),
        YomotsuTitle(300, "Aquele Que Tudo Lê", Color(0xFFE1F5FE), Color(0xFF01579B)),

        // Fase 4: O Domínio das Constelações (Nível 310 ao 400)
        YomotsuTitle(310, "Encarnação", Color(0xFFFFAB40), Color(0xFFFF6D00)),
        YomotsuTitle(320, "Transcendente", Color(0xFF69F0AE), Color(0xFF00C853)),
        YomotsuTitle(330, "Semideus", Color(0xFFFFE082), Color(0xFFFF8F00)),
        YomotsuTitle(340, "Constelação Menor", Color(0xFF448AFF), Color(0xFF2962FF)),
        YomotsuTitle(350, "Mito Esquecido", Color(0xFFB0BEC5), Color(0xFF37474F)),
        YomotsuTitle(360, "Constelação Soberana", Color(0xFF7C4DFF), Color(0xFF311B92)),
        YomotsuTitle(370, "Executor de Mundos", Color(0xFFFF5252), Color(0xFFBF360C)),
        YomotsuTitle(380, "Rei Demônio", Color(0xFF212121), Color(0xFFD50000)),
        YomotsuTitle(390, "Shinigami", Color(0xFF424242), Color(0xFF212121)),
        YomotsuTitle(400, "Rei do Submundo", Color(0xFF1A1A1A), Color(0xFF4A148C)),

        // Fase 5: O Fim do Abismo (Nível 410 ao 500)
        YomotsuTitle(410, "Entidade Conceptual", Color(0xFF00E5FF), Color(0xFF006064)),
        YomotsuTitle(420, "Devorador de Dimensões", Color(0xFF1A1A1A), Color(0xFF00E676)),
        YomotsuTitle(430, "Arauto do Vazio", Color(0xFF311B92), Color(0xFF000000)),
        YomotsuTitle(440, "Ancestral do Dao", Color(0xFFFFF9C4), Color(0xFFFBC02D)),
        YomotsuTitle(450, "Tecelão do Destino", Color(0xFFFF4081), Color(0xFF00E5FF)),
        YomotsuTitle(460, "Izanagi", Color(0xFF1A1A1A), Color(0xFFFFD54F)),
        YomotsuTitle(470, "Deus Externo", Color(0xFF000051), Color(0xFF000000)),
        YomotsuTitle(480, "Avatar do Sistema", Color(0xFF76FF03), Color(0xFFD50000)),
        YomotsuTitle(490, "A Singularidade", Color(0xFFFFFFFF), Color(0xFFE0E0E0)),
        YomotsuTitle(500, "O Vazio Absoluto", Color(0xFF000000), Color(0xFFFFFFFF))
    )

    /**
     * Fórmula Curva Exponencial (Quadrática)
     * XP(Level) = 125 * (Level - 1)^2
     */
    fun calculateLevelFromXp(xp: Long): Int {
        var level = 1
        while (level < MAX_LEVEL) {
            val nextLevelXp = 125L * (level) * (level)
            if (xp < nextLevelXp) {
                break
            }
            level++
        }
        return level
    }

    fun getXpRequiredForLevel(level: Int): Long {
        if (level <= 1) return 0L
        if (level > MAX_LEVEL) return 125L * (MAX_LEVEL - 1) * (MAX_LEVEL - 1)
        return 125L * (level - 1) * (level - 1)
    }

    fun getUnlockedTitles(currentLevel: Int): List<YomotsuTitle> {
        return ALL_TITLES.filter { it.unlockLevel <= currentLevel }.reversed() // Mostra os mais picas primeiro
    }

    fun getCurrentTitleByLevel(level: Int): YomotsuTitle {
        return ALL_TITLES.lastOrNull { it.unlockLevel <= level } ?: ALL_TITLES.first()
    }
}
