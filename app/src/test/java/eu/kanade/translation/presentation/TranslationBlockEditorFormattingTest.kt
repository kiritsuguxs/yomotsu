package eu.kanade.translation.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TranslationBlockEditorFormattingTest {

    @Test
    fun `formatHorizontalPositionOffset shows left for negative and right for positive`() {
        assertEquals("-30% (←)", formatHorizontalPositionOffset(-30))
        assertEquals("0%", formatHorizontalPositionOffset(0))
        assertEquals("+40% (→)", formatHorizontalPositionOffset(40))
    }

    @Test
    fun `formatVerticalPositionOffset shows up for negative and down for positive`() {
        assertEquals("-20% (↑)", formatVerticalPositionOffset(-20))
        assertEquals("0%", formatVerticalPositionOffset(0))
        assertEquals("+50% (↓)", formatVerticalPositionOffset(50))
    }
}
