package eu.kanade.presentation.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChapterNumberFormatterTest {

    @Test
    fun `decodes season and episode numbers encoded as season plus thousandths`() {
        assertEquals("7", formatEpisodeNumber(1.007))
        assertEquals("18 (S2)", formatEpisodeNumber(2.018))
        assertEquals("7", formatEpisodeNumber(1.007f.toDouble(), "S01E07"))
        assertEquals("18 (S2)", formatEpisodeNumber(2.018f.toDouble(), "Mission: Yozakura Family 2x18"))
        assertEquals("100", formatEpisodeNumber(1.1, "Season 1 Episode 100"))
    }

    @Test
    fun `preserves normal integer and real fractional episode numbers`() {
        assertEquals("1", formatEpisodeNumber(1.0))
        assertEquals("12", formatEpisodeNumber(12.0))
        assertEquals("12.5", formatEpisodeNumber(12.5, "Recap 12.5"))
        assertEquals("1.5", formatEpisodeNumber(1.5))
        assertEquals("1122", formatEpisodeNumber(1122.0))
    }

    @Test
    fun `keeps standard manga chapter decimal formatting unchanged`() {
        assertEquals("2.018", formatChapterNumber(2.018))
        assertEquals("12.5", formatChapterNumber(12.5))
        assertEquals("100", formatChapterNumber(100.0))
    }

    @Test
    fun `decodeEpisodeNumber returns numeric episode for trackers and AniSkip`() {
        assertEquals(7.0, decodeEpisodeNumber(1.007))
        assertEquals(18.0, decodeEpisodeNumber(2.018, "S02E18"))
        assertEquals(100.0, decodeEpisodeNumber(1.1, "Season 1 Episode 100"))
        assertEquals(12.5, decodeEpisodeNumber(12.5, "Recap 12.5"))
        assertEquals(12.0, decodeEpisodeNumber(12.0))
    }

    @Test
    fun `normalizeMergedEpisodeNumber aligns season 1 with flat numbers without cross-season collisions`() {
        assertEquals(7.0, normalizeMergedEpisodeNumber(1.007, "S01E07"))
        assertEquals(7.0, normalizeMergedEpisodeNumber(7.0, "Episode 7"))
        assertEquals(20007.0, normalizeMergedEpisodeNumber(2.007, "S02E07"))
        assertEquals(0.001, normalizeMergedEpisodeNumber(0.001, "OVA 1"), 1e-6)
    }
}
