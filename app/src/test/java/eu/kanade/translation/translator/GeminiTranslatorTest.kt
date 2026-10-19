package eu.kanade.translation.translator

import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationBlock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeminiTranslatorTest {
    @Test
    fun `gemini request chunks keep a chapter request below the per-call block limit`() {
        val items = (1..45).map { "bubble-$it" }

        val chunks = chunkGeminiItems(items, maxItems = 16)

        assertEquals(listOf(16, 16, 13), chunks.map { it.size })
        assertEquals(items, chunks.flatten())
    }

    @Test
    fun `chunkPages splits 40 page chapter safely without dropping any page or block`() {
        val pages = (1..40).associate { pageNum ->
            val key = "%03d.jpg".format(pageNum)
            val blocks = (1..3).map { b -> TranslationBlock(text = "Dialogue $pageNum-$b") }.toMutableList()
            key to PageTranslation(blocks = blocks)
        }

        val chunks = chunkPages(pages, maxPages = 8, maxBlocks = 30)

        // 40 pages with 3 blocks each = 120 blocks.
        // Each chunk can take at most 8 pages (24 blocks). 40 / 8 = 5 chunks of 8 pages.
        assertEquals(5, chunks.size)
        chunks.forEach { chunk ->
            assertTrue(chunk.size <= 8)
            val totalBlocks = chunk.values.sumOf { it.blocks.size }
            assertTrue(totalBlocks <= 30)
        }

        // Verify all 40 pages and all 120 blocks exist in sequential order
        val allPageKeys = chunks.flatMap { it.keys }
        assertEquals(pages.keys.toList(), allPageKeys)
    }

    @Test
    fun `chunkPages handles empty pages map cleanly`() {
        val chunks = chunkPages(emptyMap())
        assertTrue(chunks.isEmpty())
    }
}

