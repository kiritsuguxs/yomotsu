package eu.kanade.translation.translator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeminiModelsCatalogTest {

    @Test
    fun `parses Gemini models filters non-translation entries and prioritizes gemini-3_8-flash`() {
        val json = """
            {
              "models": [
                {
                  "name": "models/text-embedding-004",
                  "displayName": "Text Embedding 004",
                  "inputTokenLimit": 2048,
                  "supportedGenerationMethods": ["embedContent"]
                },
                {
                  "name": "models/imagen-3.0-generate-002",
                  "displayName": "Imagen 3",
                  "inputTokenLimit": 480,
                  "supportedGenerationMethods": ["predict"]
                },
                {
                  "name": "models/gemini-3.8-flash-tts",
                  "displayName": "Gemini 3.8 Flash TTS",
                  "inputTokenLimit": 32768,
                  "supportedGenerationMethods": ["generateContent"]
                },
                {
                  "name": "models/gemini-3.8-live",
                  "displayName": "Gemini 3.8 Live",
                  "inputTokenLimit": 131072,
                  "supportedGenerationMethods": ["generateContent"]
                },
                {
                  "name": "models/gemini-nano-banana-2.1",
                  "displayName": "Nano Banana 2.1",
                  "inputTokenLimit": 32768,
                  "supportedGenerationMethods": ["generateContent"]
                },
                {
                  "name": "models/gemini-3.1-pro-preview",
                  "displayName": "Gemini 3.1 Pro Preview",
                  "inputTokenLimit": 1048576,
                  "supportedGenerationMethods": ["generateContent"]
                },
                {
                  "name": "models/gemini-3.8-flash",
                  "displayName": "Gemini 3.8 Flash",
                  "inputTokenLimit": 1048576,
                  "supportedGenerationMethods": ["generateContent", "countTokens"]
                },
                {
                  "name": "models/gemini-4-hydrogen",
                  "displayName": "Gemini 4 Hydrogen",
                  "inputTokenLimit": 1048576,
                  "supportedGenerationMethods": ["generateContent"]
                }
              ]
            }
        """.trimIndent()

        val models = parseGeminiModels(json)

        assertEquals(
            listOf(
                "gemini-3.8-flash",
                "gemini-3.1-pro-preview",
                "gemini-4-hydrogen",
            ),
            models.map { it.id },
        )
        assertTrue(models[0].isRecommended)
        assertTrue(models[0].supportsVision)
        assertEquals(1_048_576, models[0].contextLength)
        assertEquals("Gemini 4 Hydrogen", models[2].name)
    }

    @Test
    fun `curated defaults start with gemini-3_8-flash and include active 3_x and 4_x models`() {
        val ids = GeminiModelsCatalog.curatedDefaults.map { it.id }
        assertEquals("gemini-3.8-flash", ids.first())
        assertTrue("gemini-3.5-flash-lite" in ids)
        assertTrue("gemini-4-hydrogen" in ids)
        assertTrue(GeminiModelsCatalog.curatedDefaults.first().isRecommended)
        assertTrue(GeminiModelsCatalog.curatedDefaults.first().supportsVision)
    }

    @Test
    fun `buildModelsRequest uses x-goog-api-key header, force network, and pagination token`() {
        val firstPage = GeminiModelsCatalog.buildModelsRequest("AIza_test_key")
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models?pageSize=100",
            firstPage.url.toString(),
        )
        assertEquals("AIza_test_key", firstPage.header("x-goog-api-key"))
        assertTrue(firstPage.cacheControl.noCache)
        assertNull(firstPage.url.queryParameter("pageToken"))

        val secondPage = GeminiModelsCatalog.buildModelsRequest("AIza_test_key", pageToken = "next-123")
        assertEquals("next-123", secondPage.url.queryParameter("pageToken"))
    }

    @Test
    fun `sanitizes pasted Gemini API keys cleanly`() {
        assertEquals("AIzaSyTest123", sanitizeGeminiApiKey("  AIzaSyTest123\r\n "))
    }
}
