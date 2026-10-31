package eu.kanade.translation.recognizer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PaddleWebtoonTilerTest {

    @Test
    fun `short manga page plans a single untiled window`() {
        val windows = PaddleWebtoonTiler.planTiles(800, 1200)
        assertEquals(1, windows.size)
        assertEquals(0, windows.first().top)
        assertEquals(1200, windows.first().bottom)
    }

    @Test
    fun `tall manhwa strip is sliced into overlapping tiles without blind spots`() {
        val width = 720
        val height = 7500
        val windows = PaddleWebtoonTiler.planTiles(width, height)

        assertTrue(windows.size >= 4, "A 7500px tall strip must be sliced, got ${windows.size}")
        assertEquals(0, windows.first().top)
        assertEquals(height, windows.last().bottom)

        for (i in 0 until windows.size - 1) {
            val curr = windows[i]
            val next = windows[i + 1]
            assertTrue(next.top < curr.bottom, "Adjacent tiles must overlap: $curr vs $next")
            val overlap = curr.bottom - next.top
            assertTrue(overlap in 100..400, "Overlap must be sane: $overlap px")
        }
    }

    @Test
    fun `deduplicateBlocks keeps the higher quality recognition across seam overlap`() {
        val blockA = OcrTextBlock(
            text = "FIGHTING ARTS",
            x = 100f,
            y = 1500f,
            width = 200f,
            height = 30f,
            symbolWidth = 15f,
            symbolHeight = 25f,
            angle = 0f,
            confidence = 0.95f,
        )
        // Same line detected on the overlapping seam of next tile with slightly lower confidence
        val blockB = OcrTextBlock(
            text = "FIGHTING ARTS",
            x = 102f,
            y = 1502f,
            width = 198f,
            height = 29f,
            symbolWidth = 15f,
            symbolHeight = 25f,
            angle = 0f,
            confidence = 0.82f,
        )
        val deduplicated = PaddleWebtoonTiler.deduplicateBlocks(listOf(blockA, blockB))
        assertEquals(1, deduplicated.size)
        assertEquals(0.95f, deduplicated.single().confidence)
    }

    @Test
    fun `isClippedAtTileSeam flags boxes touching internal seam edges when covered by neighbor tile`() {
        val prevTile = PaddleWebtoonTiler.TileWindow(0, 1800)
        val currTile = PaddleWebtoonTiler.TileWindow(1650, 3450)
        val nextTile = PaddleWebtoonTiler.TileWindow(3300, 5000)

        // Box touching top edge of currTile (local Y in 0..10 -> page Y in 1650..1660, fully inside prevTile bottom=1800)
        val seamPoints = listOf(
            OcrPoint(50f, 2f),
            OcrPoint(150f, 2f),
            OcrPoint(150f, 25f),
            OcrPoint(50f, 25f),
        )
        assertTrue(PaddleWebtoonTiler.isClippedAtTileSeam(seamPoints, currTile, prevTile, nextTile))

        // Box safely inside the center of currTile
        val centerPoints = listOf(
            OcrPoint(50f, 500f),
            OcrPoint(150f, 500f),
            OcrPoint(150f, 530f),
            OcrPoint(50f, 530f),
        )
        assertFalse(PaddleWebtoonTiler.isClippedAtTileSeam(centerPoints, currTile, prevTile, nextTile))
    }
}
