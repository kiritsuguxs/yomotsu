package eu.kanade.translation.translator

import eu.kanade.translation.model.TranslationRegion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VisionBubbleMontageTest {

    @Test
    fun `computeBubbleCropRect expands with context padding and clamps to page edges`() {
        val rect = VisionBubbleMontagePlanner.computeBubbleCropRect(
            region = TranslationRegion(x = 10f, y = 12f, width = 120f, height = 80f),
            pageWidth = 800,
            pageHeight = 1200,
        )

        assertEquals(0, rect.left)
        assertEquals(0, rect.top)
        assertTrue(rect.right > 130)
        assertTrue(rect.bottom > 92)
    }

    @Test
    fun `chooseSampleSize selects largest power of two above target dimensions`() {
        assertEquals(1, VisionBubbleMontagePlanner.chooseSampleSize(400, 300, 350, 260))
        assertEquals(2, VisionBubbleMontagePlanner.chooseSampleSize(1000, 800, 300, 220))
        assertEquals(4, VisionBubbleMontagePlanner.chooseSampleSize(1200, 900, 300, 220))
        assertEquals(4, VisionBubbleMontagePlanner.chooseSampleSize(2400, 1800, 400, 300))
    }

    @Test
    fun `planMontageGrid bounds sheet dimensions and prevents overlapping cells`() {
        val requests = (1..30).map { idx ->
            VisionBubbleMontagePlanner.CropRequest(
                reference = VisionBubbleReference(
                    index = idx,
                    pageKey = "page_%02d.jpg".format((idx - 1) / 3 + 1),
                    blockIndex = (idx - 1) % 3,
                ),
                cropRect = VisionBubbleMontagePlanner.CropRect(0, 0, 500, 450),
            )
        }

        val layout = VisionBubbleMontagePlanner.planMontageGrid(requests)

        assertEquals(30, layout.placements.size)
        assertTrue(layout.sheetWidth in 1..VisionBubbleMontagePlanner.DEFAULT_MAX_SHEET_WIDTH)
        assertTrue(layout.sheetHeight in 1..VisionBubbleMontagePlanner.DEFAULT_MAX_SHEET_HEIGHT)

        layout.placements.forEach { cell ->
            assertTrue(cell.cellLeft >= 0)
            assertTrue(cell.cellTop >= 0)
            assertTrue(cell.cellLeft + cell.cellWidth <= layout.sheetWidth)
            assertTrue(cell.cellTop + cell.cellHeight <= layout.sheetHeight)
        }

        for (i in layout.placements.indices) {
            for (j in i + 1 until layout.placements.size) {
                val a = layout.placements[i]
                val b = layout.placements[j]
                val overlapsX = a.cellLeft < b.cellLeft + b.cellWidth && b.cellLeft < a.cellLeft + a.cellWidth
                val overlapsY = a.cellTop < b.cellTop + b.cellHeight && b.cellTop < a.cellTop + a.cellHeight
                assertTrue(!(overlapsX && overlapsY), "Cells ${a.reference.index} and ${b.reference.index} overlapped")
            }
        }
    }
}
