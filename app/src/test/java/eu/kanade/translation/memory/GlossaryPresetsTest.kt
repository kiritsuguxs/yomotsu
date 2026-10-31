package eu.kanade.translation.memory

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GlossaryPresetsTest {

    @Test
    fun `manhwa presets contain essential dungeon and hunter terms`() {
        val entries = GlossaryPresets.getEntries(GlossaryPresetType.MANHWA)
        assertTrue(entries.isNotEmpty())
        assertTrue(entries.size >= 50)

        val sources = entries.map { it.source }
        assertTrue(sources.contains("Hunter"))
        assertTrue(sources.contains("Gate"))
        assertTrue(sources.contains("Dungeon"))
        assertTrue(sources.contains("Rank-S"))
        assertTrue(sources.contains("Shadow Monarch"))
    }

    @Test
    fun `manga presets contain essential honorifics and shonen terms`() {
        val entries = GlossaryPresets.getEntries(GlossaryPresetType.MANGA)
        assertTrue(entries.isNotEmpty())
        assertTrue(entries.size >= 35)

        val sources = entries.map { it.source }
        assertTrue(sources.contains("-san"))
        assertTrue(sources.contains("-kun"))
        assertTrue(sources.contains("Sensei"))
        assertTrue(sources.contains("Nakama"))
        assertTrue(sources.contains("Bankai"))
    }

    @Test
    fun `all presets have non-empty source and target`() {
        for (preset in GlossaryPresetType.entries) {
            val entries = GlossaryPresets.getEntries(preset)
            for (entry in entries) {
                assertFalse(entry.source.isBlank())
                assertFalse(entry.target.isBlank())
            }
        }
    }
}
