package eu.kanade.tachiyomi.data.profile

import androidx.compose.ui.graphics.Color

data class YomotsuTitle(
    val unlockLevel: Int,
    val name: String,
    val colorStart: Color,
    val colorEnd: Color
)

object YomotsuLevelManager {

    const val MAX_LEVEL = 1000
    const val XP_PER_CHAPTER_READ = 150
    const val XP_PER_CHAPTER_DOWNLOAD = 50
    const val XP_PER_EPISODE_WATCHED = 200
    const val XP_PER_EPISODE_DOWNLOAD = 50

    // Lista de Títulos baseada na evolução do Nível 1 ao 1000
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
        YomotsuTitle(290, "Sábio da Leitura", Color(0xFFFFFFFF), Color(0xFF424242)),
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

        // Fase 5: O Abismo Dimensional (Nível 410 ao 500)
        YomotsuTitle(410, "Entidade Conceptual", Color(0xFF00E5FF), Color(0xFF006064)),
        YomotsuTitle(420, "Devorador de Dimensões", Color(0xFF1A1A1A), Color(0xFF00E676)),
        YomotsuTitle(430, "Arauto do Vazio", Color(0xFF311B92), Color(0xFF000000)),
        YomotsuTitle(440, "Ancestral do Dao", Color(0xFFFFF9C4), Color(0xFFFBC02D)),
        YomotsuTitle(450, "Tecelão do Destino", Color(0xFFFF4081), Color(0xFF00E5FF)),
        YomotsuTitle(460, "Izanagi", Color(0xFF1A1A1A), Color(0xFFFFD54F)),
        YomotsuTitle(470, "Deus Externo", Color(0xFF000051), Color(0xFF000000)),
        YomotsuTitle(480, "Avatar do Sistema", Color(0xFF76FF03), Color(0xFFD50000)),
        YomotsuTitle(490, "A Singularidade", Color(0xFFFFFFFF), Color(0xFFE0E0E0)),
        YomotsuTitle(500, "Soberano do Tempo", Color(0xFFF0F4C3), Color(0xFF827717)),

        // Fase 6: O Caminho da Onisciência (Nível 510 ao 600)
        YomotsuTitle(510, "Observador Etéreo", Color(0xFFE8EAF6), Color(0xFF3F51B5)),
        YomotsuTitle(520, "Leitor Clarividente", Color(0xFFB3E5FC), Color(0xFF0277BD)),
        YomotsuTitle(530, "Oráculo de Histórias", Color(0xFFD1C4E9), Color(0xFF512DA8)),
        YomotsuTitle(540, "Devorador de Enredos", Color(0xFFFFCDD2), Color(0xFFD32F2F)),
        YomotsuTitle(550, "O Olho Que Tudo Vê", Color(0xFFFFF9C4), Color(0xFFF57F17)),
        YomotsuTitle(560, "Viajante do Multiverso", Color(0xFF00BFA5), Color(0xFF004D40)),
        YomotsuTitle(570, "Guardião da Quarta Parede", Color(0xFFCFD8DC), Color(0xFF263238)),
        YomotsuTitle(580, "Sábio da Eternidade", Color(0xFFF5F5F5), Color(0xFF9E9E9E)),
        YomotsuTitle(590, "Mente Cósmica", Color(0xFFEA80FC), Color(0xFFAA00FF)),
        YomotsuTitle(600, "Leitor Onipresente", Color(0xFFB2EBF2), Color(0xFF006064)),

        // Fase 7: Domínio Divino (Nível 610 ao 700)
        YomotsuTitle(610, "Senhor do Karma", Color(0xFFFF8A80), Color(0xFFD50000)),
        YomotsuTitle(620, "Manipulador do Destino", Color(0xFF8C9EFF), Color(0xFF304FFE)),
        YomotsuTitle(630, "Juiz Celestial", Color(0xFFFFF176), Color(0xFFF57F17)),
        YomotsuTitle(640, "O Fim das Eras", Color(0xFF1A1A1A), Color(0xFFD50000)),
        YomotsuTitle(650, "Caminhante do Infinito", Color(0xFF84FFFF), Color(0xFF00B8D4)),
        YomotsuTitle(660, "Deus Criador", Color(0xFFF8BBD0), Color(0xFFC2185B)),
        YomotsuTitle(670, "Deus Destruidor", Color(0xFF1A1A1A), Color(0xFFFF0000)),
        YomotsuTitle(680, "Supremo Primordial", Color(0xFFD7CCC8), Color(0xFF3E2723)),
        YomotsuTitle(690, "A Fonte", Color(0xFFFFFFFF), Color(0xFF00E5FF)),
        YomotsuTitle(700, "Leitor Onisciente", Color(0xFFFFD54F), Color(0xFFFF6D00)),

        // Fase 8: Além da Compressão (Nível 710 ao 800)
        YomotsuTitle(710, "Aquele que Viveu Mil Vidas", Color(0xFFB2FF59), Color(0xFF64DD17)),
        YomotsuTitle(720, "Buda Demoníaco", Color(0xFF1A1A1A), Color(0xFFAA00FF)),
        YomotsuTitle(730, "Imperador Imortal", Color(0xFFFFE082), Color(0xFFFF8F00)),
        YomotsuTitle(740, "Senhor do Ciclo de Reencarnação", Color(0xFF4FC3F7), Color(0xFF0D47A1)),
        YomotsuTitle(750, "A Vontade do Céu", Color(0xFFFFFFFF), Color(0xFF448AFF)),
        YomotsuTitle(760, "A Lei Suprema", Color(0xFFE0E0E0), Color(0xFF212121)),
        YomotsuTitle(770, "O Paradoxo", Color(0xFF64FFDA), Color(0xFF00BFA5)),
        YomotsuTitle(780, "Ser Além do Sistema", Color(0xFFFF1744), Color(0xFF000000)),
        YomotsuTitle(790, "Sonhador do Fim", Color(0xFFD1C4E9), Color(0xFF311B92)),
        YomotsuTitle(800, "Leitor Onipotente", Color(0xFFFFE57F), Color(0xFFFFAB00)),

        // Fase 9: A Gênese (Nível 810 ao 900)
        YomotsuTitle(810, "Aquele que Escreve as Estrelas", Color(0xFF80D8FF), Color(0xFF0091EA)),
        YomotsuTitle(820, "O Falso Deus", Color(0xFF1A1A1A), Color(0xFF64FFDA)),
        YomotsuTitle(830, "Progenitor", Color(0xFFCCFF90), Color(0xFF76FF03)),
        YomotsuTitle(840, "Arquimago Universal", Color(0xFFEA80FC), Color(0xFF6200EA)),
        YomotsuTitle(850, "Entidade Pura", Color(0xFFFFFFFF), Color(0xFFE0F7FA)),
        YomotsuTitle(860, "Senhor de Todas as Fics", Color(0xFFFF8A65), Color(0xFFD84315)),
        YomotsuTitle(870, "Criador de Universos", Color(0xFFFFF59D), Color(0xFFF57F17)),
        YomotsuTitle(880, "Caos Encarnado", Color(0xFF000000), Color(0xFFFF5252)),
        YomotsuTitle(890, "O Limite da Realidade", Color(0xFF18FFFF), Color(0xFF000000)),
        YomotsuTitle(900, "O Alfa e o Ômega", Color(0xFFFFFFFF), Color(0xFFFFD54F)),

        // Fase 10: O Ápice (Nível 910 ao 1000)
        YomotsuTitle(910, "Senhor do Multiverso", Color(0xFFE1BEE7), Color(0xFF4A148C)),
        YomotsuTitle(920, "Vontade Primordial", Color(0xFFFFCCBC), Color(0xFFBF360C)),
        YomotsuTitle(930, "Luz Infinita", Color(0xFFFFFFFF), Color(0xFFFFF176)),
        YomotsuTitle(940, "Escuridão Perfeita", Color(0xFF212121), Color(0xFF000000)),
        YomotsuTitle(950, "A Mão do Autor", Color(0xFF82B1FF), Color(0xFF2962FF)),
        YomotsuTitle(960, "Trono da Criação", Color(0xFFFFD180), Color(0xFFFF6D00)),
        YomotsuTitle(970, "O Arquiteto", Color(0xFFECEFF1), Color(0xFF263238)),
        YomotsuTitle(980, "Aquele Que Tudo Controla", Color(0xFFFF5252), Color(0xFFD50000)),
        YomotsuTitle(990, "Entidade Ômicron", Color(0xFF1A1A1A), Color(0xFF00E5FF)),
        YomotsuTitle(1000, "Deus Supremo do Yomotsu", Color(0xFF000000), Color(0xFFFFFFFF)) // Preto e Branco brabo
    )

    /**
     * Fórmula Curva Exponencial (Quadrática)
     * XP(Level) = 10 * (Level - 1)^2
     */
    fun calculateLevelFromXp(xp: Long): Int {
        var level = 1
        while (level < MAX_LEVEL) {
            val nextLevelXp = 10L * (level) * (level)
            if (xp < nextLevelXp) {
                break
            }
            level++
        }
        return level
    }

    fun getXpRequiredForLevel(level: Int): Long {
        if (level <= 1) return 0L
        if (level > MAX_LEVEL) return 10L * (MAX_LEVEL - 1) * (MAX_LEVEL - 1)
        return 10L * (level - 1) * (level - 1)
    }

    fun getUnlockedTitles(currentLevel: Int): List<YomotsuTitle> {
        return ALL_TITLES.filter { it.unlockLevel <= currentLevel }.reversed() // Mostra os mais picas primeiro
    }

    fun getCurrentTitleByLevel(level: Int): YomotsuTitle {
        return ALL_TITLES.lastOrNull { it.unlockLevel <= level } ?: ALL_TITLES.first()
    }
}
