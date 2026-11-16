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
import kotlinx.serialization.json.add
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
        val expectedPageKeys = uncachedPages.keys.toList()
        val hasTranslatableBlocks = uncachedPages.values.flatMap { it.blocks }.any { block ->
            TranslationQualityGuard.shouldRetry(block.text, "", fromLang, toLang)
        }

        suspend fun executeSingleGroqCall(
            attachment: VisionMontageAttachment?,
            responseFormatMode: GroqResponseFormatMode,
            includeReasoningParams: Boolean,
            formatReminder: String = "",
        ): GroqHttpResponse {
            val requestText = continuity.promptPrefix() + buildComicTranslationRequest(
                context = context,
                inputJson = inputJson,
                relevantText = relevantGlossaryText,
                visionAttachment = attachment,
            ) + formatReminder
            val jsonObject = buildGroqRequestBody(
                modelName = modelName,
                systemInstruction = systemInstruction,
                requestText = requestText,
                maxOutputTokens = maxOutputToken,
                temperature = temp,
                visionAttachment = attachment,
                useJsonMode = responseFormatMode != GroqResponseFormatMode.TEXT,
                includeReasoningParams = includeReasoningParams,
                expectedPageKeys = expectedPageKeys,
                responseFormatMode = responseFormatMode,
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
            initialFormatMode: GroqResponseFormatMode = initialGroqResponseFormatMode(modelName),
            initialIncludeReasoningParams: Boolean = true,
            formatReminder: String = "",
        ): GroqHttpResponse {
            var currentAttachment = initialAttachment
            var responseFormatMode = initialFormatMode
            var includeReasoningParams = initialIncludeReasoningParams
            var rateLimitRetries = 0
            var compatibilityDowngrades = 0

            while (true) {
                val response = try {
                    executeSingleGroqCall(
                        attachment = currentAttachment,
                        responseFormatMode = responseFormatMode,
                        includeReasoningParams = includeReasoningParams,
                        formatReminder = formatReminder,
                    )
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

                if ((response.statusCode == 400 || response.statusCode == 422) && compatibilityDowngrades < 4) {
                    val lower = response.body.lowercase()
                    when {
                        includeReasoningParams && lower.contains("reasoning") -> {
                            includeReasoningParams = false
                            compatibilityDowngrades++
                            continue
                        }
                        responseFormatMode == GroqResponseFormatMode.JSON_SCHEMA &&
                            (lower.contains("json_schema") || lower.contains("schema") || lower.contains("response_format") || lower.contains("strict")) -> {
                            responseFormatMode = GroqResponseFormatMode.JSON_OBJECT
                            compatibilityDowngrades++
                            continue
                        }
                        responseFormatMode == GroqResponseFormatMode.JSON_OBJECT &&
                            (lower.contains("response_format") || lower.contains("json_object")) -> {
                            responseFormatMode = GroqResponseFormatMode.TEXT
                            compatibilityDowngrades++
                            continue
                        }
                        currentAttachment != null -> {
                            logcat(LogPriority.WARN) { "Groq model rejected multimodal bubble grid (HTTP ${response.statusCode}); retrying text-only" }
                            currentAttachment = null
                            compatibilityDowngrades++
                            continue
                        }
                        responseFormatMode == GroqResponseFormatMode.JSON_SCHEMA -> {
                            responseFormatMode = GroqResponseFormatMode.JSON_OBJECT
                            compatibilityDowngrades++
                            continue
                        }
                        responseFormatMode != GroqResponseFormatMode.TEXT || includeReasoningParams -> {
                            responseFormatMode = GroqResponseFormatMode.TEXT
                            includeReasoningParams = false
                            compatibilityDowngrades++
                            continue
                        }
                    }
                }

                return response
            }
        }

        fun checkHttpStatus(httpResponse: GroqHttpResponse) {
            if (httpResponse.statusCode !in 200..299) {
                val mapped = TranslationProviderFailureMapper.fromHttp(
                    provider = TranslationProviderId.GROQ,
                    statusCode = httpResponse.statusCode,
                    responseBody = httpResponse.body,
                )
                if (mapped.category == TranslationFailureCategory.SERVER && httpResponse.statusCode in 400..499) {
                    throw TranslationProviderException(
                        TranslationProviderId.GROQ,
                        TranslationFailureCategory.SERVER,
                        false,
                        buildGroqHttpDiagnostic(httpResponse.statusCode, httpResponse.body, cleanApiKey),
                    )
                }
                throw mapped
            }
        }

        var response = executeWithCompatibilityAndRateLimit(visionAttachment)
        checkHttpStatus(response)

        val resJson = try {
            parseGroqTranslationBody(
                responseBody = response.body,
                modelName = modelName,
                apiKey = cleanApiKey,
                expectedPageKeys = expectedPageKeys,
                hasTranslatableBlocks = hasTranslatableBlocks,
                isFinalAttempt = false,
            )
        } catch (firstParseFailure: TranslationProviderException) {
            if (!firstParseFailure.retryable) throw firstParseFailure
            logcat(LogPriority.WARN, firstParseFailure) {
                "Groq returned empty/unparseable JSON; retrying with explicit JSON-keys format reminder"
            }
            response = executeWithCompatibilityAndRateLimit(
                initialAttachment = null,
                initialFormatMode = GroqResponseFormatMode.JSON_OBJECT,
                initialIncludeReasoningParams = true,
                formatReminder = buildGroqFormatRetryReminder(expectedPageKeys),
            )
            checkHttpStatus(response)
            parseGroqTranslationBody(
                responseBody = response.body,
                modelName = modelName,
                apiKey = cleanApiKey,
                expectedPageKeys = expectedPageKeys,
                hasTranslatableBlocks = hasTranslatableBlocks,
                isFinalAttempt = true,
            )
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

internal enum class GroqResponseFormatMode {
    JSON_SCHEMA,
    JSON_OBJECT,
    TEXT,
}

internal fun supportsGroqStrictJsonSchema(modelName: String): Boolean {
    val lower = modelName.trim().lowercase()
    return lower.contains("qwen3.8") ||
        lower.contains("gpt-oss-120b") ||
        lower.contains("gpt-oss-20b") ||
        lower.contains("muse-spark-open")
}

internal fun initialGroqResponseFormatMode(modelName: String): GroqResponseFormatMode =
    if (supportsGroqStrictJsonSchema(modelName)) GroqResponseFormatMode.JSON_SCHEMA else GroqResponseFormatMode.JSON_OBJECT

internal fun buildGroqFormatRetryReminder(expectedKeys: Collection<String>): String {
    if (expectedKeys.isEmpty()) return ""
    val keysHint = expectedKeys.joinToString(", ") { "\"$it\"" }
    return "\n\nCRITICAL FORMAT REQUIREMENT: Your previous reply was empty or invalid. Return ONLY a single valid JSON object containing EVERY one of these keys: $keysHint. Map each key to an array of translated strings (use \"\" inside the array for watermarks/credits). NEVER output bare \"\" or {}."
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
    expectedPageKeys: Collection<String> = emptyList(),
    responseFormatMode: GroqResponseFormatMode = if (useJsonMode) GroqResponseFormatMode.JSON_OBJECT else GroqResponseFormatMode.TEXT,
): String = buildJsonObject {
    val trimmedModel = modelName.trim()
    val safeTemp = temperature.coerceIn(0.01f, 0.5f)
    put("model", trimmedModel)
    put("temperature", safeTemp)
    put("max_completion_tokens", maxOutputTokens.coerceIn(256, 8192))
    when (responseFormatMode) {
        GroqResponseFormatMode.JSON_SCHEMA -> if (expectedPageKeys.isNotEmpty()) {
            putJsonObject("response_format") {
                put("type", "json_schema")
                putJsonObject("json_schema") {
                    put("name", "comic_page_translations")
                    put("strict", true)
                    putJsonObject("schema") {
                        put("type", "object")
                        putJsonObject("properties") {
                            expectedPageKeys.forEach { pageKey ->
                                putJsonObject(pageKey) {
                                    put("type", "array")
                                    putJsonObject("items") {
                                        put("type", "string")
                                    }
                                }
                            }
                        }
                        putJsonArray("required") {
                            expectedPageKeys.forEach { add(it) }
                        }
                        put("additionalProperties", false)
                    }
                }
            }
        } else {
            putJsonObject("response_format") {
                put("type", "json_object")
            }
        }
        GroqResponseFormatMode.JSON_OBJECT -> {
            putJsonObject("response_format") {
                put("type", "json_object")
            }
        }
        GroqResponseFormatMode.TEXT -> Unit
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

internal fun parseGroqTranslationBody(
    responseBody: String,
    modelName: String,
    apiKey: String,
    expectedPageKeys: Collection<String>,
    hasTranslatableBlocks: Boolean,
    isFinalAttempt: Boolean,
): JSONObject {
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
            buildGroqHttpDiagnostic(200, responseBody, apiKey, fallbackMessage = errorMsg),
        )
    }

    val choice = choices.getJSONObject(0)
    val refusal = extractGroqRefusal(choice)
    if (refusal.isNotBlank()) {
        val snippet = refusal.take(120).replace('\n', ' ')
        throw TranslationProviderException(
            TranslationProviderId.GROQ,
            TranslationFailureCategory.MALFORMED_RESPONSE,
            false,
            "Groq ($modelName) recusou este trecho ($snippet). Tente outro modelo em Modelo de LLM (ex.: openai/gpt-oss-120b).",
        )
    }

    val finishReason = choice.optString("finish_reason").ifBlank { "unknown" }
    val rawContent = extractGroqMessageContent(choice)
    val cleanedContent = stripGroqThinkingTags(rawContent)

    fun handleEmptyOrMissingPages(rawSnippet: String, cause: Throwable? = null): JSONObject {
        if (isFinalAttempt && !hasTranslatableBlocks) {
            return JSONObject()
        }
        val retryable = !isFinalAttempt
        val message = if (cleanedContent.isBlank() || cleanedContent == "\"\"" || cleanedContent == "{}") {
            if (isFinalAttempt) {
                "Groq ($modelName) retornou resposta vazia ($rawSnippet, finish_reason=$finishReason). Se este capítulo tiver conteúdo sensível ou recusado pelo modelo, tente outro modelo em Modelo de LLM (ex.: openai/gpt-oss-120b)."
            } else {
                "Groq retornou resposta vazia ($rawSnippet, finish_reason=$finishReason)."
            }
        } else {
            "Groq JSON inválido (finish_reason=$finishReason): $rawSnippet"
        }
        throw TranslationProviderException(
            TranslationProviderId.GROQ,
            TranslationFailureCategory.MALFORMED_RESPONSE,
            retryable,
            message,
            cause,
        )
    }

    if (cleanedContent.isBlank() || cleanedContent == "\"\"" || cleanedContent == "{}") {
        return handleEmptyOrMissingPages(cleanedContent.ifBlank { "\"\"" })
    }

    val parsed = runCatching { parseComicTranslationResponse(cleanedContent) }
        .getOrElse { parseError ->
            val snippet = cleanedContent.take(80).replace('\n', ' ')
            return handleEmptyOrMissingPages(snippet, parseError)
        }
    val unwrapped = unwrapGroqPageMapJson(parsed, expectedPageKeys)
    if (expectedPageKeys.isNotEmpty() && expectedPageKeys.none { unwrapped.has(it) }) {
        val snippet = cleanedContent.take(80).replace('\n', ' ')
        return handleEmptyOrMissingPages(snippet)
    }
    return unwrapped
}

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

internal fun extractGroqRefusal(choice: JSONObject): String {
    val message = choice.optJSONObject("message") ?: return ""
    val refusal = message.opt("refusal")
    return when {
        refusal == null || refusal === JSONObject.NULL -> ""
        refusal is String -> refusal.trim()
        else -> refusal.toString().trim()
    }
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
