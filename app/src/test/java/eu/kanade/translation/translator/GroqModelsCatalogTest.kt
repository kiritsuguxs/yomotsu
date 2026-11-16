package eu.kanade.translation.translator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GroqModelsCatalogTest {

    @Test
    fun `parses Groq models filters audio tts guard and inactive entries and prioritizes Qwen 3_8 Vision`() {
        val json = """
            {
              "object": "list",
              "data": [
                {
                  "id": "whisper-large-v3-turbo",
                  "active": true,
                  "context_window": 448
                },
                {
                  "id": "canopylabs/orpheus-v1-english",
                  "active": true,
                  "context_window": 4000
                },
                {
                  "id": "meta-llama/llama-prompt-guard-2-86m",
                  "active": true,
                  "context_window": 512
                },
                {
                  "id": "openai/gpt-oss-safeguard-20b",
                  "active": true,
                  "context_window": 131072
                },
                {
                  "id": "deprecated-model",
                  "active": false,
                  "context_window": 8192
                },
                {
                  "id": "openai/gpt-oss-20b",
                  "active": true,
                  "context_window": 131072
                },
                {
                  "id": "openai/gpt-oss-120b",
                  "active": true,
                  "context_window": 131072
                },
                {
                  "id": "qwen/qwen3.8-27b",
                  "active": true,
                  "context_window": 131072
                },
                {
                  "id": "llama-3.3-70b-versatile",
                  "active": true,
                  "context_window": 131072
                }
              ]
            }
        """.trimIndent()

        val models = parseGroqModels(json)

        assertEquals(
            listOf(
                "qwen/qwen3.8-27b",
                "openai/gpt-oss-120b",
                "openai/gpt-oss-20b",
                "llama-3.3-70b-versatile",
            ),
            models.map { it.id },
        )
        assertTrue(models[0].isRecommended)
        assertTrue(models[0].supportsVision)
        assertFalse(models[1].supportsVision)
        assertFalse(models[2].supportsVision)
        assertFalse(models[3].supportsVision)
    }

    @Test
    fun `curated defaults include recommended Qwen 3_8 Vision and GPT-OSS models`() {
        assertEquals(
            listOf("qwen/qwen3.8-27b", "openai/gpt-oss-120b", "openai/gpt-oss-20b"),
            GroqModelsCatalog.curatedDefaults.map { it.id },
        )
        assertTrue(GroqModelsCatalog.curatedDefaults.first().isRecommended)
        assertTrue(GroqModelsCatalog.curatedDefaults.first().supportsVision)
    }

    @Test
    fun `formats context length human readably`() {
        assertNull(GroqModelsCatalog.formatContextLength(0))
        assertEquals("512 ctx", GroqModelsCatalog.formatContextLength(512))
        assertEquals("131k ctx", GroqModelsCatalog.formatContextLength(131072))
        assertEquals("1M ctx", GroqModelsCatalog.formatContextLength(1048576))
    }

    @Test
    fun `buildModelsRequest forces network refresh with Bearer auth`() {
        val request = GroqModelsCatalog.buildModelsRequest("gsk_test_key")

        assertEquals("https://api.groq.com/openai/v1/models", request.url.toString())
        assertEquals("Bearer gsk_test_key", request.header("Authorization"))
        assertTrue(request.cacheControl.noCache)
    }
}
