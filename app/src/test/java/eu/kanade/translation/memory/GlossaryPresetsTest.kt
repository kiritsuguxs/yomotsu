package eu.kanade.translation.memory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GlossaryPresetsTest {

    @Test
    fun `manhwa presets cover diverse genres beyond hunters`() {
        val entries = GlossaryPresets.getEntries(GlossaryPresetType.MANHWA)
        assertTrue(entries.size >= 250)

        val sources = entries.map { it.source }.toSet()
        // Honoríficos coreanos
        assertTrue(sources.contains("Oppa"))
        assertTrue(sources.contains("Hyung"))
        assertTrue(sources.contains("-nim"))
        // Romance / Otome Isekai / Nobreza
        assertTrue(sources.contains("Villainess"))
        assertTrue(sources.contains("Crown Prince"))
        assertTrue(sources.contains("Grand Duke"))
        assertTrue(sources.contains("Young Lady"))
        // Murim / Artes Marciais
        assertTrue(sources.contains("Murim"))
        assertTrue(sources.contains("Mount Hua Sect"))
        assertTrue(sources.contains("Heavenly Demon"))
        assertTrue(sources.contains("Dantian"))
        // Escolar / Urbano / Chaebol
        assertTrue(sources.contains("Chaebol"))
        assertTrue(sources.contains("Iljin"))
        assertTrue(sources.contains("Student Council"))
        // Torre / Constelações / Caçadores
        assertTrue(sources.contains("Constellation"))
        assertTrue(sources.contains("Hunter"))
        assertTrue(sources.contains("Dungeon"))
        assertTrue(sources.contains("Rank-S"))
        assertTrue(sources.contains("Shadow Monarch"))
    }

    @Test
    fun `manga presets cover honorifics, school, isekai, culture and shonen`() {
        val entries = GlossaryPresets.getEntries(GlossaryPresetType.MANGA)
        assertTrue(entries.size >= 200)

        val sources = entries.map { it.source }.toSet()
        // Honoríficos
        assertTrue(sources.contains("-san"))
        assertTrue(sources.contains("-kun"))
        assertTrue(sources.contains("Sensei"))
        assertTrue(sources.contains("Ojou-sama"))
        // Escolar / RomCom / Slice of Life
        assertTrue(sources.contains("Student Council President"))
        assertTrue(sources.contains("Childhood Friend"))
        assertTrue(sources.contains("Tsundere"))
        // Isekai / Fantasia
        assertTrue(sources.contains("Demon Lord"))
        assertTrue(sources.contains("Adventurer's Guild"))
        assertTrue(sources.contains("Grimoire"))
        // Cultura / Cotidiano
        assertTrue(sources.contains("Yukata"))
        assertTrue(sources.contains("Onsen"))
        assertTrue(sources.contains("Bento"))
        // Shonen / Samurai / Sobrenatural
        assertTrue(sources.contains("Nakama"))
        assertTrue(sources.contains("Samurai"))
        assertTrue(sources.contains("Domain Expansion"))
        assertTrue(sources.contains("Bankai"))
    }

    @Test
    fun `all presets have unique case-insensitive sources and non-empty targets`() {
        for (preset in GlossaryPresetType.entries) {
            val entries = GlossaryPresets.getEntries(preset)
            val normalizedSources = entries.map { it.source.trim().lowercase() }
            assertEquals(entries.size, normalizedSources.toSet().size)
            for (entry in entries) {
                assertFalse(entry.source.isBlank())
                assertFalse(entry.target.isBlank())
            }
        }
    }

    @Test
    fun `smart recommendation selects only matching categories for murim manhwa`() {
        val rec = GlossaryPresets.recommendForWork(
            title = "Return of the Mount Hua Sect",
            genresText = "Action, Martial Arts, Webtoon",
            description = "Chung Myung of the Mount Hua Sect wakes up 100 years later in Murim.",
        )
        assertEquals(GlossaryPresetType.MANHWA, rec.detectedType)
        assertFalse(rec.usedFallbackAll)
        assertTrue(GlossaryCategory.MANHWA_HONORIFICS in rec.matchedCategories)
        assertTrue(GlossaryCategory.MANHWA_MURIM in rec.matchedCategories)
        assertFalse(GlossaryCategory.MANHWA_ROMANCE_NOBILITY in rec.matchedCategories)
        assertFalse(GlossaryCategory.MANHWA_HUNTER_SYSTEM in rec.matchedCategories)
    }

    @Test
    fun `smart recommendation selects romance nobility for villainess work`() {
        val rec = GlossaryPresets.recommendForWork(
            title = "Villainess Is Destined to Die",
            genresText = "Romance, Josei, Manhwa",
            description = "Reincarnated as the Duke's hated daughter and pursued by the Crown Prince.",
        )
        assertEquals(GlossaryPresetType.MANHWA, rec.detectedType)
        assertFalse(rec.usedFallbackAll)
        assertTrue(GlossaryCategory.MANHWA_HONORIFICS in rec.matchedCategories)
        assertTrue(GlossaryCategory.MANHWA_ROMANCE_NOBILITY in rec.matchedCategories)
        assertFalse(GlossaryCategory.MANHWA_MURIM in rec.matchedCategories)
    }

    @Test
    fun `ai glossary parser handles fenced json and truncated objects`() {
        val raw = """
            ```json
            [
              {"source": "Chung Myung", "target": "Chung Myung", "type": "NAME", "protected": true},
              {"source": "Plum Blossom Sword", "target": "Espada da Flor de Ameixeira", "type": "TECHNIQUE", "protected": false}
            ```
        """.trimIndent()
        val parsed = AiGlossaryGenerator.parseAiGlossaryEntries(raw)
        assertEquals(2, parsed.size)
        assertEquals("Chung Myung", parsed[0].source)
        assertEquals(TranslationMemoryEntryType.NAME, parsed[0].type)
        assertTrue(parsed[0].isProtected)
        assertEquals("Espada da Flor de Ameixeira", parsed[1].target)
        assertEquals(TranslationMemoryEntryType.TECHNIQUE, parsed[1].type)
    }
}
