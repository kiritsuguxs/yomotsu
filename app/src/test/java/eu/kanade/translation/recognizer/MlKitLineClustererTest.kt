package eu.kanade.translation.recognizer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MlKitLineClustererTest {

    @Test
    fun `centered multi-line balloon lines remain one block`() {
        val lines = listOf(
            line("You should stay", x = 100f, y = 100f, width = 140f, height = 18f),
            line("farther away from here.", x = 90f, y = 122f, width = 160f, height = 18f),
            line("Ok.", x = 152f, y = 144f, width = 36f, height = 18f),
        )

        val blocks = MlKitLineClusterer.clusterLines(lines)

        assertEquals(1, blocks.size)
        assertEquals("You should stay\nfarther away from here.\nOk.", blocks.single().text)
    }

    @Test
    fun `diagonal figure-8 double balloon lines split into two blocks`() {
        // Upper-right lobe ("I can't...") + lower-left lobe ("You say you / can't tell me.")
        val lines = listOf(
            line("I can't...", x = 250f, y = 100f, width = 85f, height = 18f),
            line("You say you", x = 70f, y = 132f, width = 135f, height = 18f),
            line("can't tell me.", x = 78f, y = 154f, width = 120f, height = 18f),
        )

        val blocks = MlKitLineClusterer.clusterLines(lines)

        assertEquals(2, blocks.size)
        assertEquals("I can't...", blocks[0].text)
        assertEquals("You say you\ncan't tell me.", blocks[1].text)
        assertTrue(blocks[0].x >= 240f)
        assertTrue(blocks[1].x + blocks[1].width <= 210f)
    }

    @Test
    fun `partially overlapping diagonal lobes ending with sentence split into two blocks`() {
        // Upper-left lobe ("I feel like Mom changed...") + lower-right lobe ("since my dad...")
        val lines = listOf(
            line("I feel like Mom", x = 70f, y = 100f, width = 130f, height = 18f),
            line("changed...", x = 90f, y = 122f, width = 95f, height = 18f),
            line("since my dad", x = 175f, y = 148f, width = 140f, height = 18f),
            line("passed away last year...", x = 170f, y = 170f, width = 150f, height = 18f),
        )

        val blocks = MlKitLineClusterer.clusterLines(lines)

        assertEquals(2, blocks.size)
        assertEquals("I feel like Mom\nchanged...", blocks[0].text)
        assertEquals("since my dad\npassed away last year...", blocks[1].text)
    }

    private fun line(
        text: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) = MlKitLineInput(
        text = text,
        x = x,
        y = y,
        width = width,
        height = height,
        symbolWidths = listOf(10f, 10f, 10f),
        symbolHeights = listOf(16f, 16f, 16f),
        angle = 0f,
    )
}
