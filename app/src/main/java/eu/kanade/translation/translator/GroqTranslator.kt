package eu.kanade.translation.translator

import eu.kanade.tachiyomi.network.await
import eu.kanade.translation.context.TranslationContext
import eu.kanade.translation.memory.PreparedGlossaryText
import eu.kanade.translation.memory.TranslationCache
import eu.kanade.translation.memory.TranslationGlossary
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.TranslationRecoveryPolicy
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import logcat.LogPriority
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import tachiyomi.core.common.util.system.logcat
import java.io.IOException
import java.util.IdentityHashMap
import kotlin.math.roundToLong

class GroqTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
    val apiKey: String,
    val modelName: String,
    val maxOutputToken: Int,
    val temp: Float,
) : TextTranslator {
    private val okHttpClient by lazy { TranslationNetworkHelper.sharedClient }
    private val continuity = TranslationContext()
    private val cleanApiKey: String
        get() = sanitizeGroqApiKey(apiKey)

    override suspend fun translate(
        pages: MutableMap<String, PageTranslation>,
        context: ComicTranslationContext,
    ) {
        if (cleanApiKey.isBlank()) throw TranslationProviderFailureMapper.missingKey(TranslationProviderId.GROQ)
        if (modelName.isBlank()) throw TranslationProviderFailureMapper.missingModel(TranslationProviderId.GROQ)
        try {
            translateInternal(pages, context)
        } catch (error: CancellationException) {
            throw error
        } catch (error: TranslationProviderException) {
            logcat(LogPriority.ERROR, error) { "Groq translation failed: ${error.category}" }
            throw error
        } catch (error: Throwable) {
            val mapped = TranslationProviderFailureMapper.fromThrowable(TranslationProviderId.GROQ, error)
            logcat(LogPriority.ERROR, error) { "Groq translation failed: ${mapped.category}" }
            when (mapped.category) {
                TranslationFailureCategory.CONNECTION -> throw TranslationProviderException(
                    TranslationProviderId.GROQ,
                    TranslationFailureCategory.CONNECTION,
                    true,
                    buildGroqNetworkDiagnostic(error, cleanApiKey),
                    error,
                )
                TranslationFailureCategory.UNKNOWN -> throw TranslationProviderException(
                    TranslationProviderId.GROQ,
                    TranslationFailureCategory.UNKNOWN,
                    false,
                    buildGroqUnknownDiagnostic(error, cleanApiKey),
                    error,
                )
                else -> throw mapped
            }
        }
    }

    private suspend fun translateInternal(
        pages: MutableMap<String, PageTranslation>,
        context: ComicTranslationContext,
    ) {
        val chunkedPages = chunkPages(pages, maxPages = 8, maxBlocks = GROQ_MAX_BLOCKS_PER_CHUNK)
        for (chunk in chunkedPages) {
            translateChunkInternal(chunk, context)
        }
    }

    private suspend fun translateChunkInternal(
        pages: Map<String, PageTranslation>,
        context: ComicTranslationContext,
    ) {
        continuity.enter(context)
        val preparedChunk = prepareGroqChunk(
            pages = pages,
            context = context,
        ) { normalized ->
            TranslationCache.get(context, fromLang, toLang, normalized)
        }

        if (preparedChunk.uncachedPages.isEmpty()) {
            rememberChunkContinuity(pages)
            return
        }

        val uncachedPages = preparedChunk.uncachedPages
        val inputJson = buildComicInputJson(uncachedPages) { block ->
            preparedChunk.preparedByBlock[block]?.textForTranslation ?: normalizeOcrText(block.text)
        }
        val relevantGlossaryText = preparedChunk.uncachedNormalizedTexts.joinToString("\n") + "\n" + inputJson
        val visionAttachment = runCatching {
            context.visionAttachmentBuilder?.invoke(uncachedPages)
        }.onFailure { error ->
            if (error is CancellationException) throw error
            logcat(LogPriority.WARN, error) { "Failed to build Vision bubble montage for Groq; continuing text-only" }
        }.getOrNull()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val systemInstruction = buildComicTranslationPrompt(fromLang, toLang) +
            "\nIMPORTANT: Return ONLY a valid JSON object. No explanations, no markdown formatting."

        suspend fun executeSingleGroqCall(
            attachment: VisionMontageAttachment?,
            useJsonMode: Boolean,
            includeReasoningParams: Boolean,
        ): GroqHttpResponse {
            val requestText = continuity.promptPrefix() + buildComicTranslationRequest(
                context = context,
                inputJson = inputJson,
                relevantText = relevantGlossaryText,
                visionAttachment = attachment,
            )
            val jsonObject = buildGroqRequestBody(
                modelName = modelName,
                systemInstruction = systemInstruction,
                requestText = requestText,
                maxOutputTokens = maxOutputToken,
                temperature = temp,
                visionAttachment = attachment,
                useJsonMode = useJsonMode,
                includeReasoningParams = includeReasoningParams,
            )
            val request = Request.Builder()
                .url(GROQ_CHAT_COMPLETIONS_URL)
                .header("Authorization", "Bearer $cleanApiKey")
                .header("Content-Type", "application/json")
                .post(jsonObject.toRequestBody(mediaType))
                .build()
            return okHttpClient.newCall(request).await().use { response ->
                GroqHttpResponse(
                    statusCode = response.code,
                    body = response.body.string(),
                    retryAfterHeader = response.header("retry-after"),
                    resetTokensHeader = response.header("x-ratelimit-reset-tokens"),
                    resetRequestsHeader = response.header("x-ratelimit-reset-requests"),
                )
            }
        }

        suspend fun executeWithCompatibilityAndRateLimit(
            initialAttachment: VisionMontageAttachment?,
            initialUseJsonMode: Boolean = true,
            initialIncludeReasoningParams: Boolean = true,
        ): GroqHttpResponse {
            var currentAttachment = initialAttachment
            var useJsonMode = initialUseJsonMode
            var includeReasoningParams = initialIncludeReasoningParams
            var rateLimitRetries = 0
            var compatibilityDowngrades = 0

            while (true) {
                val response = try {
                    executeSingleGroqCall(currentAttachment, useJsonMode, includeReasoningParams)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: OutOfMemoryError) {
                    if (currentAttachment != null) {
                        logcat(LogPriority.WARN) { "OutOfMemoryError sending Groq vision bubble grid; retrying text-only" }
                        currentAttachment = null
                        continue
                    } else {
                        throw error
                    }
                }

                if (response.statusCode == 429 && rateLimitRetries < GROQ_MAX_RATE_LIMIT_RETRIES) {
                    if (!isGroqDailyQuotaExhausted(response.body)) {
                        val waitMs = parseGroqRateLimitWaitMs(
                            retryAfterHeader = response.retryAfterHeader,
                            resetTokensHeader = response.resetTokensHeader,
                            resetRequestsHeader = response.resetRequestsHeader,
                        ) ?: ((rateLimitRetries + 1) * GROQ_DEFAULT_RATE_LIMIT_WAIT_MS)
                        if (waitMs in 0..GROQ_MAX_AUTO_RETRY_DELAY_MS) {
                            rateLimitRetries++
                            delay(waitMs + GROQ_RETRY_BUFFER_MS)
                            continue
                        }
                    }
                }

                if ((response.statusCode == 400 || response.statusCode == 422) && compatibilityDowngrades < 3) {
                    val lower = response.body.lowercase()
                    when {
                        useJsonMode && (lower.contains("response_format") || lower.contains("json_object")) -> {
                            useJsonMode = false
                            compatibilityDowngrades++
                            continue
                        }
                        includeReasoningParams && lower.contains("reasoning") -> {
                            includeReasoningParams = false
                            compatibilityDowngrades++
                            continue
                        }
                        currentAttachment != null -> {
                            logcat(LogPriority.WARN) { "Groq model rejected multimodal bubble grid (HTTP ${response.statusCode}); retrying text-only" }
                            currentAttachment = null
                            compatibilityDowngrades++
                            continue
                        }
                        useJsonMode || includeReasoningParams -> {
                            useJsonMode = false
                            includeReasoningParams = false
                            compatibilityDowngrades++
                            continue
                        }
                    }
                }

                return response
            }
        }

        fun parseSuccessfulGroqBody(responseBody: String): JSONObject {
            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                val errorMsg = json.optJSONObject("error")?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: "Unknown Groq Error"
                val mappedFromBody = TranslationProviderFailureMapper.fromHttp(
                    provider = TranslationProviderId.GROQ,
                    statusCode = 400,
                    responseBody = responseBody,
                )
                if (mappedFromBody.category != TranslationFailureCategory.SERVER) {
                    throw mappedFromBody
                }
                throw TranslationProviderException(
                    TranslationProviderId.GROQ,
                    TranslationFailureCategory.SERVER,
                    false,
                    buildGroqHttpDiagnostic(200, responseBody, cleanApiKey, fallbackMessage = errorMsg),
                )
            }

            val choice = choices.getJSONObject(0)
            val finishReason = choice.optString("finish_reason").ifBlank { "unknown" }
            val rawContent = extractGroqMessageContent(choice)
            val cleanedContent = stripGroqThinkingTags(rawContent)
            if (cleanedContent.isBlank()) {
                throw TranslationProviderException(
                    TranslationProviderId.GROQ,
                    TranslationFailureCategory.MALFORMED_RESPONSE,
                    true,
                    "Groq retornou resposta vazia (finish_reason=$finishReason).",
                )
            }

            val parsed = runCatching { parseComicTranslationResponse(cleanedContent) }
                .getOrElse { parseError ->
                    val snippet = cleanedContent.take(80).replace('\n', ' ')
                    throw TranslationProviderException(
                        TranslationProviderId.GROQ,
                        TranslationFailureCategory.MALFORMED_RESPONSE,
                        true,
                        "Groq JSON inválido (finish_reason=$finishReason): $snippet",
                        parseError,
                    )
                }
            return unwrapGroqPageMapJson(parsed, uncachedPages.keys)
        }

        var response = executeWithCompatibilityAndRateLimit(visionAttachment)
        if (response.statusCode !in 200..299) {
            val mapped = TranslationProviderFailureMapper.fromHttp(
                provider = TranslationProviderId.GROQ,
                statusCode = response.statusCode,
                responseBody = response.body,
            )
            if (mapped.category == TranslationFailureCategory.SERVER && response.statusCode in 400..499) {
                throw TranslationProviderException(
                    TranslationProviderId.GROQ,
                    TranslationFailureCategory.SERVER,
                    false,
                    buildGroqHttpDiagnostic(response.statusCode, response.body, cleanApiKey),
                )
            }
            throw mapped
        }

        val resJson = try {
            parseSuccessfulGroqBody(response.body)
        } catch (firstParseFailure: TranslationProviderException) {
            if (!firstParseFailure.retryable) throw firstParseFailure
            logcat(LogPriority.WARN, firstParseFailure) {
                "Groq returned empty/unparseable JSON; retrying without strict json_object/reasoning params"
            }
            response = executeWithCompatibilityAndRateLimit(
                initialAttachment = null,
                initialUseJsonMode = false,
                initialIncludeReasoningParams = false,
            )
            if (response.statusCode !in 200..299) {
                throw TranslationProviderFailureMapper.fromHttp(
                    provider = TranslationProviderId.GROQ,
                    statusCode = response.statusCode,
                    responseBody = response.body,
                )
            }
            parseSuccessfulGroqBody(response.body)
        }

        val uncachedEntries = uncachedPages.entries.toList()
        uncachedEntries.forEachIndexed { pageIdx, (pageKey, uncachedPage) ->
            val hasSubsequent = uncachedEntries.drop(pageIdx + 1).any { (nextKey, _) -> resJson.has(nextKey) }
            val aligned = extractAlignedPageTranslations(resJson, pageKey, uncachedPage.blocks.size, hasSubsequent)
            uncachedPage.blocks.forEachIndexed { index, block ->
                val raw = aligned.getOrElse(index) { "" }.trim()
                val postEdited = if (raw.isNotBlank()) postEditMachineTranslation(block.text, raw, toLang) else ""
                val prepared = preparedChunk.preparedByBlock[block]
                val finalTranslation = when {
                    postEdited.isNotBlank() && prepared != null -> TranslationGlossary.resolve(
                        prepared = prepared,
                        translatedText = postEdited,
                        context = context,
                    )
                    postEdited.isNotBlank() -> postEdited
                    else -> ""
                }

                if (finalTranslation.contains("RTMTH", ignoreCase = true) ||
                    TranslationRecoveryPolicy.isWatermarkOrCredit(block.text) ||
                    TranslationRecoveryPolicy.isWatermarkOrCredit(finalTranslation)
                ) {
                    block.translation = ""
                } else {
                    block.translation = finalTranslation
                }

                if (block.translation.isNotBlank()) {
                    val normalized = preparedChunk.normalizedByBlock[block] ?: normalizeOcrText(block.text)
                    TranslationCache.put(context, fromLang, toLang, normalized, block.translation)
                }
            }
        }

        rememberChunkContinuity(pages)
    }

    private fun rememberChunkContinuity(pages: Map<String, PageTranslation>) {
        pages.values.forEach { page ->
            page.blocks.forEach { block ->
                if (block.translation.isNotBlank()) {
                    continuity.remember(block.text, block.translation)
                }
            }
        }
    }

    override fun close() {
        continuity.clear()
    }
}

private data class GroqHttpResponse(
    val statusCode: Int,
    val body: String,
    val retryAfterHeader: String?,
    val resetTokensHeader: String?,
    val resetRequestsHeader: String?,
)

internal data class GroqPreparedChunk(
    val uncachedPages: Map<String, PageTranslation>,
    val uncachedNormalizedTexts: List<String>,
    val preparedByBlock: IdentityHashMap<TranslationBlock, PreparedGlossaryText>,
    val normalizedByBlock: IdentityHashMap<TranslationBlock, String>,
)

internal fun prepareGroqChunk(
    pages: Map<String, PageTranslation>,
    context: ComicTranslationContext,
    applyCached: (String, String) -> String = { source, cached -> TranslationGlossary.apply(source, cached, context) },
    prepareText: (String) -> PreparedGlossaryText = { TranslationGlossary.prepare(it, context) },
    cacheLookup: (String) -> String?,
): GroqPreparedChunk {
    val uncachedPages = LinkedHashMap<String, PageTranslation>()
    val uncachedNormalizedTexts = ArrayList<String>()
    val preparedByBlock = IdentityHashMap<TranslationBlock, PreparedGlossaryText>()
    val normalizedByBlock = IdentityHashMap<TranslationBlock, String>()

    pages.forEach { (pageKey, page) ->
        val uncachedBlocks = mutableListOf<TranslationBlock>()
        page.blocks.forEach { block ->
            val normalized = normalizeOcrText(block.text)
            val cachedTranslation = cacheLookup(normalized)
            if (cachedTranslation != null) {
                if (cachedTranslation.contains("RTMTH", ignoreCase = true) ||
                    TranslationRecoveryPolicy.isWatermarkOrCredit(block.text) ||
                    TranslationRecoveryPolicy.isWatermarkOrCredit(cachedTranslation)
                ) {
                    block.translation = ""
                } else {
                    block.translation = applyCached(block.text, cachedTranslation)
                }
            } else {
                val prepared = prepareText(normalized)
                preparedByBlock[block] = prepared
                normalizedByBlock[block] = normalized
                uncachedNormalizedTexts += normalized
                uncachedBlocks += block
            }
        }
        if (uncachedBlocks.isNotEmpty()) {
            uncachedPages[pageKey] = PageTranslation(
                blocks = uncachedBlocks,
                imgWidth = page.imgWidth,
                imgHeight = page.imgHeight,
            )
        }
    }

    return GroqPreparedChunk(
        uncachedPages = uncachedPages,
        uncachedNormalizedTexts = uncachedNormalizedTexts,
        preparedByBlock = preparedByBlock,
        normalizedByBlock = normalizedByBlock,
    )
}

internal fun buildGroqRequestBody(
    modelName: String,
    systemInstruction: String,
    requestText: String,
    maxOutputTokens: Int,
    temperature: Float,
    visionAttachment: VisionMontageAttachment? = null,
    useJsonMode: Boolean = true,
    includeReasoningParams: Boolean = true,
): String = buildJsonObject {
    val trimmedModel = modelName.trim()
    val safeTemp = temperature.coerceIn(0.01f, 0.5f)
    put("model", trimmedModel)
    put("top_p", 0.5f)
    put("temperature", safeTemp)
    put("max_completion_tokens", maxOutputTokens.coerceIn(256, 8192))
    if (useJsonMode) {
        putJsonObject("response_format") {
            put("type", "json_object")
        }
    }
    if (includeReasoningParams) {
        applyGroqReasoningControls(trimmedModel)
    }
    putJsonArray("messages") {
        addJsonObject {
            put("role", "system")
            put("content", systemInstruction)
        }
        addJsonObject {
            put("role", "user")
            if (visionAttachment == null) {
                put("content", requestText)
            } else {
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "text")
                        put("text", requestText)
                    }
                    addJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") {
                            put("url", "data:${visionAttachment.mimeType};base64,${visionAttachment.base64Data}")
                        }
                    }
                }
            }
        }
    }
}.toString()

internal fun kotlinx.serialization.json.JsonObjectBuilder.applyGroqReasoningControls(modelName: String) {
    when {
        isGroqQwenReasoningModel(modelName) -> {
            put("reasoning_effort", "none")
        }
        isGroqGptOssModel(modelName) -> {
            put("reasoning_effort", "low")
            put("include_reasoning", false)
        }
    }
}

internal fun isGroqQwenReasoningModel(modelName: String): Boolean =
    modelName.contains("qwen3", ignoreCase = true)

internal fun isGroqGptOssModel(modelName: String): Boolean =
    modelName.contains("gpt-oss", ignoreCase = true)

internal fun sanitizeGroqApiKey(rawKey: String): String =
    rawKey.trim().filter { ch -> ch in '!'..'~' }

internal fun stripGroqThinkingTags(content: String): String {
    var cleaned = content.replace(THINK_TAG_REGEX, "")
    val closeIdx = cleaned.lastIndexOf("</think>", ignoreCase = true)
    if (closeIdx >= 0) {
        cleaned = cleaned.substring(closeIdx + "</think>".length)
    }
    cleaned = cleaned.replace(UNCLOSED_THINK_TAG_REGEX, "")
    return cleaned.trim()
}

internal fun extractGroqMessageContent(choice: JSONObject): String {
    val message = choice.optJSONObject("message") ?: return ""
    val rawContent = message.opt("content")
    return when {
        rawContent == null || rawContent === JSONObject.NULL -> ""
        rawContent is String -> rawContent
        else -> rawContent.toString()
    }
}

internal fun unwrapGroqPageMapJson(
    parsed: JSONObject,
    expectedPageKeys: Collection<String>,
): JSONObject {
    if (expectedPageKeys.isEmpty() || expectedPageKeys.any { parsed.has(it) }) {
        return parsed
    }
    val keys = parsed.keys()
    while (keys.hasNext()) {
        val wrapperKey = keys.next()
        val nested = parsed.optJSONObject(wrapperKey) ?: continue
        if (expectedPageKeys.any { nested.has(it) }) {
            return nested
        }
    }
    return parsed
}

internal fun buildGroqHttpDiagnostic(
    statusCode: Int,
    responseBody: String,
    apiKey: String,
    fallbackMessage: String = "",
): String {
    val parsed = runCatching {
        val error = JSONObject(responseBody).optJSONObject("error")
        listOf(error?.optString("type").orEmpty(), error?.optString("message").orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(": ")
    }.getOrDefault("")
    var detail = parsed.ifBlank { fallbackMessage }.ifBlank { responseBody }
    if (apiKey.isNotBlank()) detail = detail.replace(apiKey, "[REDACTED]")
    detail = detail.replace(GROQ_API_KEY_PATTERN, "[REDACTED]").replace(Regex("\\s+"), " ").trim().take(280)
    return if (detail.isBlank()) "Groq HTTP $statusCode" else "Groq HTTP $statusCode: $detail"
}

internal fun buildGroqNetworkDiagnostic(error: Throwable, apiKey: String): String =
    buildGroqDiagnostic("Groq diagnóstico de rede", error, apiKey, preferIOException = true)

internal fun buildGroqUnknownDiagnostic(error: Throwable, apiKey: String): String =
    buildGroqDiagnostic("Groq diagnóstico", error, apiKey, preferIOException = false)

private fun buildGroqDiagnostic(
    prefix: String,
    error: Throwable,
    apiKey: String,
    preferIOException: Boolean,
): String {
    val causes = generateSequence(error) { it.cause }.toList()
    val detail = if (preferIOException) {
        causes.firstOrNull { it is IOException } ?: causes.lastOrNull() ?: error
    } else {
        causes.lastOrNull() ?: error
    }
    val type = detail::class.java.simpleName.ifBlank { "Throwable" }
    var detailMessage = detail.message.orEmpty()
    if (apiKey.isNotBlank()) detailMessage = detailMessage.replace(apiKey, "[REDACTED]")
    detailMessage = detailMessage.replace(GROQ_API_KEY_PATTERN, "[REDACTED]").replace(Regex("\\s+"), " ").trim().take(220)
    return if (detailMessage.isBlank()) "$prefix: $type" else "$prefix: $type: $detailMessage"
}

internal fun parseGroqRateLimitWaitMs(
    retryAfterHeader: String?,
    resetTokensHeader: String?,
    resetRequestsHeader: String?,
): Long? {
    val retryAfterMs = parseGroqDurationHeaderMs(retryAfterHeader)
    val resetTokensMs = parseGroqDurationHeaderMs(resetTokensHeader)
    val resetRequestsMs = parseGroqDurationHeaderMs(resetRequestsHeader)
    return listOfNotNull(retryAfterMs, resetTokensMs, resetRequestsMs).maxOrNull()
}

internal fun parseGroqDurationHeaderMs(rawHeader: String?): Long? {
    val raw = rawHeader?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    raw.toDoubleOrNull()?.let { seconds ->
        if (seconds.isFinite() && seconds >= 0.0) {
            return (seconds * 1000.0).roundToLong()
        }
    }

    val matches = DURATION_SEGMENT_REGEX.findAll(raw.lowercase()).toList()
    if (matches.isEmpty()) return null
    var totalMs = 0.0
    for (match in matches) {
        val value = match.groupValues[1].toDoubleOrNull() ?: continue
        val multiplierMs = when (match.groupValues[2]) {
            "ms" -> 1.0
            "s" -> 1000.0
            "m" -> 60_000.0
            "h" -> 3_600_000.0
            else -> 0.0
        }
        totalMs += value * multiplierMs
    }
    return totalMs.roundToLong().coerceAtLeast(0L)
}

internal fun isGroqDailyQuotaExhausted(responseBody: String): Boolean {
    val lower = responseBody.lowercase()
    return lower.contains("tokens per day") ||
        lower.contains("requests per day") ||
        lower.contains("daily limit") ||
        lower.contains("tpd") && lower.contains("limit") ||
        lower.contains("rpd") && lower.contains("limit")
}

internal const val GROQ_CHAT_COMPLETIONS_URL = "https://api.groq.com/openai/v1/chat/completions"
private const val GROQ_MAX_BLOCKS_PER_CHUNK = 20
private const val GROQ_MAX_AUTO_RETRY_DELAY_MS = 35_000L
private const val GROQ_DEFAULT_RATE_LIMIT_WAIT_MS = 3_000L
private const val GROQ_RETRY_BUFFER_MS = 250L
private const val GROQ_MAX_RATE_LIMIT_RETRIES = 2
private val THINK_TAG_REGEX = Regex("(?is)<think>.*?</think>")
private val UNCLOSED_THINK_TAG_REGEX = Regex("(?is)<think>.*$")
private val GROQ_API_KEY_PATTERN = Regex("gsk_[0-9A-Za-z_-]{8,}")
private val DURATION_SEGMENT_REGEX = Regex("""(\d+(?:\.\d+)?)(ms|s|m|h)""")
