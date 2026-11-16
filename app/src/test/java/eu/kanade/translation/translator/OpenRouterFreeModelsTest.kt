package eu.kanade.translation.translator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenRouterFreeModelsTest {

    @Test
    fun `parses free models detects vision and ignores paid models`() {
        val json = """
            {
              "data": [
                {
                  "id": "anthropic/claude-3.7-sonnet",
                  "name": "Claude 3.7 Sonnet",
                  "context_length": 200000,
                  "pricing": { "prompt": "0.000003", "completion": "0.000015" },
                  "architecture": { "input_modalities": ["text", "image"] }
                },
                {
                  "id": "meta-llama/llama-3.3-70b-instruct:free",
                  "name": "Llama 3.3 70B Instruct (free)",
                  "context_length": 131072,
                  "pricing": { "prompt": "0", "completion": "0" },
                  "architecture": { "input_modalities": ["text"] }
                },
                {
                  "id": "google/gemini-2.0-flash-exp:free",
                  "name": "Gemini 2.0 Flash Experimental (free)",
                  "context_length": 1048576,
                  "pricing": { "prompt": "0", "completion": "0", "image": "0" },
                  "architecture": { "input_modalities": ["text", "image"] }
                },
                {
                  "id": "openrouter/free",
                  "name": "Free Models Router",
                  "context_length": 200000,
                  "pricing": { "prompt": "-1", "completion": "-1" },
                  "architecture": { "modality": "text+image->text" }
                },
                {
                  "id": "qwen/qwen2.5-vl-72b-instruct:free",
                  "name": "Qwen2.5 VL 72B (free)",
                  "context_length": 32768,
                  "pricing": { "prompt": "0.0", "completion": "0.00" },
                  "architecture": { "modality": "text+image->text" }
                }
              ]
            }
        """.trimIndent()

        val models = parseOpenRouterFreeModels(json)

        assertEquals(
            listOf(
                "openrouter/free",
                "google/gemini-2.0-flash-exp:free",
                "qwen/qwen2.5-vl-72b-instruct:free",
                "meta-llama/llama-3.3-70b-instruct:free",
            ),
            models.map { it.id },
        )
        assertTrue(models[0].supportsVision)
        assertTrue(models[1].supportsVision)
        assertTrue(models[2].supportsVision)
        assertFalse(models[3].supportsVision)
    }

    @Test
    fun `formats context length human readably`() {
        assertNull(OpenRouterFreeModelsCatalog.formatContextLength(0))
        assertEquals("512 ctx", OpenRouterFreeModelsCatalog.formatContextLength(512))
        assertEquals("131k ctx", OpenRouterFreeModelsCatalog.formatContextLength(131072))
        assertEquals("1M ctx", OpenRouterFreeModelsCatalog.formatContextLength(1048576))
    }
}
