package eu.kanade.translation.translator

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class GeminiTranslatorConfigTest {

    @Test
    fun `builds Gemini REST request without unsupported sampling parameters`() {
        val body = buildGeminiRestRequestBody(
            systemInstruction = "Translate comics",
            requestText = "Translate this page",
            maxOutputTokens = 4096,
        )
        val json = Json.parseToJsonElement(body).jsonObject
        val generationConfig = json.getValue("generationConfig").jsonObject

        assertEquals(4096, generationConfig.getValue("maxOutputTokens").jsonPrimitive.int)
        assertFalse("temperature" in generationConfig)
        assertFalse("topP" in generationConfig)
        assertFalse("topK" in generationConfig)
        assertEquals(
            1,
            json.getValue("systemInstruction")
                .jsonObject
                .getValue("parts")
                .jsonArray
                .size,
        )
        assertEquals(
            "Translate this page",
            json.getValue("contents")
                .jsonArray[0]
                .jsonObject
                .getValue("parts")
                .jsonArray[0]
                .jsonObject
                .getValue("text")
                .jsonPrimitive
                .content,
        )
    }

    @Test
    fun `normalizes Gemini model path for REST endpoint`() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent",
            buildGeminiRestUrl("models/gemini-3.6-flash"),
        )
    }

    @Test
    fun `builds Groq text-only request payloads cleanly with model-aware reasoning controls`() {
        val textOnlyBody = buildGroqRequestBody(
            modelName = "openai/gpt-oss-120b",
            systemInstruction = "Translate comics",
            requestText = "Translate JSON",
            maxOutputTokens = 4096,
            temperature = 0f,
        )
        val textOnlyJson = Json.parseToJsonElement(textOnlyBody).jsonObject
        val textOnlyUser = textOnlyJson.getValue("messages").jsonArray[1].jsonObject
        assertEquals("Translate JSON", textOnlyUser.getValue("content").jsonPrimitive.content)
        assertFalse("top_k" in textOnlyJson)
        assertEquals(4096, textOnlyJson.getValue("max_completion_tokens").jsonPrimitive.int)
        assertEquals("json_object", textOnlyJson.getValue("response_format").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("low", textOnlyJson.getValue("reasoning_effort").jsonPrimitive.content)
        assertEquals("false", textOnlyJson.getValue("include_reasoning").jsonPrimitive.content)
        assertEquals("0.01", textOnlyJson.getValue("temperature").jsonPrimitive.content)

        val qwenBody = buildGroqRequestBody(
            modelName = "qwen/qwen3.8-27b",
            systemInstruction = "Translate comics",
            requestText = "Translate JSON",
            maxOutputTokens = 4096,
            temperature = 0.3f,
        )
        val qwenJson = Json.parseToJsonElement(qwenBody).jsonObject
        assertEquals("none", qwenJson.getValue("reasoning_effort").jsonPrimitive.content)
        assertFalse("include_reasoning" in qwenJson)
        assertEquals(
            "Translate JSON",
            qwenJson.getValue("messages").jsonArray[1].jsonObject.getValue("content").jsonPrimitive.content,
        )

        val customBody = buildGroqRequestBody(
            modelName = "llama-3.3-70b-versatile",
            systemInstruction = "Translate comics",
            requestText = "Translate JSON",
            maxOutputTokens = 8192,
            temperature = 0.3f,
        )
        val customJson = Json.parseToJsonElement(customBody).jsonObject
        assertFalse("reasoning_effort" in customJson)
        assertFalse("include_reasoning" in customJson)
        assertEquals(8192, customJson.getValue("max_completion_tokens").jsonPrimitive.int)
    }
}
