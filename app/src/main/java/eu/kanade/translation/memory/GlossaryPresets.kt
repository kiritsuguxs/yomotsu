package eu.kanade.translation.memory

import eu.kanade.translation.translator.ComicTranslationContext

enum class GlossaryPresetType {
    MANHWA,
    MANGA,
}

object GlossaryPresets {

    val manhwaPresetEntries: List<TranslationMemoryEntry> = listOf(
        // Ranks e Categorias
        TranslationMemoryEntry("Rank-S", "Rank S", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("S-Rank", "Rank S", TranslationMemoryEntryType.TITLE, isProtected = true),
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

        // Dungeon, Portais e Incursões
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

        // Guildas e Organizações
        TranslationMemoryEntry("Guild", "Guilda", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Guilds", "Guildas", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Guild Master", "Mestre da Guilda", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Guildmaster", "Mestre da Guilda", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Vice Guild Master", "Vice-Mestre da Guilda", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Hunter Association", "Associação de Caçadores", TranslationMemoryEntryType.TERM, isProtected = false),

        // Monstros e Itens
        TranslationMemoryEntry("Magic Beast", "Fera Mágica", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Magic Beasts", "Feras Mágicas", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Monster Core", "Núcleo de Monstro", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Magic Core", "Núcleo Mágico", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Mana Crystal", "Cristal de Mana", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Boss", "Chefe", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Boss Monster", "Monstro Chefe", TranslationMemoryEntryType.TERM, isProtected = false),

        // Sistema, RPG e Status
        TranslationMemoryEntry("Status Window", "Janela de Status", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Level Up", "Subir de Nível", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Quest", "Missão", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Daily Quest", "Missão Diária", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Penalty Quest", "Missão de Penalidade", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Inventory", "Inventário", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Skill", "Habilidade", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Skills", "Habilidades", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Cooldown", "Tempo de Recarga", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Mana", "Mana", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Stat", "Atributo", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Stats", "Atributos", TranslationMemoryEntryType.TERM, isProtected = false),

        // Monarcas e Sombras
        TranslationMemoryEntry("Shadow Monarch", "Monarca das Sombras", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Shadow Army", "Exército das Sombras", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Shadow Soldier", "Soldado das Sombras", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Shadow Soldiers", "Soldados das Sombras", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Monarch", "Monarca", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Monarchs", "Monarcas", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Ruler", "Governante", TranslationMemoryEntryType.TITLE, isProtected = false),
        TranslationMemoryEntry("Rulers", "Governantes", TranslationMemoryEntryType.TITLE, isProtected = false),
    )

    val mangaPresetEntries: List<TranslationMemoryEntry> = listOf(
        // Honoríficos japoneses (Protegidos)
        TranslationMemoryEntry("-san", "-san", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("-kun", "-kun", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("-chan", "-chan", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("-sama", "-sama", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("-senpai", "-senpai", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("-kouhai", "-kouhai", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("-sensei", "-sensei", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("-dono", "-dono", TranslationMemoryEntryType.NAME, isProtected = true),
        TranslationMemoryEntry("Senpai", "Senpai", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Sensei", "Sensei", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Kouhai", "Kouhai", TranslationMemoryEntryType.TITLE, isProtected = true),

        // Termos clássicos e shonen
        TranslationMemoryEntry("Nakama", "Companheiro", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Shinobi", "Shinobi", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Chakra", "Chakra", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Jutsu", "Jutsu", TranslationMemoryEntryType.TECHNIQUE, isProtected = true),
        TranslationMemoryEntry("Genjutsu", "Genjutsu", TranslationMemoryEntryType.TECHNIQUE, isProtected = true),
        TranslationMemoryEntry("Taijutsu", "Taijutsu", TranslationMemoryEntryType.TECHNIQUE, isProtected = true),
        TranslationMemoryEntry("Ninjutsu", "Ninjutsu", TranslationMemoryEntryType.TECHNIQUE, isProtected = true),
        TranslationMemoryEntry("Dojo", "Dojô", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Katana", "Catana", TranslationMemoryEntryType.TERM, isProtected = false),
        TranslationMemoryEntry("Shuriken", "Shuriken", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Kunai", "Kunai", TranslationMemoryEntryType.TERM, isProtected = true),
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
        TranslationMemoryEntry("Kage", "Kage", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Hokage", "Hokage", TranslationMemoryEntryType.TITLE, isProtected = true),
        TranslationMemoryEntry("Oni", "Oni", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Yokai", "Youkai", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Youkai", "Youkai", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Ki", "Ki", TranslationMemoryEntryType.TERM, isProtected = true),
        TranslationMemoryEntry("Nen", "Nen", TranslationMemoryEntryType.TERM, isProtected = true),
    )

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
}
