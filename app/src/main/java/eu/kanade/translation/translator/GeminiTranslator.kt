package eu.kanade.translation.translator

import eu.kanade.tachiyomi.network.await
import eu.kanade.translation.context.TranslationContext
import eu.kanade.translation.memory.TranslationCache
import eu.kanade.translation.memory.TranslationGlossary
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationRecoveryPolicy
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import logcat.LogPriority
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import tachiyomi.core.common.util.system.logcat
import java.io.IOException

class GeminiTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
    private val apiKey: String,
    private val modelName: String,
    val maxOutputToken: Int,
    val temp: Float,
) : TextTranslator {

    private val okHttpClient by lazy { TranslationNetworkHelper.sharedClient }
    private val continuity = TranslationContext()
    private val cleanApiKey: String
        get() = sanitizeGeminiApiKey(apiKey)

    override suspend fun translate(
        pages: MutableMap<String, PageTranslation>,
        context: ComicTranslationContext,
    ) {
        if (cleanApiKey.isBlank()) throw TranslationProviderFailureMapper.missingKey(TranslationProviderId.GEMINI)
        if (modelName.isBlank()) throw TranslationProviderFailureMapper.missingModel(TranslationProviderId.GEMINI)
        try {
            continuity.enter(context)
            val chunks = chunkPages(
                pages = pages,
                maxPages = GEMINI_MAX_PAGES_PER_CHUNK,
                maxBlocks = GEMINI_MAX_BLOCKS_PER_CHUNK,
            )
            for (batch in chunks.chunked(GEMINI_CONCURRENT_CHUNKS)) {
                val continuityPrefix = continuity.promptPrefix()
                val batchRemembered = coroutineScope {
                    batch.map { chunk ->
                        async {
                            translateChunk(chunk, context, continuityPrefix)
                        }
                    }.awaitAll()
                }
                batchRemembered.forEach { pairs ->
                    pairs.forEach { (source, translation) ->
                        continuity.remember(source, translation)
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: TranslationProviderException) {
            logcat(LogPriority.ERROR, error) { "Gemini translation failed: ${error.category}" }
            throw error
        } catch (error: Throwable) {
            val mapped = TranslationProviderFailureMapper.fromThrowable(TranslationProviderId.GEMINI, error)
            logcat(LogPriority.ERROR, mapped) { "Gemini translation failed: ${mapped.category}" }
            if (mapped.category == TranslationFailureCategory.CONNECTION) {
                throw TranslationProviderException(TranslationProviderId.GEMINI, TranslationFailureCategory.CONNECTION, true, buildGeminiNetworkDiagnostic(error, cleanApiKey), error)
            }
            if (mapped.category == TranslationFailureCategory.UNKNOWN) {
                throw TranslationProviderException(TranslationProviderId.GEMINI, TranslationFailureCategory.UNKNOWN, false, buildGeminiUnknownDiagnostic(error, cleanApiKey), error)
            }
            throw mapped
        }
    }

    private suspend fun translateChunk(
        chunkPages: Map<String, PageTranslation>,
        context: ComicTranslationContext,
        continuityPrefix: String,
    ): List<Pair<String, String>> {
        val blocks = chunkPages.values.flatMap { it.blocks }
        if (blocks.isEmpty()) return emptyList()
        val normalizedTexts = blocks.map { normalizeOcrText(it.text) }
        val preparedTexts = normalizedTexts.map { TranslationGlossary.prepare(it, context) }
        val cached = normalizedTexts.map { TranslationCache.get(context, fromLang, toLang, it) }

        if (cached.all { it != null }) {
            val remembered = ArrayList<Pair<String, String>>(blocks.size)
            blocks.forEachIndexed { index, block ->
                val cachedTranslation = cached[index].orEmpty()
                if (cachedTranslation.contains("RTMTH", ignoreCase = true) ||
                    TranslationRecoveryPolicy.isWatermarkOrCredit(block.text) ||
                    TranslationRecoveryPolicy.isWatermarkOrCredit(cachedTranslation)
                ) {
                    block.translation = ""
                } else {
                    block.translation = TranslationGlossary.apply(block.text, cachedTranslation, context)
                }
                if (block.translation.isNotBlank()) {
                    remembered += block.text to block.translation
                }
            }
            return remembered
        }

        val preparedIterator = preparedTexts.iterator()
        val inputJson = buildComicInputJson(chunkPages) {
            preparedIterator.next().textForTranslation
        }
        val relevantGlossaryText = normalizedTexts.joinToString("\n") + "\n" + inputJson
        val systemInstruction = buildComicTranslationPrompt(fromLang, toLang)
        val requestText = continuityPrefix + buildComicTranslationRequest(
            context = context,
            inputJson = inputJson,
            relevantText = relevantGlossaryText,
        )
        val endpointUrl = buildGeminiRestUrl(modelName)

        val rawBody = executeGeminiRequestWithFallback(
            systemInstruction = systemInstruction,
            requestText = requestText,
            maxOutputTokens = maxOutputToken,
            modelName = modelName,
            temperature = temp,
            responseMimeType = JSON_MIME_TYPE,
        ) { requestBody ->
            val request = Request.Builder()
                .url(endpointUrl)
                .header("x-goog-api-key", cleanApiKey)
                .header("Content-Type", "application/json")
                .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            okHttpClient.newCall(request).await().use { it.code to it.body.string() }
        }.let { (statusCode, body) ->
            if (statusCode !in 200..299) {
                throw TranslationProviderException(
                    TranslationProviderId.GEMINI,
                    TranslationFailureCategory.UNKNOWN,
                    statusCode == 429 || statusCode >= 500,
                    buildGeminiHttpDiagnostic(statusCode, body, cleanApiKey),
                )
            }
            body
        }

        val responseJson = JSONObject(rawBody)
        val responseText = extractGeminiResponseText(responseJson)

        if (responseText.isBlank()) {
            val finishReason = responseJson.optJSONArray("candidates")?.optJSONObject(0)?.optString("finishReason") ?: "UNKNOWN"
            throw TranslationProviderException(
                TranslationProviderId.GEMINI,
                TranslationFailureCategory.UNKNOWN,
                false,
                "Gemini blocked the translation. Reason: $finishReason",
            )
        }

        val resJson = try {
            parseComicTranslationResponse(responseText)
        } catch (_: Exception) {
            val snippet = responseText.take(50).replace("\n", " ")
            throw TranslationProviderException(
                TranslationProviderId.GEMINI,
                TranslationFailureCategory.UNKNOWN,
                false,
                "Gemini JSON error: $snippet",
            )
        }

        val remembered = ArrayList<Pair<String, String>>(blocks.size)
        val pageEntries = chunkPages.entries.toList()
        var globalIndex = 0
        pageEntries.forEachIndexed { pageIdx, (k, v) ->
            val hasSubsequent = pageEntries.drop(pageIdx + 1).any { (nextKey, _) -> resJson.has(nextKey) }
            val aligned = extractAlignedPageTranslations(resJson, k, v.blocks.size, hasSubsequent)
            v.blocks.forEachIndexed { i, b ->
                val cachedTranslation = cached.getOrNull(globalIndex)
                val raw = aligned.getOrElse(i) { "" }.trim()
                val postEdited = if (raw.isNotBlank()) postEditMachineTranslation(b.text, raw, toLang) else ""
                val finalTranslation = when {
                    !cachedTranslation.isNullOrBlank() -> TranslationGlossary.apply(b.text, cachedTranslation, context)
                    postEdited.isNotBlank() -> TranslationGlossary.resolve(preparedTexts[globalIndex], postEdited, context)
                    else -> ""
                }

                if (finalTranslation.contains("RTMTH", ignoreCase = true) ||
                    TranslationRecoveryPolicy.isWatermarkOrCredit(b.text) ||
                    TranslationRecoveryPolicy.isWatermarkOrCredit(finalTranslation)
                ) {
                    b.translation = ""
                } else {
                    b.translation = finalTranslation
                }

                if (b.translation.isNotBlank() && cachedTranslation == null) {
                    TranslationCache.put(context, fromLang, toLang, normalizedTexts[globalIndex], b.translation)
                }
                if (b.translation.isNotBlank()) {
                    remembered += b.text to b.translation
                }
                globalIndex++
            }
        }
        return remembered
    }

    override fun close() { continuity.clear() }
}

internal inline fun executeGeminiRequestWithFallback(
    systemInstruction: String,
    requestText: String,
    maxOutputTokens: Int,
    modelName: String,
    temperature: Float?,
    responseMimeType: String?,
    execute: (String) -> Pair<Int, String>,
): Pair<Int, String> {
    var includeThinkingConfig = true
    var activeResponseMimeType = responseMimeType
    var activeTemperature = temperature
    var compatibilityDowngrades = 0

    while (true) {
        val body = buildGeminiRestRequestBody(
            systemInstruction = systemInstruction,
            requestText = requestText,
            maxOutputTokens = maxOutputTokens,
            modelName = modelName,
            temperature = activeTemperature,
            responseMimeType = activeResponseMimeType,
            includeThinkingConfig = includeThinkingConfig,
        )
        val result = execute(body)
        val (statusCode, rawBody) = result
        if (statusCode != 400 || compatibilityDowngrades >= 3) {
            return result
        }
        val downgraded = resolveGeminiCompatibilityDowngrade(
            responseBody = rawBody,
            includeThinkingConfig = includeThinkingConfig,
            hasResponseMimeType = !activeResponseMimeType.isNullOrBlank(),
            hasTemperature = activeTemperature != null,
        ) ?: return result

        includeThinkingConfig = downgraded.includeThinkingConfig
        if (!downgraded.keepResponseMimeType) activeResponseMimeType = null
        if (!downgraded.keepTemperature) activeTemperature = null
        compatibilityDowngrades++
    }
}

internal data class GeminiCompatibilityDowngrade(
    val includeThinkingConfig: Boolean,
    val keepResponseMimeType: Boolean,
    val keepTemperature: Boolean,
)

internal fun resolveGeminiCompatibilityDowngrade(
    responseBody: String,
    includeThinkingConfig: Boolean,
    hasResponseMimeType: Boolean,
    hasTemperature: Boolean,
): GeminiCompatibilityDowngrade? {
    val lower = responseBody.lowercase()
    return when {
        includeThinkingConfig && (lower.contains("thinking") || lower.contains("thinkinglevel") || lower.contains("thinkingbudget")) ->
            GeminiCompatibilityDowngrade(
                includeThinkingConfig = false,
                keepResponseMimeType = hasResponseMimeType,
                keepTemperature = hasTemperature,
            )
        hasResponseMimeType && (lower.contains("responsemimetype") || lower.contains("response_mime_type") || lower.contains("mime")) ->
            GeminiCompatibilityDowngrade(
                includeThinkingConfig = includeThinkingConfig,
                keepResponseMimeType = false,
                keepTemperature = hasTemperature,
            )
        hasTemperature && lower.contains("temperature") ->
            GeminiCompatibilityDowngrade(
                includeThinkingConfig = includeThinkingConfig,
                keepResponseMimeType = hasResponseMimeType,
                keepTemperature = false,
            )
        includeThinkingConfig ->
            GeminiCompatibilityDowngrade(
                includeThinkingConfig = false,
                keepResponseMimeType = hasResponseMimeType,
                keepTemperature = hasTemperature,
            )
        hasResponseMimeType ->
            GeminiCompatibilityDowngrade(
                includeThinkingConfig = false,
                keepResponseMimeType = false,
                keepTemperature = hasTemperature,
            )
        hasTemperature ->
            GeminiCompatibilityDowngrade(
                includeThinkingConfig = false,
                keepResponseMimeType = false,
                keepTemperature = false,
            )
        else -> null
    }
}

internal fun extractGeminiResponseText(responseJson: JSONObject): String {
    val parts = responseJson.optJSONArray("candidates")
        ?.optJSONObject(0)
        ?.optJSONObject("content")
        ?.optJSONArray("parts")
        ?: return ""

    val nonThoughtText = buildString {
        for (index in 0 until parts.length()) {
            val part = parts.optJSONObject(index) ?: continue
            if (!part.optBoolean("thought", false)) {
                append(part.optString("text"))
            }
        }
    }
    if (nonThoughtText.isNotBlank()) return nonThoughtText

    return buildString {
        for (index in 0 until parts.length()) {
            append(parts.optJSONObject(index)?.optString("text").orEmpty())
        }
    }
}

internal fun <T> chunkGeminiItems(items: List<T>, maxItems: Int = GEMINI_MAX_ITEMS_PER_REQUEST): List<List<T>> {
    require(maxItems > 0)
    return items.chunked(maxItems)
}

internal fun buildGeminiRestRequestBody(
    systemInstruction: String,
    requestText: String,
    maxOutputTokens: Int,
    modelName: String = "",
    temperature: Float? = null,
    responseMimeType: String? = null,
    includeThinkingConfig: Boolean = false,
): String = buildJsonObject {
    if (systemInstruction.isNotBlank()) {
        put("systemInstruction", buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", systemInstruction) }) }) })
    }
    put(
        "contents",
        buildJsonArray {
            add(
                buildJsonObject {
                    put("role", "user")
                    put(
                        "parts",
                        buildJsonArray {
                            add(buildJsonObject { put("text", requestText) })
                        },
                    )
                },
            )
        },
    )
    put(
        "generationConfig",
        buildJsonObject {
            put("maxOutputTokens", maxOutputTokens)
            temperature?.let { put("temperature", it.coerceIn(0f, 1f)) }
            responseMimeType?.takeIf { it.isNotBlank() }?.let { put("responseMimeType", it) }
            if (includeThinkingConfig) {
                applyGeminiThinkingControls(modelName)
            }
        },
    )
    put("safetySettings", buildJsonArray {
        add(buildJsonObject { put("category", "HARM_CATEGORY_HARASSMENT"); put("threshold", "BLOCK_NONE") })
        add(buildJsonObject { put("category", "HARM_CATEGORY_HATE_SPEECH"); put("threshold", "BLOCK_NONE") })
        add(buildJsonObject { put("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT"); put("threshold", "BLOCK_NONE") })
        add(buildJsonObject { put("category", "HARM_CATEGORY_DANGEROUS_CONTENT"); put("threshold", "BLOCK_NONE") })
    })
}.toString()

internal fun kotlinx.serialization.json.JsonObjectBuilder.applyGeminiThinkingControls(modelName: String) {
    val normalized = modelName.trim().removePrefix("models/").lowercase()
    if (normalized.isBlank()) return
    when {
        normalized.contains("gemini-3") -> {
            putJsonObject("thinkingConfig") {
                val level = if (normalized.contains("-pro")) "low" else "minimal"
                put("thinkingLevel", level)
            }
        }
        normalized.contains("gemini-2.5") ||
            normalized.contains("flash-latest") ||
            normalized.contains("pro-latest") -> {
            putJsonObject("thinkingConfig") {
                val budget = if (normalized.contains("-pro")) 128 else 0
                put("thinkingBudget", budget)
            }
        }
    }
}

internal fun buildGeminiRestUrl(modelName: String): String {
    val normalizedModel = modelName.trim().removePrefix("models/")
    return "https://generativelanguage.googleapis.com/v1beta/models/$normalizedModel:generateContent"
}

internal fun buildGeminiHttpDiagnostic(statusCode: Int, responseBody: String, apiKey: String): String {
    val parsed = runCatching {
        val error = JSONObject(responseBody).optJSONObject("error")
        listOf(error?.optString("status").orEmpty(), error?.optString("message").orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(": ")
    }.getOrDefault("")
    var detail = parsed.ifBlank { responseBody }.replace(apiKey, "[REDACTED]")
    detail = detail.replace(GEMINI_API_KEY_PATTERN, "[REDACTED]").replace(Regex("\\s+"), " ").trim().take(320)
    return if (detail.isBlank()) "Gemini HTTP $statusCode" else "Gemini HTTP $statusCode: $detail"
}

internal fun buildGeminiNetworkDiagnostic(error: Throwable, apiKey: String): String = buildGeminiDiagnostic("Gemini diagnóstico de rede", error, apiKey, true)
internal fun buildGeminiUnknownDiagnostic(error: Throwable, apiKey: String): String = buildGeminiDiagnostic("Gemini diagnóstico", error, apiKey, false)

private fun buildGeminiDiagnostic(prefix: String, error: Throwable, apiKey: String, preferIOException: Boolean): String {
    val causes = generateSequence(error) { it.cause }.toList()
    val detail = if (preferIOException) causes.firstOrNull { it is IOException } ?: causes.lastOrNull() ?: error else causes.lastOrNull() ?: error
    val type = detail::class.java.simpleName.ifBlank { "Throwable" }
    var detailMessage = detail.message.orEmpty()
    if (apiKey.isNotBlank()) detailMessage = detailMessage.replace(apiKey, "[REDACTED]")
    detailMessage = detailMessage.replace(GEMINI_API_KEY_PATTERN, "[REDACTED]").trim().take(220)
    return if (detailMessage.isBlank()) "$prefix: $type" else "$prefix: $type: $detailMessage"
}

private const val GEMINI_MAX_PAGES_PER_CHUNK = 16
private const val GEMINI_MAX_BLOCKS_PER_CHUNK = 75
private const val GEMINI_CONCURRENT_CHUNKS = 2
private const val GEMINI_MAX_ITEMS_PER_REQUEST = 16
private const val JSON_MIME_TYPE = "application/json"
private val GEMINI_API_KEY_PATTERN = Regex("AIza[0-9A-Za-z_-]{10,}")
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
