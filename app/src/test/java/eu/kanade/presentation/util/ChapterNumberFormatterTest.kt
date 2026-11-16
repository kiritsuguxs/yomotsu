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
}
