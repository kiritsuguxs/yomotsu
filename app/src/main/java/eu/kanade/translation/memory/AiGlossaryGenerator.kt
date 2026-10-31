package eu.kanade.translation.memory

import eu.kanade.tachiyomi.network.await
import eu.kanade.translation.translator.ComicTranslationContext
import eu.kanade.translation.translator.TextTranslators
import eu.kanade.translation.translator.TranslationNetworkHelper
import eu.kanade.translation.translator.buildGeminiHttpDiagnostic
import eu.kanade.translation.translator.buildGeminiRestRequestBody
import eu.kanade.translation.translator.buildGeminiRestUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import tachiyomi.domain.translation.TranslationLlmProvider
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

sealed interface AiGlossaryResult {
    data class Success(
        val addedCount: Int,
        val providerLabel: String,
        val modelName: String,
    ) : AiGlossaryResult

    data object MissingApiKey : AiGlossaryResult
    data class Failure(val reason: String) : AiGlossaryResult
}

object AiGlossaryGenerator {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val objectBlockRegex = Regex("""\{[^{}]+\}""")

    suspend fun generateAndApply(
        context: ComicTranslationContext,
        mangaTitle: String,
        mangaGenresText: String = "",
        mangaDescription: String = "",
        preferences: TranslationPreferences = Injekt.get(),
    ): AiGlossaryResult = withContext(Dispatchers.IO) {
        val cleanTitle = mangaTitle.trim()
        if (cleanTitle.isEmpty()) {
            return@withContext AiGlossaryResult.Failure("Título da obra inválido.")
        }

        TranslationLlmProvider.entries.forEach(preferences::migrateLegacyLlmSettings)
        val preferredProvider = TextTranslators.fromPref(preferences.translationEngine()).llmProvider
        val candidateProviders = buildList {
            if (preferredProvider != null) add(preferredProvider)
            TranslationLlmProvider.entries.forEach { if (it !in this) add(it) }
        }
        val activeProvider = candidateProviders.firstOrNull {
            preferences.llmApiKey(it).get().trim().isNotEmpty()
        } ?: return@withContext AiGlossaryResult.MissingApiKey

        val apiKey = preferences.llmApiKey(activeProvider).get().trim()
        val modelName = preferences.llmModel(activeProvider).get().trim()
            .ifBlank { activeProvider.defaultModel }
        val maxTokens = (preferences.translationEngineMaxOutputTokens().get().toIntOrNull() ?: 4096)
            .coerceIn(2048, 8192)

        val systemInstruction = buildGlossarySystemPrompt()
        val userRequest = buildGlossaryUserPrompt(
            mangaTitle = cleanTitle,
            mangaGenresText = mangaGenresText,
            mangaDescription = mangaDescription,
        )

        try {
            val rawResponse = when (activeProvider) {
                TranslationLlmProvider.GEMINI -> requestGeminiGlossary(
                    apiKey = apiKey,
                    modelName = modelName,
                    systemInstruction = systemInstruction,
                    userRequest = userRequest,
                    maxOutputTokens = maxTokens,
                )
                TranslationLlmProvider.OPENROUTER -> requestOpenRouterGlossary(
                    apiKey = apiKey,
                    modelName = modelName,
                    systemInstruction = systemInstruction,
                    userRequest = userRequest,
                    maxOutputTokens = maxTokens.coerceAtMost(4096),
                )
            }

            val entries = parseAiGlossaryEntries(rawResponse)
            if (entries.isEmpty()) {
                return@withContext AiGlossaryResult.Failure(
                    "A IA não retornou termos válidos para esta obra. Tente novamente.",
                )
            }

            TranslationMemory.replaceEntries(context, entries, merge = true)
            val providerLabel = when (activeProvider) {
                TranslationLlmProvider.GEMINI -> "Gemini"
                TranslationLlmProvider.OPENROUTER -> "OpenRouter"
            }
            AiGlossaryResult.Success(
                addedCount = entries.size,
                providerLabel = providerLabel,
                modelName = modelName,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val safeMessage = error.message
                ?.replace(apiKey, "[REDACTED]")
                ?.trim()
                ?.take(180)
                .orEmpty()
                .ifBlank { error::class.java.simpleName }
            AiGlossaryResult.Failure("Falha ao gerar glossário: $safeMessage")
        }
    }

    private suspend fun requestGeminiGlossary(
        apiKey: String,
        modelName: String,
        systemInstruction: String,
        userRequest: String,
        maxOutputTokens: Int,
    ): String {
        val body = buildGeminiRestRequestBody(
            systemInstruction = systemInstruction,
            requestText = userRequest,
            maxOutputTokens = maxOutputTokens,
        )
        val request = Request.Builder()
            .url(buildGeminiRestUrl(modelName))
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        val response = TranslationNetworkHelper.sharedClient.newCall(request).await()
        val rawBody = response.use { res ->
            val text = res.body.string()
            if (!res.isSuccessful) {
                throw IllegalStateException(buildGeminiHttpDiagnostic(res.code, text, apiKey))
            }
            text
        }

        val json = JSONObject(rawBody)
        val parts = json.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
        val content = buildString {
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    if (!part.optBoolean("thought", false)) {
                        append(part.optString("text"))
                    }
                }
            }
        }
        if (content.isBlank()) {
            val finishReason = json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optString("finishReason")
                .orEmpty()
                .ifBlank { "EMPTY" }
            throw IllegalStateException("Resposta vazia do Gemini ($finishReason)")
        }
        return content
    }

    private suspend fun requestOpenRouterGlossary(
        apiKey: String,
        modelName: String,
        systemInstruction: String,
        userRequest: String,
        maxOutputTokens: Int,
    ): String {
        val payload = buildJsonObject {
            put("model", modelName)
            put("temperature", 0.2f)
            put("max_tokens", maxOutputTokens)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", systemInstruction)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", userRequest)
                }
            }
        }.toString()

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        val response = TranslationNetworkHelper.sharedClient.newCall(request).await()
        val rawBody = response.use { res ->
            val text = res.body.string()
            if (!res.isSuccessful) {
                throw IllegalStateException("OpenRouter HTTP ${res.code}")
            }
            text
        }
        val json = JSONObject(rawBody)
        val choices = json.optJSONArray("choices")
            ?: throw IllegalStateException(
                json.optJSONObject("error")?.optString("message") ?: "Resposta inválida do OpenRouter",
            )
        return choices.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
    }

    internal fun buildGlossarySystemPrompt(): String = """
        You are an expert Brazilian Portuguese (pt-BR) Manga, Manhwa, Manhua, and Webtoon scanlation editor.
        Your task is to generate a concise, highly accurate translation glossary tailored EXCLUSIVELY to the specific work requested.

        RULES:
        1. If you know the specific work by its title/synopsis, include its real main and recurring character names (in standard English/romanized scanlation spelling, with "type": "NAME" and "protected": true), specific organizations/clans/guilds/sects/locations, signature skills/techniques ("type": "TECHNIQUE"), and titles/ranks ("type": "TITLE").
        2. Include ONLY terms that actually fit THIS specific work and its exact setting/genre (between 25 and 60 entries). NEVER include unrelated genre terms (e.g., no Dungeon/System terms for a historical romance; no Murim terms for a modern school drama).
        3. Include appropriate honorifics ONLY if they match the work's origin (e.g., Korean honorifics like Oppa, Hyung, Sunbae, -nim for Korean Manhwa; Japanese honorifics like -san, -sama, -senpai for Japanese Manga).
        4. "source" must be the English/romanized term as it appears in English comic chapters. "target" must be the natural Brazilian Portuguese (pt-BR) scanlation translation.
        5. For proper character names or terms kept unchanged in pt-BR, keep "target" identical to the preferred spelling and set "protected": true. For translated terms, set "protected": false.
        6. Output ONLY a valid JSON array of objects, with NO markdown fences and NO commentary:
        [{"source":"Term in English","target":"Tradução em PT-BR","type":"NAME|TITLE|TECHNIQUE|TERM","protected":true}]
    """.trimIndent()

    internal fun buildGlossaryUserPrompt(
        mangaTitle: String,
        mangaGenresText: String,
        mangaDescription: String,
    ): String = buildString {
        appendLine("Generate a work-specific pt-BR scanlation glossary JSON array for:")
        appendLine("Title: ${mangaTitle.trim()}")
        if (mangaGenresText.isNotBlank()) {
            appendLine("Genres/Tags: ${mangaGenresText.trim().take(400)}")
        }
        if (mangaDescription.isNotBlank()) {
            appendLine("Synopsis: ${mangaDescription.trim().take(1800)}")
        }
    }

    internal fun parseAiGlossaryEntries(rawResponse: String): List<TranslationMemoryEntry> {
        val cleaned = rawResponse.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (cleaned.isEmpty()) return emptyList()

        val jsonObjects = extractJsonObjects(cleaned)
        val seen = HashSet<String>()
        val entries = ArrayList<TranslationMemoryEntry>()

        for (obj in jsonObjects) {
            val source = obj.optString("source")
                .ifBlank { obj.optString("original") }
                .ifBlank { obj.optString("term") }
                .trim()
            val target = obj.optString("target")
                .ifBlank { obj.optString("translation") }
                .ifBlank { obj.optString("pt") }
                .trim()
            if (source.isEmpty() || target.isEmpty()) continue
            if (source.length > 80 || target.length > 100) continue

            val type = when (obj.optString("type", "TERM").trim().uppercase()) {
                "NAME", "CHARACTER", "PERSON" -> TranslationMemoryEntryType.NAME
                "TITLE", "RANK", "HONORIFIC" -> TranslationMemoryEntryType.TITLE
                "TECHNIQUE", "SKILL", "ABILITY" -> TranslationMemoryEntryType.TECHNIQUE
                else -> TranslationMemoryEntryType.TERM
            }
            val sameText = source.equals(target, ignoreCase = true)
            val isProtected = obj.optBoolean("protected", false) ||
                type == TranslationMemoryEntryType.NAME ||
                sameText

            if (seen.add(source.lowercase())) {
                entries += TranslationMemoryEntry(
                    source = source,
                    target = target,
                    type = type,
                    isProtected = isProtected,
                )
            }
        }

        if (entries.isNotEmpty()) return entries

        // Fallback if the model replied with plain "Source => Target" lines
        return TranslationGlossaryManager.parseMany(
            text = cleaned,
            type = TranslationMemoryEntryType.TERM,
            isProtected = false,
        ).entries
    }

    private fun extractJsonObjects(cleaned: String): List<JSONObject> {
        val firstBracket = cleaned.indexOf('[')
        val lastBracket = cleaned.lastIndexOf(']')
        if (firstBracket >= 0 && lastBracket > firstBracket) {
            val candidateArray = runCatching {
                JSONArray(cleaned.substring(firstBracket, lastBracket + 1))
            }.getOrNull()
            if (candidateArray != null) {
                return buildList {
                    for (i in 0 until candidateArray.length()) {
                        candidateArray.optJSONObject(i)?.let(::add)
                    }
                }
            }
        }

        val firstBrace = cleaned.indexOf('{')
        val lastBrace = cleaned.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            val rootObj = runCatching {
                JSONObject(cleaned.substring(firstBrace, lastBrace + 1))
            }.getOrNull()
            if (rootObj != null) {
                for (key in listOf("entries", "glossary", "terms", "items")) {
                    val arr = rootObj.optJSONArray(key)
                    if (arr != null) {
                        return buildList {
                            for (i in 0 until arr.length()) {
                                arr.optJSONObject(i)?.let(::add)
                            }
                        }
                    }
                }
            }
        }

        // Salvage complete JSON objects even if the array was truncated mid-output
        return objectBlockRegex.findAll(cleaned)
            .mapNotNull { match -> runCatching { JSONObject(match.value) }.getOrNull() }
            .toList()
    }
}
