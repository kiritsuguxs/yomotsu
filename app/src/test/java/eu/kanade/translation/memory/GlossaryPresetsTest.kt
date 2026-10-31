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
}
