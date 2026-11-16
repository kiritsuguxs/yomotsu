package eu.kanade.translation.translator

import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationBlock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GroqTranslatorOptimizationTest {

    @Test
    fun `prepareGroqChunk filters cached blocks and fully cached pages before LLM and Vision`() {
        val cachedPage = PageTranslation(
            blocks = mutableListOf(block("Cached bubble 1")),
            imgWidth = 800f,
            imgHeight = 2400f,
        )
        val mixedPage = PageTranslation(
            blocks = mutableListOf(
                block("Cached bubble 2"),
                block("New manhwa bubble"),
            ),
            imgWidth = 720f,
            imgHeight = 3200f,
        )
        val cache = mapOf(
            "Cached bubble 1" to "Balão salvo 1",
            "Cached bubble 2" to "Balão salvo 2",
        )

        val prepared = prepareGroqChunk(
            pages = linkedMapOf(
                "001.jpg" to cachedPage,
                "002.jpg" to mixedPage,
            ),
            context = ComicTranslationContext.EMPTY,
            applyCached = { _, cached -> cached },
            prepareText = { eu.kanade.translation.memory.PreparedGlossaryText(it, it, emptyList()) },
            cacheLookup = { cache[it] },
        )

        assertEquals("Balão salvo 1", cachedPage.blocks[0].translation)
        assertEquals("Balão salvo 2", mixedPage.blocks[0].translation)
        assertEquals("", mixedPage.blocks[1].translation)

        assertEquals(listOf("002.jpg"), prepared.uncachedPages.keys.toList())
        val uncachedMixed = prepared.uncachedPages.getValue("002.jpg")
        assertEquals(1, uncachedMixed.blocks.size)
        assertEquals("New manhwa bubble", uncachedMixed.blocks[0].text)
        assertEquals(720f, uncachedMixed.imgWidth)
        assertEquals(3200f, uncachedMixed.imgHeight)
    }

    @Test
    fun `parses Groq retry-after and Go-style rate limit reset durations`() {
        assertEquals(8000L, parseGroqDurationHeaderMs("8"))
        assertEquals(7660L, parseGroqDurationHeaderMs("7.66s"))
        assertEquals(200L, parseGroqDurationHeaderMs("200ms"))
        assertEquals(65_200L, parseGroqDurationHeaderMs("1m5.2s"))
        assertNull(parseGroqDurationHeaderMs("  "))

        assertEquals(
            12_500L,
            parseGroqRateLimitWaitMs(
                retryAfterHeader = "5",
                resetTokensHeader = "12.5s",
                resetRequestsHeader = "1s",
            ),
        )
    }

    @Test
    fun `detects daily quota exhaustion vs short per-minute rate limits`() {
        assertTrue(isGroqDailyQuotaExhausted("""{"error":{"message":"Rate limit reached for tokens per day (TPD)"}}"""))
        assertTrue(isGroqDailyQuotaExhausted("""{"error":{"message":"Limit reached on requests per day (RPD)"}}"""))
        assertFalse(isGroqDailyQuotaExhausted("""{"error":{"message":"Rate limit reached for tokens per minute (TPM): try again in 7.66s"}}"""))
    }

    @Test
    fun `strips think blocks cleanly without corrupting JSON`() {
        val raw = """<think>Analyzing comic context</think>{"001.jpg":["Olá!"]}"""
        assertEquals("""{"001.jpg":["Olá!"]}""", stripGroqThinkingTags(raw))
    }

    private fun block(text: String) = TranslationBlock(
        text = text,
        width = 160f,
        height = 60f,
        x = 20f,
        y = 40f,
        symHeight = 16f,
        symWidth = 10f,
        angle = 0f,
    )
}
