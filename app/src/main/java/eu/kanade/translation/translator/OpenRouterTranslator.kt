package eu.kanade.translation.translator

import eu.kanade.tachiyomi.network.await
import eu.kanade.translation.context.TranslationContext
import eu.kanade.translation.memory.TranslationCache
import eu.kanade.translation.memory.TranslationGlossary
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationRecoveryPolicy
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import logcat.logcat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class OpenRouterTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
    val apiKey: String,
    val modelName: String,
    val maxOutputToken: Int,
    val temp: Float,
) : TextTranslator {
    private val okHttpClient by lazy { TranslationNetworkHelper.sharedClient }
    private val continuity = TranslationContext()

    override suspend fun translate(
        pages: MutableMap<String, PageTranslation>,
        context: ComicTranslationContext,
    ) {
        if (apiKey.isBlank()) throw TranslationProviderFailureMapper.missingKey(TranslationProviderId.OPENROUTER)
        if (modelName.isBlank()) throw TranslationProviderFailureMapper.missingModel(TranslationProviderId.OPENROUTER)
        try {
            translateInternal(pages, context)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val mapped = TranslationProviderFailureMapper.fromThrowable(TranslationProviderId.OPENROUTER, error)
            logcat { "OpenRouter translation failed: ${mapped.category}" }
            throw mapped
        }
    }

    private suspend fun translateInternal(
        pages: MutableMap<String, PageTranslation>,
        context: ComicTranslationContext,
    ) {
        val chunkedPages = chunkPages(pages, maxPages = 8, maxBlocks = 30)
        for (chunk in chunkedPages) {
            translateChunkInternal(chunk, context)
        }
    }

    private suspend fun translateChunkInternal(
        pages: Map<String, PageTranslation>,
        context: ComicTranslationContext,
    ) {
        try {
            continuity.enter(context)
            val blocks = pages.values.flatMap { it.blocks }
            if (blocks.isEmpty()) return
            val normalizedTexts = blocks.map { normalizeOcrText(it.text) }
            val preparedTexts = normalizedTexts.map { TranslationGlossary.prepare(it, context) }
            val cached = normalizedTexts.map { TranslationCache.get(context, fromLang, toLang, it) }

            if (cached.all { it != null }) {
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
                        continuity.remember(block.text, block.translation)
                    }
                }
                return
            }

            val preparedIterator = preparedTexts.iterator()
            val inputJson = buildComicInputJson(pages) {
                preparedIterator.next().textForTranslation
            }
            val relevantGlossaryText = normalizedTexts.joinToString("\n") + "\n" + inputJson
            val visionAttachment = runCatching {
                context.visionAttachmentBuilder?.invoke(pages)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                logcat { "Failed to build Vision bubble montage for OpenRouter; continuing text-only" }
            }.getOrNull()

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val systemInstruction = buildComicTranslationPrompt(fromLang, toLang) +
                "\nIMPORTANT: Return ONLY a valid JSON object. No explanations, no markdown formatting."

            suspend fun executeOpenRouterRequest(attachment: VisionMontageAttachment?): Pair<Int, String> {
                val requestText = continuity.promptPrefix() + buildComicTranslationRequest(
                    context = context,
                    inputJson = inputJson,
                    relevantText = relevantGlossaryText,
                    visionAttachment = attachment,
                )
                val jsonObject = buildOpenRouterRequestBody(
                    modelName = modelName,
                    systemInstruction = systemInstruction,
                    requestText = requestText,
                    maxOutputTokens = maxOutputToken,
                    temperature = temp,
                    visionAttachment = attachment,
                )
                val body = jsonObject.toRequestBody(mediaType)
                val request = Request.Builder()
                    .url("https://openrouter.ai/api/v1/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build()
                return okHttpClient.newCall(request).await().use { it.code to it.body.string() }
            }

            var (statusCode, rawBody) = try {
                executeOpenRouterRequest(visionAttachment)
            } catch (error: CancellationException) {
                throw error
            } catch (error: OutOfMemoryError) {
                if (visionAttachment != null) {
                    logcat { "OutOfMemoryError sending OpenRouter vision bubble grid; retrying text-only" }
                    executeOpenRouterRequest(null)
                } else {
                    throw error
                }
            }
            if (visionAttachment != null && (statusCode == 400 || statusCode == 422)) {
                logcat { "OpenRouter model rejected multimodal bubble grid (HTTP $statusCode); retrying text-only" }
                val fallbackResult = executeOpenRouterRequest(null)
                statusCode = fallbackResult.first
                rawBody = fallbackResult.second
            }
            if (statusCode !in 200..299) {
                throw TranslationProviderFailureMapper.fromHttp(
                    provider = TranslationProviderId.OPENROUTER,
                    statusCode = statusCode,
                    responseBody = rawBody,
                )
            }
            val responseBody = rawBody
            val json = JSONObject(responseBody)
            
            val choices = json.optJSONArray("choices")
            if (choices == null) {
                val errorMsg = json.optJSONObject("error")?.optString("message") ?: "Unknown OpenRouter Error"
                throw Exception("OpenRouter returned error: $errorMsg")
            }
            
            val resJson = parseComicTranslationResponse(
                choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content"),
            )

            val pageEntries = pages.entries.toList()
            var globalIndex = 0
            pageEntries.forEachIndexed { pageIdx, (pageKey, page) ->
                val hasSubsequent = pageEntries.drop(pageIdx + 1).any { (nextKey, _) -> resJson.has(nextKey) }
                val aligned = extractAlignedPageTranslations(resJson, pageKey, page.blocks.size, hasSubsequent)
                page.blocks.forEachIndexed { index, block ->
                    val cachedTranslation = cached.getOrNull(globalIndex)
                    val raw = aligned.getOrElse(index) { "" }.trim()
                    val postEdited = if (raw.isNotBlank()) postEditMachineTranslation(block.text, raw, toLang) else ""
                    val finalTranslation = when {
                        !cachedTranslation.isNullOrBlank() -> TranslationGlossary.apply(block.text, cachedTranslation, context)
                        postEdited.isNotBlank() -> TranslationGlossary.resolve(
                            prepared = preparedTexts[globalIndex],
                            translatedText = postEdited,
                            context = context,
                        )
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

                    if (block.translation.isNotBlank() && cachedTranslation == null) {
                        TranslationCache.put(context, fromLang, toLang, normalizedTexts[globalIndex], block.translation)
                    }
                    if (block.translation.isNotBlank()) {
                        continuity.remember(block.text, block.translation)
                    }
                    globalIndex++
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw error
        }
    }

    override fun close() {
        continuity.clear()
    }
}

internal fun buildOpenRouterRequestBody(
    modelName: String,
    systemInstruction: String,
    requestText: String,
    maxOutputTokens: Int,
    temperature: Float,
    visionAttachment: VisionMontageAttachment? = null,
): String = buildJsonObject {
    put("model", modelName)
    put("top_p", 0.5f)
    put("top_k", 30)
    put("temperature", temperature)
    put("max_tokens", maxOutputTokens.coerceAtMost(4096))
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
