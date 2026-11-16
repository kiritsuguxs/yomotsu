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

        val prefilled = """Reasoning with {braces} inside</think>{"001.jpg":["E aí?"]}"""
        assertEquals("""{"001.jpg":["E aí?"]}""", stripGroqThinkingTags(prefilled))

        val unclosed = """{"001.jpg":["Certo"]}<think>trailing thought"""
        assertEquals("""{"001.jpg":["Certo"]}""", stripGroqThinkingTags(unclosed))
    }

    @Test
    fun `sanitizes pasted Groq API keys and redacts secrets in diagnostics`() {
        assertEquals("gsk_abc123", sanitizeGroqApiKey("  gsk_abc123\r\n "))

        val diag = buildGroqUnknownDiagnostic(
            IllegalArgumentException("Header failure for gsk_supersecret123"),
            apiKey = "gsk_supersecret123",
        )
        assertTrue(diag.contains("IllegalArgumentException"))
        assertFalse(diag.contains("gsk_supersecret123"))
        assertTrue(diag.contains("[REDACTED]"))
    }

    @Test
    fun `safely extracts null message content and unwraps nested page map objects`() {
        val nullChoice = org.json.JSONObject("""{"message":{"role":"assistant","content":null}}""")
        assertEquals("", extractGroqMessageContent(nullChoice))

        val wrapped = org.json.JSONObject("""{"translations":{"001.jpg":["Oi!"],"002.jpg":["Tudo bem?"]}}""")
        val unwrapped = unwrapGroqPageMapJson(wrapped, listOf("001.jpg", "002.jpg"))
        assertTrue(unwrapped.has("001.jpg"))
        assertEquals("Oi!", unwrapped.getJSONArray("001.jpg").getString(0))
    }

    @Test
    fun `uses strict json_schema for supported Groq models and omits restrictive top_p`() {
        val body = buildGroqRequestBody(
            modelName = "qwen/qwen3.8-27b",
            systemInstruction = "Translate comics",
            requestText = "Translate JSON",
            maxOutputTokens = 4096,
            temperature = 0.3f,
            expectedPageKeys = listOf("001.jpg", "002.jpg"),
            responseFormatMode = initialGroqResponseFormatMode("qwen/qwen3.8-27b"),
        )
        val parsed = kotlinx.serialization.json.Json.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject
        assertFalse("top_p" in parsed)

        val responseFormat = parsed["response_format"] as kotlinx.serialization.json.JsonObject
        assertEquals("json_schema", (responseFormat["type"] as kotlinx.serialization.json.JsonPrimitive).content)
        val jsonSchema = responseFormat["json_schema"] as kotlinx.serialization.json.JsonObject
        assertEquals("true", (jsonSchema["strict"] as kotlinx.serialization.json.JsonPrimitive).content)
        val schema = jsonSchema["schema"] as kotlinx.serialization.json.JsonObject
        val properties = schema["properties"] as kotlinx.serialization.json.JsonObject
        assertTrue("001.jpg" in properties)
        assertTrue("002.jpg" in properties)
        assertEquals(GroqResponseFormatMode.JSON_OBJECT, initialGroqResponseFormatMode("llama-3.3-70b-versatile"))
    }

    @Test
    fun `detects Groq refusals and handles bare empty responses according to chunk translatability`() {
        val refusalBody = """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":null,"refusal":"Safety policy"}}]}"""
        val refusalError = org.junit.jupiter.api.Assertions.assertThrows(TranslationProviderException::class.java) {
            parseGroqTranslationBody(
                responseBody = refusalBody,
                modelName = "qwen/qwen3.8-27b",
                apiKey = "gsk_test",
                expectedPageKeys = listOf("001.jpg"),
                hasTranslatableBlocks = true,
                isFinalAttempt = false,
            )
        }
        assertFalse(refusalError.retryable)
        assertTrue(refusalError.message.orEmpty().contains("openai/gpt-oss-120b"))

        val bareEmptyQuoteBody = """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"\"\""}}]}"""
        val firstAttemptError = org.junit.jupiter.api.Assertions.assertThrows(TranslationProviderException::class.java) {
            parseGroqTranslationBody(
                responseBody = bareEmptyQuoteBody,
                modelName = "qwen/qwen3.8-27b",
                apiKey = "gsk_test",
                expectedPageKeys = listOf("001.jpg"),
                hasTranslatableBlocks = false,
                isFinalAttempt = false,
            )
        }
        assertTrue(firstAttemptError.retryable)

        val recoveredWatermarkChunk = parseGroqTranslationBody(
            responseBody = bareEmptyQuoteBody,
            modelName = "qwen/qwen3.8-27b",
            apiKey = "gsk_test",
            expectedPageKeys = listOf("001.jpg"),
            hasTranslatableBlocks = false,
            isFinalAttempt = true,
        )
        assertEquals(0, recoveredWatermarkChunk.length())

        val finalDialogueError = org.junit.jupiter.api.Assertions.assertThrows(TranslationProviderException::class.java) {
            parseGroqTranslationBody(
                responseBody = bareEmptyQuoteBody,
                modelName = "qwen/qwen3.8-27b",
                apiKey = "gsk_test",
                expectedPageKeys = listOf("001.jpg"),
                hasTranslatableBlocks = true,
                isFinalAttempt = true,
            )
        }
        assertFalse(finalDialogueError.retryable)
        assertTrue(finalDialogueError.message.orEmpty().contains("openai/gpt-oss-120b"))
    }

    @Test
    fun `comic prompt forbids returning bare empty quotes or empty object`() {
        val prompt = buildComicTranslationPrompt(
            eu.kanade.translation.recognizer.TextRecognizerLanguage.ENGLISH,
            TextTranslatorLanguage.PORTUGUESE,
        )
        assertTrue(prompt.contains("NEVER output bare \"\" or {}"))
        assertTrue(buildGroqFormatRetryReminder(listOf("001.jpg")).contains("\"001.jpg\""))
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
