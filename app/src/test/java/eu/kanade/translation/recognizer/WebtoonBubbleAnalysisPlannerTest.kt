package eu.kanade.translation.recognizer

import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.TranslationRegion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebtoonBubbleAnalysisPlannerTest {

    @Test
    fun `shouldUseTiledAnalysis enables tiling only for tall strips above pixel cap`() {
        assertFalse(WebtoonBubbleAnalysisPlanner.shouldUseTiledAnalysis(pageWidth = 1200, pageHeight = 1800))
        assertFalse(WebtoonBubbleAnalysisPlanner.shouldUseTiledAnalysis(pageWidth = 4000, pageHeight = 2000))
        assertTrue(WebtoonBubbleAnalysisPlanner.shouldUseTiledAnalysis(pageWidth = 1080, pageHeight = 18000))
    }

    @Test
    fun `planSlices covers full webtoon strip within per-slice pixel budget and overlap`() {
        val pageWidth = 1080
        val pageHeight = 18000
        val slices = WebtoonBubbleAnalysisPlanner.planSlices(pageWidth, pageHeight)

        assertTrue(slices.size >= 3)
        assertEquals(0, slices.first().top)
        assertEquals(pageHeight, slices.last().bottom)

        slices.forEach { slice ->
            assertTrue(slice.height > 0)
            assertTrue(pageWidth.toLong() * slice.height.toLong() <= WebtoonBubbleAnalysisPlanner.DEFAULT_MAX_ANALYSIS_PIXELS)
        }

        for (i in 0 until slices.lastIndex) {
            assertTrue(slices[i].bottom > slices[i + 1].top)
        }
    }

    @Test
    fun `assignIndicesToSlices prefers slice with greater interior clearance near boundary`() {
        val slices = WebtoonBubbleAnalysisPlanner.planSlices(pageWidth = 1080, pageHeight = 14000)
        assertTrue(slices.size >= 3)

        val secondSlice = slices[1]
        val centeredRegion = TranslationRegion(
            x = 200f,
            y = secondSlice.top + secondSlice.height / 2f - 60f,
            width = 300f,
            height = 120f,
        )
        val assigned = WebtoonBubbleAnalysisPlanner.assignIndicesToSlices(slices, listOf(centeredRegion))
        assertEquals(listOf(0), assigned[1])
    }

    @Test
    fun `slice coordinate round-trip restores full-page cleanup and layout regions`() {
        val slice = WebtoonBubbleAnalysisPlanner.SliceWindow(top = 4500, bottom = 10055)
        val block = TranslationBlock(
            text = "System warning",
            width = 240f,
            height = 80f,
            x = 120f,
            y = 6100f,
            symHeight = 18f,
            symWidth = 14f,
            angle = 0f,
            sourceRegions = listOf(TranslationRegion(120f, 6100f, 240f, 80f)),
        )

        val local = WebtoonBubbleAnalysisPlanner.toSliceLocalBlock(block, slice)
        assertEquals(1600f, local.y, 0.01f)
        assertEquals(1600f, local.sourceRegions.first().y, 0.01f)

        val restored = WebtoonBubbleAnalysisPlanner.shiftAnalysisToPage(
            analysis = SpeechBubbleAnalysis(
                cleanupRegion = TranslationRegion(110f, 1590f, 260f, 100f),
                layoutRegion = TranslationRegion(100f, 1580f, 280f, 120f),
                backgroundColor = -1,
                foregroundColor = 0,
                balloonDetected = true,
            ),
            slice = slice,
            pageWidth = 1080,
            pageHeight = 18000,
        )

        assertEquals(6090f, restored.cleanupRegion.y, 0.01f)
        assertEquals(6080f, restored.layoutRegion.y, 0.01f)
    }

    @Test
    fun `toSliceLocalRegions keeps overlapping regions shifted and excludes distant regions`() {
        val slice = WebtoonBubbleAnalysisPlanner.SliceWindow(top = 4000, bottom = 8000)
        val regions = listOf(
            TranslationRegion(100f, 500f, 200f, 80f),
            TranslationRegion(100f, 4500f, 220f, 100f),
            TranslationRegion(100f, 11000f, 200f, 90f),
        )

        val local = WebtoonBubbleAnalysisPlanner.toSliceLocalRegions(regions, slice)
        assertEquals(1, local.size)
        assertEquals(500f, local.first().y, 0.01f)
        assertEquals(100f, local.first().height, 0.01f)
    }
}
