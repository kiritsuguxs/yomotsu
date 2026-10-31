package eu.kanade.translation.memory

import eu.kanade.translation.translator.ComicTranslationContext

enum class GlossaryPresetType {
    MANHWA,
    MANGA,
}

enum class GlossaryCategory(
    val presetType: GlossaryPresetType,
    val label: String,
    val isHonorifics: Boolean = false,
) {
    MANHWA_HONORIFICS(GlossaryPresetType.MANHWA, "Honoríficos Coreanos", isHonorifics = true),
    MANHWA_ROMANCE_NOBILITY(GlossaryPresetType.MANHWA, "Romance, Nobreza e Reencarnação"),
    MANHWA_MURIM(GlossaryPresetType.MANHWA, "Murim, Artes Marciais e Cultivo"),
    MANHWA_MODERN_SCHOOL(GlossaryPresetType.MANHWA, "Escolar, Gangues e Moderno"),
    MANHWA_HUNTER_SYSTEM(GlossaryPresetType.MANHWA, "Caçadores, Sistema e Dungeons"),

    MANGA_HONORIFICS(GlossaryPresetType.MANGA, "Honoríficos Japoneses", isHonorifics = true),
    MANGA_SCHOOL_ROMCOM(GlossaryPresetType.MANGA, "Escolar, RomCom e Esportes"),
    MANGA_ISEKAI_FANTASY(GlossaryPresetType.MANGA, "Isekai, Fantasia e RPG"),
    MANGA_CULTURE_DAILY(GlossaryPresetType.MANGA, "Cultura Japonesa e Cotidiano"),
    MANGA_SHONEN_SUPERNATURAL(GlossaryPresetType.MANGA, "Shonen, Samurai e Sobrenatural"),
}

data class SmartGlossaryRecommendation(
    val detectedType: GlossaryPresetType,
    val matchedCategories: Set<GlossaryCategory>,
    val entries: List<TranslationMemoryEntry>,
    val usedFallbackAll: Boolean,
)

object GlossaryPresets {

    private val manhwaHonorificsEntries: List<TranslationMemoryEntry> = listOf(
        // 1. Honoríficos, Parentesco e Tratamentos Coreanos
        TranslationMemoryEntry("Oppa", "Oppa", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Hyung", "Hyung", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Hyung-nim", "Hyung-nim", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Noona", "Noona", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Unnie", "Unnie", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Sunbae", "Sunbae", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Sunbae-nim", "Sunbae-nim", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Hoobae", "Hoobae", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("-nim", "-nim", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("-ssi", "-ssi", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("-ah", "-ah", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("-yah", "-yah", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("Ahjussi", "Ahjussi", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Ahjumma", "Ahjumma", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Orabeoni", "Irmão", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Maknae", "Caçula", TranslationMemoryEntryType.TERM, isProtected = false),
    )

    private val manhwaRomanceNobilityEntries: List<TranslationMemoryEntry> = listOf(
        // 2. Romance, Nobreza, Reencarnação e Otome Isekai (Fantasia Romântica)
        TranslationMemoryEntry("Villainess", "Vilã", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Transmigration", "Transmigração", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Regression", "Regressão", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Regressor", "Regressor", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Regressors", "Regressores", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Reincarnation", "Reencarnação", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Reincarnator", "Reencarnado", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Returner", "Retornado", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Returners", "Retornados", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Emperor", "Imperador", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Empress", "Imperatriz", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Empress Dowager", "Imperatriz Viúva", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Crown Prince", "Príncipe Herdeiro", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Crown Princess", "Princesa Herdeira", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Imperial Prince", "Príncipe Imperial", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Imperial Princess", "Princesa Imperial", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Imperial Family", "Família Imperial", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Imperial Palace", "Palácio Imperial", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Royal Family", "Família Real", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Grand Duke", "Grão-Duque", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Grand Duchess", "Grã-Duquesa", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Grand Duchy", "Grão-Ducado", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Archduke", "Arquiduque", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Archduchess", "Arquiduquesa", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Duke", "Duque", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Duchess", "Duquesa", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Duchy", "Ducado", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Marquis", "Marquês", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Marchioness", "Marquesa", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Marquess", "Marquesa", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Count", "Conde", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Countess", "Condessa", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Viscount", "Visconde", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Viscountess", "Viscondessa", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Baron", "Barão", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Baroness", "Baronesa", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Young Lady", "Jovem Dama", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("My Lady", "Minha Senhora", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Milady", "Milady", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Your Majesty", "Vossa Majestade", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("His Majesty", "Sua Majestade", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Her Majesty", "Sua Majestade", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Your Highness", "Vossa Alteza", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("His Highness", "Sua Alteza", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Her Highness", "Sua Alteza", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Your Grace", "Vossa Graça", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("His Grace", "Sua Graça", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Her Grace", "Sua Graça", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Saintess", "Santa", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("High Priest", "Sumo Sacerdote", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Holy Power", "Poder Sagrado", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Divine Power", "Poder Divino", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Holy Temple", "Templo Sagrado", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Holy Sword", "Espada Sagrada", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Magic Tower", "Torre Mágica", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Tower Master", "Mestre da Torre", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Archmage", "Arquimago", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Head Maid", "Criada-Chefe", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Butler", "Mordomo", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Lady-in-waiting", "Dama de Companhia", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Concubine", "Concubina", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Fiance", "Noivo", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Fiancee", "Noiva", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Debutante", "Debutante", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("High Society", "Alta Sociedade", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Tea Party", "Festa do Chá", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Banquet", "Banquete", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Contract Marriage", "Casamento por Contrato", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Arranged Marriage", "Casamento Arranjado", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Imperial Knights", "Cavaleiros Imperiais", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Holy Knights", "Cavaleiros Sagrados", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Knight Commander", "Comandante dos Cavaleiros", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Swordmaster", "Mestre da Espada", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Aura", "Aura", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Aura Blade", "Lâmina de Aura", TranslationMemoryEntryType.TECHNIQUE, isProtected = false),
    )

    private val manhwaMurimEntries: List<TranslationMemoryEntry> = listOf(
        // 3. Murim, Artes Marciais e Cultivo
        TranslationMemoryEntry("Murim", "Murim", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Jianghu", "Jianghu", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Central Plains", "Planícies Centrais", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Murim Alliance", "Aliança Murim", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Orthodox Sect", "Seita Ortodoxa", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Unorthodox Sect", "Seita Heterodoxa", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Righteous Faction", "Facção Justa", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Evil Faction", "Facção Maligna", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Demonic Cult", "Culto Demoníaco", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Heavenly Demon", "Demônio Celestial", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Blood Cult", "Culto de Sangue", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Mount Hua", "Monte Hua", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Mount Hua Sect", "Seita do Monte Hua", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Wudang", "Wudang", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Shaolin", "Shaolin", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Kunlun", "Kunlun", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Emei", "Emei", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Qingcheng", "Qingcheng", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Beggar's Sect", "Seita dos Mendigos", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Namgung Clan", "Clã Namgung", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Tang Clan", "Clã Tang", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Zhuge Clan", "Clã Zhuge", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Moyong Clan", "Clã Moyong", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Peng Clan", "Clã Peng", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Nine Great Sects", "Nove Grandes Seitas", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Five Great Families", "Cinco Grandes Famílias", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Ten Great Clans", "Dez Grandes Clãs", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Sect Leader", "Líder da Seita", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Patriarch", "Patriarca", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Matriarch", "Matriarca", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Supreme Elder", "Ancião Supremo", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Grand Elder", "Grão-Ancião", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Outer Disciple", "Discípulo Externo", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Inner Disciple", "Discípulo Interno", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Direct Disciple", "Discípulo Direto", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Senior Brother", "Irmão Sênior", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Junior Brother", "Irmão Júnior", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Senior Sister", "Irmã Sênior", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Junior Sister", "Irmã Júnior", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Young Master", "Jovem Mestre", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Pavilion Master", "Mestre do Pavilhão", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Qi", "Qi", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Ki", "Ki", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Internal Energy", "Energia Interna", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Inner Qi", "Qi Interno", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("True Qi", "Qi Verdadeiro", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Demonic Qi", "Qi Demoníaco", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Sword Qi", "Qi da Espada", TranslationMemoryEntryType.TECHNIQUE, isProtected = false),
        TranslationMemoryEntry("Sword Intent", "Intenção da Espada", TranslationMemoryEntryType.TECHNIQUE, isProtected = false),
        TranslationMemoryEntry("Dantian", "Dantian", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Meridians", "Meridianos", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Qi Deviation", "Desvio de Qi", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Heart Demon", "Demônio Interior", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Enlightenment", "Iluminação", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Breakthrough", "Avanço de Reino", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Cultivation", "Cultivo", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Martial Artist", "Artista Marcial", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Martial Arts", "Artes Marciais", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Peak Realm", "Reino do Ápice", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Transcendent Realm", "Reino Transcendente", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Elixir", "Elixir", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Lightness Skill", "Técnica de Leveza", TranslationMemoryEntryType.TECHNIQUE, isProtected = false),
        TranslationMemoryEntry("Footwork", "Jogo de Pés", TranslationMemoryEntryType.TECHNIQUE, isProtected = false),
    )

    private val manhwaModernSchoolEntries: List<TranslationMemoryEntry> = listOf(
        // 4. Escolar, Ação Urbana, Gangues, Moderno, Chaebol e Showbiz
        TranslationMemoryEntry("Chaebol", "Chaebol", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Chairman", "Presidente do Conselho", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("CEO", "CEO", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Team Leader", "Líder de Equipe", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Department Head", "Chefe de Departamento", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Managing Director", "Diretor Executivo", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Iljin", "Valentão", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Bully", "Valentão", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Bullies", "Valentões", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Delinquent", "Delinquente", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Delinquents", "Delinquentes", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Four Major Crews", "Quatro Grandes Gangues", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Runaway Fam", "Família de Fugitivos", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Transfer Student", "Aluno Transferido", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Student Council", "Conselho Estudantil", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Class President", "Representante de Classe", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Homeroom Teacher", "Professor Titular", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Cram School", "Cursinho", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Hagwon", "Cursinho", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("CSAT", "Vestibular Nacional", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("PC Bang", "Lan House", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Convenience Store", "Loja de Conveniência", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Soju", "Soju", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Tteokbokki", "Tteokbokki", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Ramyeon", "Ramyeon", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Kimchi", "Kimchi", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Hanbok", "Hanbok", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Won", "Won", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Trainee", "Trainee", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Idol", "Idol", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Rookie", "Novato", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Streamer", "Streamer", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Pro Gamer", "Jogador Profissional", TranslationMemoryEntryType.TERM, isProtected = false),
    )

    private val manhwaHunterSystemEntries: List<TranslationMemoryEntry> = listOf(
        // 5. Torre, Constelações, Apocalipse, Sistema, Caçadores e Dungeons
        TranslationMemoryEntry("Constellation", "Constelação", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Constellations", "Constelações", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Incarnation", "Encarnação", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Incarnations", "Encarnações", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Sponsor", "Patrocinador", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Stigma", "Estigma", TranslationMemoryEntryType.TECHNIQUE, isProtected = false),
        TranslationMemoryEntry("Scenario", "Cenário", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Main Scenario", "Cenário Principal", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Sub Scenario", "Subcenário", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Hidden Scenario", "Cenário Oculto", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Dokkaebi", "Dokkaebi", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("Star Stream", "Fluxo Estelar", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Hidden Piece", "Peça Oculta", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Ranker", "Ranker", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("High Ranker", "High Ranker", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Irregular", "Irregular", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("SSS-Rank", "Rank SSS", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Rank-SSS", "Rank SSS", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("SS-Rank", "Rank SS", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Rank-SS", "Rank SS", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Rank-S", "Rank S", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("S-Rank", "Rank S", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("S-Class", "Classe S", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Rank-A", "Rank A", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("A-Rank", "Rank A", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Rank-B", "Rank B", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("B-Rank", "Rank B", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Rank-C", "Rank C", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("C-Rank", "Rank C", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Rank-D", "Rank D", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("D-Rank", "Rank D", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Rank-E", "Rank E", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("E-Rank", "Rank E", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Rank-F", "Rank F", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("F-Rank", "Rank F", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("National Level Hunter", "Caçador de Nível Nacional", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Hunter", "Caçador", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Hunters", "Caçadores", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Gate", "Portal", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Gates", "Portais", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Red Gate", "Portal Vermelho", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Blue Gate", "Portal Azul", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Dungeon", "Masmorra", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Dungeons", "Masmorras", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Dungeon Break", "Ruptura de Masmorra", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Raid", "Incursão", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Raids", "Incursões", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Raider", "Invasor", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Raiders", "Invasores", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Awakening", "Despertar", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Awakened", "Desperto", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Awakened One", "Desperto", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Re-awakened", "Redesperto", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Reawakening", "Redespertar", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Guild", "Guilda", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Guilds", "Guildas", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Guild Master", "Mestre da Guilda", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Guildmaster", "Mestre da Guilda", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Vice Guild Master", "Vice-Mestre da Guilda", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Hunter Association", "Associação de Caçadores", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Magic Beast", "Fera Mágica", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Magic Beasts", "Feras Mágicas", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Monster Core", "Núcleo de Monstro", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Magic Core", "Núcleo Mágico", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Mana Crystal", "Cristal de Mana", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Mana Stone", "Pedra de Mana", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Artifact", "Artefato", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Artifacts", "Artefatos", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Potion", "Poção", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Boss", "Chefe", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Boss Monster", "Monstro Chefe", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Status Window", "Janela de Status", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Level Up", "Subir de Nível", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Quest", "Missão", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Daily Quest", "Missão Diária", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Penalty Quest", "Missão de Penalidade", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Hidden Quest", "Missão Oculta", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Inventory", "Inventário", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Skill", "Habilidade", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Skills", "Habilidades", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Passive Skill", "Habilidade Passiva", TranslationMemoryEntryType.TECHNIQUE, isProtected = false),
        TranslationMemoryEntry("Active Skill", "Habilidade Ativa", TranslationMemoryEntryType.TECHNIQUE, isProtected = false),
        TranslationMemoryEntry("Unique Skill", "Habilidade Única", TranslationMemoryEntryType.TECHNIQUE, isProtected = false),
        TranslationMemoryEntry("Ultimate Skill", "Habilidade Suprema", TranslationMemoryEntryType.TECHNIQUE, isProtected = false),
        TranslationMemoryEntry("Trait", "Traço", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Cooldown", "Tempo de Recarga", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Mana", "Mana", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Stamina", "Vigor", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("HP", "HP", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("MP", "MP", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Buff", "Buff", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Debuff", "Debuff", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Stat", "Atributo", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Stats", "Atributos", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Necromancer", "Necromante", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Tanker", "Tanque", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Healer", "Curandeiro", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Summoner", "Invocador", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Shadow Monarch", "Monarca das Sombras", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Shadow Army", "Exército das Sombras", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Shadow Soldier", "Soldado das Sombras", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Shadow Soldiers", "Soldados das Sombras", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Monarch", "Monarca", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Monarchs", "Monarcas", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Ruler", "Governante", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Rulers", "Governantes", TranslationMemoryEntryType.TITLE, isProtected = false),
    )

    private val mangaHonorificsEntries: List<TranslationMemoryEntry> = listOf(
        // 1. Honoríficos, Parentesco e Tratamentos Japoneses
        TranslationMemoryEntry("-san", "-san", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("-kun", "-kun", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("-chan", "-chan", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("-sama", "-sama", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("-senpai", "-senpai", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("-kouhai", "-kouhai", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("-sensei", "-sensei", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("-dono", "-dono", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("-tan", "-tan", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("-chin", "-chin", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("-shi", "-shi", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("Senpai", "Senpai", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Sensei", "Sensei", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Kouhai", "Kouhai", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Onii-chan", "Onii-chan", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Onii-san", "Onii-san", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Aniki", "Aniki", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Onee-chan", "Onee-chan", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Onee-san", "Onee-san", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Aneki", "Aneki", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Imouto", "Irmãzinha", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Otouto", "Irmãozinho", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Ojou-sama", "Ojou-sama", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Obocchama", "Jovem Mestre", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Shishou", "Mestre", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Danna", "Patrão", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Oyakata-sama", "Oyakata-sama", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Hime-sama", "Princesa", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Kami-sama", "Kami-sama", TranslationMemoryEntryType.TITLE, isProtected = true),
    )

    private val mangaSchoolRomcomEntries: List<TranslationMemoryEntry> = listOf(
        // 2. Escolar, Romance, Comédia Romântica (RomCom), Esportes e Slice of Life
        TranslationMemoryEntry("Student Council", "Conselho Estudantil", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Student Council President", "Presidente do Conselho Estudantil", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Vice President", "Vice-Presidente", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Disciplinary Committee", "Comitê Disciplinar", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Public Morals Committee", "Comitê de Moral Pública", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Class Representative", "Representante de Classe", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Homeroom Teacher", "Professor Titular", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Infirmary", "Enfermaria", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Rooftop", "Terraço", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Clubroom", "Sala do Clube", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Club President", "Presidente do Clube", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Culture Festival", "Festival Cultural", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("School Festival", "Festival Escolar", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Sports Festival", "Festival Esportivo", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Training Camp", "Acampamento de Treino", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Field Trip", "Excursão Escolar", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Cram School", "Cursinho", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Entrance Exam", "Exame de Admissão", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Shoe Locker", "Armário de Sapatos", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Uwabaki", "Uwabaki", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Seifuku", "Uniforme Escolar", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Gakuran", "Gakuran", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Childhood Friend", "Amigo de Infância", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Childhood Friends", "Amigos de Infância", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Confession", "Declaração", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Love Letter", "Carta de Amor", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Unrequited Love", "Amor Não Correspondido", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("First Love", "Primeiro Amor", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Soulmate", "Alma Gêmea", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Tsundere", "Tsundere", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Yandere", "Yandere", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Kuudere", "Kuudere", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Dandere", "Dandere", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Gyaru", "Gyaru", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Otaku", "Otaku", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Chuunibyou", "Chuunibyou", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Hikikomori", "Hikikomori", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("NEET", "NEET", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Mangaka", "Mangaká", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Idol", "Idol", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Nationals", "Torneio Nacional", TranslationMemoryEntryType.TERM, isProtected = false),
    )

    private val mangaIsekaiFantasyEntries: List<TranslationMemoryEntry> = listOf(
        // 3. Isekai, Fantasia Medieval, Aventura e RPG Japonês
        TranslationMemoryEntry("Isekai", "Outro Mundo", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Otherworlder", "Habitante de Outro Mundo", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Reincarnation", "Reencarnação", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Reincarnated", "Reencarnado", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Summoned Hero", "Herói Invocado", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Hero Party", "Grupo do Herói", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Demon Lord", "Rei Demônio", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Demon King", "Rei Demônio", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Maou", "Maou", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Maou-sama", "Maou-sama", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Four Heavenly Kings", "Quatro Reis Celestiais", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Demon Army", "Exército Demoníaco", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Mazoku", "Clã dos Demônios", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Adventurer", "Aventureiro", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Adventurers", "Aventureiros", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Adventurer's Guild", "Guilda de Aventureiros", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Adventurers Guild", "Guilda de Aventureiros", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Guild Master", "Mestre da Guilda", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Guild Receptionist", "Recepcionista da Guilda", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Guild Card", "Cartão da Guilda", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Quest", "Missão", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Quest Board", "Quadro de Missões", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Subjugation", "Subjugação", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Subjugation Quest", "Missão de Subjugação", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Escort Quest", "Missão de Escolta", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Labyrinth", "Labirinto", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Dungeon", "Masmorra", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Dungeon Master", "Mestre da Masmorra", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Magic Stone", "Pedra Mágica", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Mana", "Mana", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Item Box", "Caixa de Itens", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Inventory", "Inventário", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Appraisal", "Avaliação", TranslationMemoryEntryType.TECHNIQUE, isProtected = false),
        TranslationMemoryEntry("Status", "Status", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Skill", "Habilidade", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Unique Skill", "Habilidade Única", TranslationMemoryEntryType.TECHNIQUE, isProtected = false),
        TranslationMemoryEntry("Ultimate Skill", "Habilidade Suprema", TranslationMemoryEntryType.TECHNIQUE, isProtected = false),
        TranslationMemoryEntry("Blessing", "Bênção", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Divine Protection", "Proteção Divina", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Familiar", "Familiar", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Tamer", "Domador", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Beastman", "Homem-Fera", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Beastkin", "Povo-Fera", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Demi-human", "Demi-humano", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("High Elf", "Alto Elfo", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Dwarf", "Anão", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Slime", "Slime", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Goblin", "Goblin", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Orc", "Orc", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Wyvern", "Wyvern", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Mithril", "Mithril", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Orichalcum", "Oricalco", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Grimoire", "Grimório", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Spell", "Feitiço", TranslationMemoryEntryType.TECHNIQUE, isProtected = false),
        TranslationMemoryEntry("Magic Circle", "Círculo Mágico", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Barrier", "Barreira", TranslationMemoryEntryType.TECHNIQUE, isProtected = false),
        TranslationMemoryEntry("Holy Sword", "Espada Sagrada", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Cursed Sword", "Espada Amaldiçoada", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Saintess", "Santa", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Overpowered", "Apelão", TranslationMemoryEntryType.TERM, isProtected = false),
    )

    private val mangaCultureDailyEntries: List<TranslationMemoryEntry> = listOf(
        // 4. Cultura Japonesa, Cotidiano, Gastronomia, Festivais e Tradição
        TranslationMemoryEntry("Yukata", "Yukata", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Kimono", "Quimono", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Haori", "Haori", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Hakama", "Hakama", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Kotatsu", "Kotatsu", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Futon", "Futon", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Tatami", "Tatame", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Onsen", "Onsen", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Hot Spring", "Fonte Termal", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Sentou", "Casa de Banho", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Ryokan", "Ryokan", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Izakaya", "Izakaya", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Konbini", "Loja de Conveniência", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Matsuri", "Festival", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Shrine", "Santuário", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Torii", "Torii", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Miko", "Sacerdotisa", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Shrine Maiden", "Sacerdotisa", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Omamori", "Amuleto", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Omikuji", "Omikuji", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Hanabi", "Fogos de Artifício", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Hanami", "Hanami", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Golden Week", "Golden Week", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("White Day", "White Day", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Yen", "Iene", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Bento", "Bentô", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Obento", "Bentô", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Onigiri", "Onigiri", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Ramen", "Lámen", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Udon", "Udon", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Soba", "Soba", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Takoyaki", "Takoyaki", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Okonomiyaki", "Okonomiyaki", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Taiyaki", "Taiyaki", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Dango", "Dango", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Mochi", "Mochi", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Matcha", "Matcha", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Sake", "Saquê", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Karaoke", "Caraoquê", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Pachinko", "Pachinko", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Yakuza", "Yakuza", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Bosozoku", "Gangue de Motoqueiros", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Yankee", "Delinquente", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Delinquent", "Delinquente", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Salaryman", "Assalariado", TranslationMemoryEntryType.TERM, isProtected = false),
    )

    private val mangaShonenSupernaturalEntries: List<TranslationMemoryEntry> = listOf(
        // 5. Shonen, Seinen, Samurai, Sobrenatural, Exorcismo e Artes Marciais
        TranslationMemoryEntry("Nakama", "Companheiro", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Shinobi", "Shinobi", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Kunoichi", "Kunoichi", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Chakra", "Chakra", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Jutsu", "Jutsu", TranslationMemoryEntryType.TECHNIQUE, isProtected = true),
        TranslationMemoryEntry("Genjutsu", "Genjutsu", TranslationMemoryEntryType.TECHNIQUE, isProtected = true),
        TranslationMemoryEntry("Taijutsu", "Taijutsu", TranslationMemoryEntryType.TECHNIQUE, isProtected = true),
        TranslationMemoryEntry("Ninjutsu", "Ninjutsu", TranslationMemoryEntryType.TECHNIQUE, isProtected = true),
        TranslationMemoryEntry("Dojo", "Dojô", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Katana", "Catana", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Wakizashi", "Wakizashi", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Naginata", "Naginata", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Shuriken", "Shuriken", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Kunai", "Kunai", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Samurai", "Samurai", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Ronin", "Ronin", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Shogun", "Xogum", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Daimyo", "Daimiô", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Bushido", "Bushido", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Seppuku", "Seppuku", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Kenjutsu", "Kenjutsu", TranslationMemoryEntryType.TECHNIQUE, isProtected = true),
        TranslationMemoryEntry("Kendo", "Kendô", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Iaido", "Iaido", TranslationMemoryEntryType.TECHNIQUE, isProtected = true),
        TranslationMemoryEntry("Bankai", "Bankai", TranslationMemoryEntryType.TECHNIQUE, isProtected = true),
        TranslationMemoryEntry("Shikai", "Shikai", TranslationMemoryEntryType.TECHNIQUE, isProtected = true),
        TranslationMemoryEntry("Zanpakuto", "Zanpakuto", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Reiatsu", "Reiatsu", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Hollow", "Hollow", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Quincy", "Quincy", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Shinigami", "Shinigami", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Haki", "Haki", TranslationMemoryEntryType.TECHNIQUE, isProtected = true),
        TranslationMemoryEntry("Akuma no Mi", "Akuma no Mi", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Devil Fruit", "Akuma no Mi", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Bounty", "Recompensa", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Yonko", "Yonkou", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Kage", "Kage", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Hokage", "Hokage", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Oni", "Oni", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Yokai", "Youkai", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Youkai", "Youkai", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Ayakashi", "Ayakashi", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Kitsune", "Kitsune", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Tengu", "Tengu", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Kappa", "Kappa", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Yurei", "Espírito", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Evil Spirit", "Espírito Maligno", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Onmyoji", "Onmyoji", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Exorcist", "Exorcista", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Ofuda", "Talismã", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Talisman", "Talismã", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Shikigami", "Shikigami", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Cursed Energy", "Energia Amaldiçoada", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Cursed Technique", "Técnica Amaldiçoada", TranslationMemoryEntryType.TECHNIQUE, isProtected = false),
        TranslationMemoryEntry("Domain Expansion", "Expansão de Domínio", TranslationMemoryEntryType.TECHNIQUE, isProtected = false),
        TranslationMemoryEntry("Ki", "Ki", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Nen", "Nen", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Bloodlust", "Sede de Sangue", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Killing Intent", "Intenção Assassina", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Miasma", "Miasma", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Tournament", "Torneio", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Prodigy", "Prodígio", TranslationMemoryEntryType.TERM, isProtected = false),
    )

    val manhwaPresetEntries: List<TranslationMemoryEntry> =
        manhwaHonorificsEntries +
            manhwaRomanceNobilityEntries +
            manhwaMurimEntries +
            manhwaModernSchoolEntries +
            manhwaHunterSystemEntries

    val mangaPresetEntries: List<TranslationMemoryEntry> =
        mangaHonorificsEntries +
            mangaSchoolRomcomEntries +
            mangaIsekaiFantasyEntries +
            mangaCultureDailyEntries +
            mangaShonenSupernaturalEntries

    fun getCategories(type: GlossaryPresetType): List<GlossaryCategory> =
        GlossaryCategory.entries.filter { it.presetType == type }

    fun getEntriesForCategory(category: GlossaryCategory): List<TranslationMemoryEntry> = when (category) {
        GlossaryCategory.MANHWA_HONORIFICS -> manhwaHonorificsEntries
        GlossaryCategory.MANHWA_ROMANCE_NOBILITY -> manhwaRomanceNobilityEntries
        GlossaryCategory.MANHWA_MURIM -> manhwaMurimEntries
        GlossaryCategory.MANHWA_MODERN_SCHOOL -> manhwaModernSchoolEntries
        GlossaryCategory.MANHWA_HUNTER_SYSTEM -> manhwaHunterSystemEntries
        GlossaryCategory.MANGA_HONORIFICS -> mangaHonorificsEntries
        GlossaryCategory.MANGA_SCHOOL_ROMCOM -> mangaSchoolRomcomEntries
        GlossaryCategory.MANGA_ISEKAI_FANTASY -> mangaIsekaiFantasyEntries
        GlossaryCategory.MANGA_CULTURE_DAILY -> mangaCultureDailyEntries
        GlossaryCategory.MANGA_SHONEN_SUPERNATURAL -> mangaShonenSupernaturalEntries
    }

    fun getEntriesForCategories(categories: Collection<GlossaryCategory>): List<TranslationMemoryEntry> {
        val seen = HashSet<String>()
        val result = ArrayList<TranslationMemoryEntry>()
        GlossaryCategory.entries
            .filter { it in categories }
            .forEach { category ->
                getEntriesForCategory(category).forEach { entry ->
                    if (seen.add(entry.source.lowercase())) {
                        result += entry
                    }
                }
            }
        return result
    }

    fun getEntries(type: GlossaryPresetType): List<TranslationMemoryEntry> = when (type) {
        GlossaryPresetType.MANHWA -> manhwaPresetEntries
        GlossaryPresetType.MANGA -> mangaPresetEntries
    }

    fun applyPreset(
        context: ComicTranslationContext,
        preset: GlossaryPresetType,
    ): Int {
        val entries = getEntries(preset)
        TranslationMemory.replaceEntries(context, entries, merge = true)
        return entries.size
    }

    fun applyCategories(
        context: ComicTranslationContext,
        categories: Collection<GlossaryCategory>,
    ): Int {
        val entries = getEntriesForCategories(categories)
        if (entries.isEmpty()) return 0
        TranslationMemory.replaceEntries(context, entries, merge = true)
        return entries.size
    }

    fun recommendForWork(
        title: String,
        genresText: String = "",
        description: String = "",
        forcedType: GlossaryPresetType? = null,
    ): SmartGlossaryRecommendation {
        val haystack = "$title \n $genresText \n $description".lowercase()
        val detectedType = forcedType ?: detectPresetType(haystack)
        val categoriesForType = getCategories(detectedType)
        val honorificCategory = categoriesForType.first { it.isHonorifics }

        val matchedSubgenres = categoriesForType
            .filterNot { it.isHonorifics }
            .filter { category -> matchesCategoryKeywords(haystack, category) }

        val usedFallbackAll = matchedSubgenres.isEmpty()
        val finalCategories = LinkedHashSet<GlossaryCategory>().apply {
            add(honorificCategory)
            if (usedFallbackAll) {
                addAll(categoriesForType)
            } else {
                addAll(matchedSubgenres)
            }
        }

        return SmartGlossaryRecommendation(
            detectedType = detectedType,
            matchedCategories = finalCategories,
            entries = getEntriesForCategories(finalCategories),
            usedFallbackAll = usedFallbackAll,
        )
    }

    private fun detectPresetType(haystack: String): GlossaryPresetType {
        val manhwaScore = MANHWA_ORIGIN_KEYWORDS.count { containsKeyword(haystack, it) }
        val mangaScore = MANGA_ORIGIN_KEYWORDS.count { containsKeyword(haystack, it) }
        return if (mangaScore > manhwaScore) GlossaryPresetType.MANGA else GlossaryPresetType.MANHWA
    }

    private fun matchesCategoryKeywords(haystack: String, category: GlossaryCategory): Boolean {
        val keywords = CATEGORY_KEYWORDS[category].orEmpty()
        return keywords.any { containsKeyword(haystack, it) }
    }

    private fun containsKeyword(haystack: String, keyword: String): Boolean {
        if (keyword.length <= 3) {
            return Regex("(?<![\\p{L}\\p{N}])${Regex.escape(keyword)}(?![\\p{L}\\p{N}])").containsMatchIn(haystack)
        }
        return haystack.contains(keyword)
    }

    private val MANHWA_ORIGIN_KEYWORDS = listOf(
        "manhwa", "webtoon", "korean", "coreano", "coreana", "coreia", "korea", "seoul", "seul",
        "murim", "jianghu", "wuxia", "xianxia", "manhua", "cultivation", "cultivo", "chaebol",
        "villainess", "vilã", "hunter", "caçador", "leveling", "dokkaebi", "constellation",
        "oppa", "hyung", "sunbae", "manhwa", "long strip", "full color",
    )

    private val MANGA_ORIGIN_KEYWORDS = listOf(
        "manga", "mangá", "japanese", "japonês", "japonesa", "japão", "japan", "tokyo", "tóquio",
        "shounen", "shonen", "seinen", "shoujo", "josei", "maou", "shinobi", "ninja", "samurai",
        "shinigami", "yakuza", "yukata", "onsen", "senpai", "kouhai", "gyaru", "tsundere", "mangaka",
    )

    private val CATEGORY_KEYWORDS: Map<GlossaryCategory, List<String>> = mapOf(
        GlossaryCategory.MANHWA_ROMANCE_NOBILITY to listOf(
            "romance", "romântico", "villainess", "vilã", "duke", "duque", "duchess", "duquesa",
            "emperor", "imperador", "empress", "imperatriz", "princess", "princesa", "prince", "príncipe",
            "noble", "nobre", "nobreza", "aristocra", "lady", "dama", "marriage", "casamento",
            "fiance", "fiancé", "noivo", "noiva", "reincarnat", "reencarna", "transmigra",
            "otome", "josei", "shoujo", "palace", "palácio", "royal", "realeza", "imperial",
            "saintess", "santa", "tyrant", "tirano", "stepmother", "madrasta", "heiress", "herdeira",
            "magic tower", "torre mágica", "archmage", "swordmaster", "aura",
        ),
        GlossaryCategory.MANHWA_MURIM to listOf(
            "murim", "martial art", "artes marciais", "wuxia", "xianxia", "cultivat", "cultivo",
            "sect", "seita", "mount hua", "monte hua", "heavenly demon", "demônio celestial",
            "demonic cult", "culto demoníaco", "jianghu", "swordsman", "espadachim", "sword", "espada",
            "fist", "punho", "qi", "dantian", "meridian", "shaolin", "wudang", "namgung",
            "tang clan", "zhuge", "patriarch", "patriarca", "disciple", "discípulo", "young master",
            "jovem mestre", "kangho", "northern blade", "nano machine",
        ),
        GlossaryCategory.MANHWA_MODERN_SCHOOL to listOf(
            "school", "escolar", "vida escolar", "high school", "colégio", "slice of life", "cotidiano",
            "drama", "delinquent", "gang", "gangue", "bully", "bullying", "lookism", "crew",
            "chaebol", "ceo", "office", "escritório", "idol", "showbiz", "actor", "atriz", "ator",
            "streamer", "gamer", "esports", "music", "música", "modern", "moderno", "sports", "esporte",
            "mercenary", "mercenário", "university", "faculdade",
        ),
        GlossaryCategory.MANHWA_HUNTER_SYSTEM to listOf(
            "hunter", "caçador", "dungeon", "masmorra", "gate", "portal", "tower", "torre",
            "system", "sistema", "level", "leveling", "nível", "rank", "ranker", "player", "jogador",
            "necromancer", "necromante", "constellation", "constelação", "scenario", "cenário",
            "monster", "monstro", "awakened", "awakening", "desperto", "despertar", "guild", "guilda",
            "raid", "incursão", "apocalypse", "apocalipse", "omniscient", "regression", "regressor",
            "regressão", "returner", "sss", "solo", "rpg", "status window", "monarch", "monarca",
        ),
        GlossaryCategory.MANGA_SCHOOL_ROMCOM to listOf(
            "school", "escolar", "vida escolar", "high school", "colégio", "romance", "romcom",
            "comedy", "comédia", "slice of life", "cotidiano", "sports", "esporte", "club", "clube",
            "gyaru", "tsundere", "yandere", "shoujo", "music", "band", "idol", "love", "amor",
            "childhood friend", "amiga de infância", "amigo de infância", "festival",
        ),
        GlossaryCategory.MANGA_ISEKAI_FANTASY to listOf(
            "isekai", "otherworld", "outro mundo", "fantasy", "fantasia", "magic", "magia",
            "demon lord", "demon king", "rei demônio", "maou", "hero", "herói", "adventurer",
            "aventureiro", "guild", "guilda", "dragon", "dragão", "elf", "elfo", "dungeon",
            "masmorra", "labyrinth", "labirinto", "reincarnat", "reencarna", "summon", "invocado",
            "rpg", "skill", "habilidade", "slime", "goblin", "grimoire", "grimório", "medieval",
        ),
        GlossaryCategory.MANGA_CULTURE_DAILY to listOf(
            "historical", "histórico", "culture", "cultura", "gourmet", "cooking", "culinária",
            "food", "comida", "gastronomia", "japan", "japão", "shrine", "santuário", "miko",
            "kimono", "onsen", "ryokan", "yakuza", "crime", "mystery", "mistério", "drama",
            "slice of life", "cotidiano", "salaryman",
        ),
        GlossaryCategory.MANGA_SHONEN_SUPERNATURAL to listOf(
            "action", "ação", "shounen", "shonen", "seinen", "supernatural", "sobrenatural",
            "demon", "demônio", "exorcist", "exorcista", "curse", "maldição", "jujutsu",
            "shinobi", "ninja", "samurai", "ronin", "shogun", "pirate", "pirata", "shinigami",
            "yokai", "youkai", "spirit", "espírito", "martial", "marciais", "battle", "batalha",
            "horror", "terror", "dark", "vampire", "vampiro", "tournament", "torneio",
        ),
    )
}
