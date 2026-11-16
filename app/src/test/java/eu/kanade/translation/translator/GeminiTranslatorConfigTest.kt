package eu.kanade.translation.translator

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
        assertFalse("thinkingConfig" in generationConfig)
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
    fun `builds Gemini REST payload with model-aware thinkingConfig, JSON mode, and temperature`() {
        val flash25Json = Json.parseToJsonElement(
            buildGeminiRestRequestBody(
                systemInstruction = "Translate comics",
                requestText = "{}",
                maxOutputTokens = 8192,
                modelName = "gemini-2.5-flash",
                temperature = 0.3f,
                responseMimeType = "application/json",
                includeThinkingConfig = true,
            ),
        ).jsonObject.getValue("generationConfig").jsonObject
        assertEquals(0, flash25Json.getValue("thinkingConfig").jsonObject.getValue("thinkingBudget").jsonPrimitive.int)
        assertEquals("application/json", flash25Json.getValue("responseMimeType").jsonPrimitive.content)
        assertEquals(0.3f, flash25Json.getValue("temperature").jsonPrimitive.float, 0.001f)

        val pro25Json = Json.parseToJsonElement(
            buildGeminiRestRequestBody(
                systemInstruction = "Translate comics",
                requestText = "{}",
                maxOutputTokens = 8192,
                modelName = "gemini-2.5-pro",
                includeThinkingConfig = true,
            ),
        ).jsonObject.getValue("generationConfig").jsonObject
        assertEquals(128, pro25Json.getValue("thinkingConfig").jsonObject.getValue("thinkingBudget").jsonPrimitive.int)

        val flash3LiteJson = Json.parseToJsonElement(
            buildGeminiRestRequestBody(
                systemInstruction = "Translate comics",
                requestText = "{}",
                maxOutputTokens = 8192,
                modelName = "gemini-3.5-flash-lite",
                includeThinkingConfig = true,
            ),
        ).jsonObject.getValue("generationConfig").jsonObject
        assertEquals("minimal", flash3LiteJson.getValue("thinkingConfig").jsonObject.getValue("thinkingLevel").jsonPrimitive.content)

        val flash38Json = Json.parseToJsonElement(
            buildGeminiRestRequestBody(
                systemInstruction = "Translate comics",
                requestText = "{}",
                maxOutputTokens = 8192,
                modelName = "gemini-3.8-flash",
                includeThinkingConfig = true,
            ),
        ).jsonObject.getValue("generationConfig").jsonObject
        assertEquals("low", flash38Json.getValue("thinkingConfig").jsonObject.getValue("thinkingLevel").jsonPrimitive.content)

        val hydrogen4Json = Json.parseToJsonElement(
            buildGeminiRestRequestBody(
                systemInstruction = "Translate comics",
                requestText = "{}",
                maxOutputTokens = 8192,
                modelName = "gemini-4-hydrogen",
                includeThinkingConfig = true,
            ),
        ).jsonObject.getValue("generationConfig").jsonObject
        assertEquals("low", hydrogen4Json.getValue("thinkingConfig").jsonObject.getValue("thinkingLevel").jsonPrimitive.content)

        val pro3Json = Json.parseToJsonElement(
            buildGeminiRestRequestBody(
                systemInstruction = "Translate comics",
                requestText = "{}",
                maxOutputTokens = 8192,
                modelName = "gemini-3.1-pro-preview",
                includeThinkingConfig = true,
            ),
        ).jsonObject.getValue("generationConfig").jsonObject
        assertEquals("low", pro3Json.getValue("thinkingConfig").jsonObject.getValue("thinkingLevel").jsonPrimitive.content)

        val legacy20Json = Json.parseToJsonElement(
            buildGeminiRestRequestBody(
                systemInstruction = "Translate comics",
                requestText = "{}",
                maxOutputTokens = 8192,
                modelName = "gemini-2.0-flash",
                includeThinkingConfig = true,
            ),
        ).jsonObject.getValue("generationConfig").jsonObject
        assertFalse("thinkingConfig" in legacy20Json)
    }

    @Test
    fun `downgrades unsupported Gemini generationConfig options automatically on HTTP 400`() {
        val payloads = mutableListOf<String>()
        val result = executeGeminiRequestWithFallback(
            systemInstruction = "Translate",
            requestText = "{}",
            maxOutputTokens = 4096,
            modelName = "gemini-2.5-flash",
            temperature = 0.3f,
            responseMimeType = "application/json",
        ) { body ->
            payloads += body
            when (payloads.size) {
                1 -> 400 to """{"error":{"message":"thinkingBudget is not supported by this model"}}"""
                2 -> 400 to """{"error":{"message":"responseMimeType is unsupported"}}"""
                else -> 200 to """{"candidates":[{"content":{"parts":[{"text":"{\"001.jpg\":[\"Oi\"]}"}]}}]}"""
            }
        }

        assertEquals(200, result.first)
        assertEquals(3, payloads.size)
        val thirdConfig = Json.parseToJsonElement(payloads[2]).jsonObject.getValue("generationConfig").jsonObject
        assertFalse("thinkingConfig" in thirdConfig)
        assertFalse("responseMimeType" in thirdConfig)
        assertTrue("temperature" in thirdConfig)
    }

    @Test
    fun `extractGeminiResponseText ignores thought parts when assembling output`() {
        val responseJson = JSONObject(
            """
            {
              "candidates": [{
                "content": {
                  "parts": [
                    {"thought": true, "text": "Let me think step by step..."},
                    {"text": "{\"001.jpg\":[\"E aí!\"]}"}
                  ]
                }
              }]
            }
            """.trimIndent(),
        )

        assertEquals("""{"001.jpg":["E aí!"]}""", extractGeminiResponseText(responseJson))
    }

    @Test
    fun `normalizes Gemini model path for REST endpoint`() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent",
            buildGeminiRestUrl("models/gemini-3.6-flash"),
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:streamGenerateContent?alt=sse",
            buildGeminiStreamRestUrl("models/gemini-3.6-flash"),
        )
    }

    @Test
    fun `parseGeminiStreamLines merges SSE data chunks while ignoring thought parts`() {
        val ssePayload = """
            data: {"candidates":[{"content":{"parts":[{"thought":true,"text":"reasoning step"}]}}]}

            data: {"candidates":[{"content":{"parts":[{"text":"{\"001.jpg\":["}]}}]}

            data: {"candidates":[{"content":{"parts":[{"text":"\"Bora!\"]}"}]},"finishReason":"STOP"}]}

            data: [DONE]
        """.trimIndent()

        val parsed = parseGeminiStreamLines(ssePayload.lineSequence())

        assertEquals("""{"001.jpg":["Bora!"]}""", parsed.text)
        assertEquals("STOP", parsed.finishReason)
    }

    @Test
    fun `parseGeminiStreamLines handles plain JSON fallback payload cleanly`() {
        val plainJson = """{"candidates":[{"content":{"parts":[{"text":"Capítulo traduzido"}]},"finishReason":"STOP"}]}"""

        val parsed = parseGeminiStreamLines(plainJson.lineSequence())

        assertEquals("Capítulo traduzido", parsed.text)
        assertEquals("STOP", parsed.finishReason)
    }

    @Test
    fun `executeGeminiStreamWithFallback falls back from SSE streaming to generateContent on unsupported endpoint`() {
        val calledUrls = mutableListOf<String>()
        val result = executeGeminiStreamWithFallback(
            systemInstruction = "Translate",
            requestText = "{}",
            maxOutputTokens = 4096,
            modelName = "gemini-2.5-flash",
            temperature = 0.3f,
            responseMimeType = "application/json",
        ) { url, _ ->
            calledUrls += url
            if (url.contains("streamGenerateContent")) {
                GeminiHttpResult(
                    statusCode = 404,
                    content = GeminiResponseContent("", ""),
                    errorBody = """{"error":{"message":"streamGenerateContent not supported"}}""",
                )
            } else {
                GeminiHttpResult(
                    statusCode = 200,
                    content = GeminiResponseContent("""{"001.jpg":["Ok"]}""", "STOP"),
                )
            }
        }

        assertEquals(200, result.statusCode)
        assertEquals("""{"001.jpg":["Ok"]}""", result.content.text)
        assertEquals(2, calledUrls.size)
        assertTrue(calledUrls[0].endsWith(":streamGenerateContent?alt=sse"))
        assertTrue(calledUrls[1].endsWith(":generateContent"))
    }

    @Test
    fun `isTransientTranslationIoFailure retries timeouts and socket resets but not DNS or SSL failures`() {
        assertTrue(TranslationNetworkHelper.isTransientTranslationIoFailure(java.net.SocketTimeoutException("timeout")))
        assertTrue(TranslationNetworkHelper.isTransientTranslationIoFailure(java.io.EOFException("eof")))
        assertTrue(TranslationNetworkHelper.isTransientTranslationIoFailure(java.net.SocketException("reset")))
        assertFalse(TranslationNetworkHelper.isTransientTranslationIoFailure(java.net.UnknownHostException("offline")))
        assertFalse(TranslationNetworkHelper.isTransientTranslationIoFailure(javax.net.ssl.SSLException("handshake")))
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
