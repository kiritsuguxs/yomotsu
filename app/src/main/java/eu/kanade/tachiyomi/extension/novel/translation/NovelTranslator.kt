package eu.kanade.tachiyomi.extension.novel.translation

import android.app.Application
import eu.kanade.translation.memory.TranslationGlossary
import eu.kanade.translation.translator.ComicTranslationContext
import eu.kanade.translation.translator.TextTranslatorLanguage
import eu.kanade.translation.translator.TextTranslators
import eu.kanade.translation.translator.TranslationNetworkHelper
import eu.kanade.translation.translator.executeGeminiStreamWithFallback
import eu.kanade.translation.translator.toGeminiHttpResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.translation.TranslationLlmProvider
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

object NovelTranslator {

    private const val GEMINI_NOVEL_CONCURRENT_CHUNKS = 3
    private const val MAX_MEMORY_CACHE_ENTRIES = 64
    private val client: OkHttpClient by lazy { TranslationNetworkHelper.sharedClient }
    private val translationCache: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(MAX_MEMORY_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
                return size > MAX_MEMORY_CACHE_ENTRIES
            }
        },
    )
    private val translationPreferences: TranslationPreferences by lazy { Injekt.get() }
    private val context: Application by lazy { Injekt.get() }
    private val cacheDir by lazy { File(context.cacheDir, "novel_translations").apply { mkdirs() } }

    private fun currentEngine(): TextTranslators {
        val engineIndex = runCatching { translationPreferences.novelTranslationEngine().get() }
            .getOrDefault(TextTranslators.GOOGLE.ordinal)
        return TextTranslators.entries.getOrNull(engineIndex) ?: TextTranslators.GOOGLE
    }

    private fun buildCacheKey(
        chapterId: Long,
        targetLang: String = "",
        engine: TextTranslators = currentEngine(),
        seriesTitle: String = "",
    ): String {
        val resolvedTargetLang = targetLang.ifBlank { resolveTargetLanguageCode("pt") }
        val glossaryHash = runCatching {
            if (seriesTitle.isBlank()) {
                0
            } else {
                val ctx = ComicTranslationContext(mangaTitle = seriesTitle, chapterName = "")
                TranslationGlossary.promptRules(ctx).hashCode()
            }
        }.getOrDefault(0)
        val safeLang = resolvedTargetLang.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return "${chapterId}_${safeLang}_${engine.name}_g$glossaryHash"
    }

    fun getCached(
        chapterId: Long,
        targetLang: String = "",
        seriesTitle: String = "",
        originalText: String? = null,
    ): String? {
        val scopedKey = buildCacheKey(chapterId, targetLang = targetLang, seriesTitle = seriesTitle)
        translationCache[scopedKey]?.let { cached ->
            if (originalText == null || cached.trim() != originalText.trim()) {
                return cached
            }
        }
        val scopedFile = File(cacheDir, "$scopedKey.txt")
        if (scopedFile.exists()) {
            val text = scopedFile.readText()
            if (isInvalidCachedContent(text, originalText)) {
                scopedFile.delete()
                return null
            }
            translationCache[scopedKey] = text
            return text
        }
        // Backward-compatible read of pre-v0.22.3 unscoped cache file if not poisoned
        val legacyFile = File(cacheDir, "$chapterId.txt")
        if (legacyFile.exists()) {
            val text = legacyFile.readText()
            if (isInvalidCachedContent(text, originalText)) {
                legacyFile.delete()
                return null
            }
            translationCache[scopedKey] = text
            return text
        }
        return null
    }

    private fun isInvalidCachedContent(cached: String, originalText: String?): Boolean {
        if (cached.isBlank()) return true
        if (cached.contains("<script", ignoreCase = true) || cached.contains("<style", ignoreCase = true)) {
            return true
        }
        if (!originalText.isNullOrBlank() && originalText.length > 40 && cached.trim() == originalText.trim()) {
            return true
        }
        return false
    }

    fun clearCached(chapterId: Long) {
        val prefix = "${chapterId}_"
        synchronized(translationCache) {
            val keysToRemove = translationCache.keys.filter { it == chapterId.toString() || it.startsWith(prefix) }
            keysToRemove.forEach(translationCache::remove)
        }
        runCatching {
            File(cacheDir, "$chapterId.txt").takeIf { it.exists() }?.delete()
            cacheDir.listFiles()?.forEach { f ->
                if (f.name.startsWith(prefix) && f.extension == "txt") {
                    f.delete()
                }
            }
        }
    }

    private fun resolveTargetLanguageCode(defaultLang: String): String {
        val prefVal = runCatching { translationPreferences.translateToLanguage().get() }.getOrNull()
        if (prefVal.isNullOrBlank()) return defaultLang
        val langEnum = runCatching { TextTranslatorLanguage.valueOf(prefVal) }.getOrNull()
        return langEnum?.code ?: defaultLang
    }

    suspend fun translate(
        chapterId: Long,
        text: String,
        sourceLang: String = "auto",
        targetLang: String = "",
        seriesTitle: String = "",
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text

        val resolvedTargetLang = targetLang.ifBlank { resolveTargetLanguageCode("pt") }
        val selectedEngine = currentEngine()
        val cacheKey = buildCacheKey(chapterId, resolvedTargetLang, selectedEngine, seriesTitle)

        getCached(
            chapterId = chapterId,
            targetLang = resolvedTargetLang,
            seriesTitle = seriesTitle,
            originalText = text,
        )?.let { return@withContext it }

        val glossaryContext = seriesTitle.takeIf { it.isNotBlank() }?.let {
            ComicTranslationContext(mangaTitle = it, chapterName = "")
        }

        val maxChunkLen = when (selectedEngine) {
            TextTranslators.GEMINI -> 7500
            TextTranslators.GROQ -> 3200
            else -> 2800
        }
        val chunks = chunkText(text, maxChunkLen)
        val hadFailure = AtomicBoolean(false)
        val translatedChunks = if (selectedEngine == TextTranslators.GEMINI && chunks.size > 1) {
            val semaphore = Semaphore(GEMINI_NOVEL_CONCURRENT_CHUNKS)
            coroutineScope {
                chunks.map { chunk ->
                    async(Dispatchers.IO) {
                        if (chunk.isBlank()) {
                            ""
                        } else {
                            semaphore.withPermit {
                                val translated = translateChunkWithGlossary(
                                    chunk = chunk,
                                    sourceLang = sourceLang,
                                    targetLang = resolvedTargetLang,
                                    engine = selectedEngine,
                                    glossaryContext = glossaryContext,
                                    previousContext = null,
                                )
                                if (translated == null) {
                                    hadFailure.set(true)
                                    chunk
                                } else {
                                    translated
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
        } else {
            var previousChunkContext: String? = null
            chunks.map { chunk ->
                val translated = if (chunk.isBlank()) {
                    ""
                } else {
                    val chunkRes = translateChunkWithGlossary(
                        chunk = chunk,
                        sourceLang = sourceLang,
                        targetLang = resolvedTargetLang,
                        engine = selectedEngine,
                        glossaryContext = glossaryContext,
                        previousContext = previousChunkContext,
                    )
                    if (chunkRes == null) {
                        hadFailure.set(true)
                        chunk
                    } else {
                        chunkRes
                    }
                }
                if (chunk.isNotBlank()) {
                    previousChunkContext = chunk.trim().takeLast(1000)
                }
                translated
            }
        }
        val result = translatedChunks.joinToString("\n\n")

        // Only persist to memory/disk cache when every non-blank chunk actually succeeded
        if (!hadFailure.get() && result.isNotBlank()) {
            translationCache[cacheKey] = result
            try {
                File(cacheDir, "$cacheKey.txt").writeText(result)
            } catch (e: Exception) {
                logcat(throwable = e) { "Failed to write translation cache" }
            }
        }
        result
    }

    private suspend fun translateChunkWithGlossary(
        chunk: String,
        sourceLang: String,
        targetLang: String,
        engine: TextTranslators,
        glossaryContext: ComicTranslationContext?,
        previousContext: String?,
    ): String? {
        return if (glossaryContext != null) {
            val prepared = TranslationGlossary.prepare(chunk, glossaryContext)
            val rawTranslated = translateChunk(
                text = prepared.textForTranslation,
                sl = sourceLang,
                tl = targetLang,
                engine = engine,
                previousContext = previousContext,
            ) ?: return null
            TranslationGlossary.resolve(prepared, rawTranslated, glossaryContext)
        } else {
            translateChunk(
                text = chunk,
                sl = sourceLang,
                tl = targetLang,
                engine = engine,
                previousContext = previousContext,
            )
        }
    }

    internal fun chunkText(text: String, maxLength: Int): List<String> {
        val rawParagraphs = text.split("\n\n")
        val normalizedSegments = mutableListOf<String>()
        val sentenceSplitChars = charArrayOf('.', '!', '?', '。', '！', '？', ';', '；', ' ')

        for (p in rawParagraphs) {
            if (p.length <= maxLength) {
                normalizedSegments.add(p)
            } else {
                val lines = p.split("\n")
                var lineGroup = StringBuilder()
                for (line in lines) {
                    if (line.length > maxLength) {
                        if (lineGroup.isNotEmpty()) {
                            normalizedSegments.add(lineGroup.toString().trim())
                            lineGroup = StringBuilder()
                        }
                        var remaining = line
                        while (remaining.length > maxLength) {
                            val cut = remaining.take(maxLength).lastIndexOfAny(sentenceSplitChars)
                                .takeIf { it >= maxLength / 3 }
                                ?: maxLength
                            normalizedSegments.add(remaining.substring(0, cut).trim())
                            remaining = remaining.substring(cut).trimStart()
                        }
                        if (remaining.isNotEmpty()) {
                            lineGroup.append(remaining).append("\n")
                        }
                    } else {
                        if (lineGroup.length + line.length + 1 > maxLength && lineGroup.isNotEmpty()) {
                            normalizedSegments.add(lineGroup.toString().trim())
                            lineGroup = StringBuilder()
                        }
                        lineGroup.append(line).append("\n")
                    }
                }
                if (lineGroup.isNotEmpty()) {
                    normalizedSegments.add(lineGroup.toString().trim())
                }
            }
        }

        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        for (segment in normalizedSegments) {
            if (currentChunk.length + segment.length + 2 > maxLength && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString().trim())
                currentChunk = StringBuilder()
            }
            currentChunk.append(segment).append("\n\n")
        }
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }
        return chunks
    }

    private suspend fun translateChunk(
        text: String,
        sl: String,
        tl: String,
        engine: TextTranslators = TextTranslators.GOOGLE,
        previousContext: String? = null,
    ): String? {
        return when (engine) {
            TextTranslators.GEMINI -> translateWithGemini(text, tl) ?: translateWithGoogle(text, sl, tl)
            TextTranslators.GROQ -> translateWithGroq(text, tl) ?: translateWithGoogle(text, sl, tl)
            TextTranslators.DEEPL -> translateWithDeepL(text, sl, tl, previousContext) ?: translateWithGoogle(text, sl, tl)
            else -> translateWithGoogle(text, sl, tl)
        }
    }

    private fun translateWithGoogle(text: String, sl: String, tl: String): String? {
        return try {
            val url = "https://translate.google.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t"
            val formBody = FormBody.Builder()
                .add("q", text)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val responseBody = response.body?.string() ?: return null
                val jsonArray = JSONArray(responseBody)
                val sentences = jsonArray.optJSONArray(0) ?: return null

                buildString {
                    for (i in 0 until sentences.length()) {
                        val sentence = sentences.optJSONArray(i)
                        if (sentence != null) {
                            append(sentence.optString(0, ""))
                        }
                    }
                }.takeIf { it.isNotBlank() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(throwable = e) { "Failed to translate chunk with Google" }
            null
        }
    }

    private fun targetLanguagePromptName(tl: String): String {
        return when {
            tl.startsWith("pt", ignoreCase = true) -> "Portuguese (Brazil)"
            tl.startsWith("es", ignoreCase = true) -> "Spanish"
            tl.startsWith("en", ignoreCase = true) -> "English"
            tl.startsWith("fr", ignoreCase = true) -> "French"
            tl.startsWith("de", ignoreCase = true) -> "German"
            tl.startsWith("it", ignoreCase = true) -> "Italian"
            else -> tl
        }
    }

    private fun translateWithGemini(text: String, tl: String): String? {
        return try {
            translationPreferences.migrateLegacyLlmSettings(TranslationLlmProvider.GEMINI)
            val apiKey = eu.kanade.translation.translator.sanitizeGeminiApiKey(
                translationPreferences.llmApiKey(TranslationLlmProvider.GEMINI).get(),
            )
            if (apiKey.isBlank()) return null

            val modelName = translationPreferences.novelLlmModel(TranslationLlmProvider.GEMINI).get()
                .ifBlank { translationPreferences.llmModel(TranslationLlmProvider.GEMINI).get() }
                .ifBlank { eu.kanade.translation.translator.DEFAULT_GEMINI_MODEL_ID }

            val targetLanguageName = targetLanguagePromptName(tl)
            val systemInstruction = "You are a professional literary translator for light novels and web novels. Translate the following text into natural $targetLanguageName. Preserve all paragraph breaks, HTML/image tags if any, and protected tokens (such as YOMOTSUTERM0000ZXQ) verbatim without altering casing or spacing. Output ONLY the translated text without commentary."
            val maxTokens = (translationPreferences.translationEngineMaxOutputTokens().get().toIntOrNull() ?: 8192)
                .coerceIn(2048, 8192)
            val temperature = translationPreferences.translationEngineTemperature().get().toFloatOrNull()
                ?.coerceIn(0f, 0.5f)
                ?: 0.25f
            val mediaType = "application/json; charset=utf-8".toMediaType()

            val result = executeGeminiStreamWithFallback(
                systemInstruction = systemInstruction,
                requestText = text,
                maxOutputTokens = maxTokens,
                modelName = modelName,
                temperature = temperature,
                responseMimeType = null,
            ) { url, requestBody ->
                val request = Request.Builder()
                    .url(url)
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream, application/json")
                    .post(requestBody.toRequestBody(mediaType))
                    .build()
                client.newCall(request).execute().toGeminiHttpResult()
            }
            if (result.statusCode !in 200..299) return null
            result.content.text.trim().ifBlank { null }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(throwable = e) { "Failed to translate chunk with Gemini" }
            null
        }
    }

    private suspend fun translateWithGroq(text: String, tl: String): String? {
        try {
            translationPreferences.migrateLegacyLlmSettings(TranslationLlmProvider.GROQ)
            val apiKey = eu.kanade.translation.translator.sanitizeGroqApiKey(
                translationPreferences.llmApiKey(TranslationLlmProvider.GROQ).get(),
            )
            val modelName = translationPreferences.novelLlmModel(TranslationLlmProvider.GROQ).get().trim()
                .ifBlank { translationPreferences.llmModel(TranslationLlmProvider.GROQ).get().trim() }
            if (apiKey.isBlank() || modelName.isBlank()) return null

            val targetLanguageName = targetLanguagePromptName(tl)
            val systemInstruction = "You are a professional literary translator for light novels and web novels. Translate the user's text into natural $targetLanguageName. Preserve all paragraph breaks, HTML/image tags if any, and protected tokens (such as YOMOTSUTERM0000ZXQ) verbatim without altering casing or spacing. Output ONLY the translated text without commentary."

            val jsonBody = JSONObject().apply {
                put("model", modelName)
                put("temperature", 0.3)
                put("max_completion_tokens", 4096)
                when {
                    eu.kanade.translation.translator.isGroqQwenReasoningModel(modelName) -> {
                        put("reasoning_effort", "none")
                    }
                    eu.kanade.translation.translator.isGroqGptOssModel(modelName) -> {
                        put("reasoning_effort", "low")
                        put("include_reasoning", false)
                    }
                }
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemInstruction)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", text)
                    })
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url(eu.kanade.translation.translator.GROQ_CHAT_COMPLETIONS_URL)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody(mediaType))
                .build()

            var attempt = 0
            while (true) {
                val (code, responseBody, waitMs) = client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    val headerWaitMs = eu.kanade.translation.translator.parseGroqRateLimitWaitMs(
                        retryAfterHeader = response.header("retry-after"),
                        resetTokensHeader = response.header("x-ratelimit-reset-tokens"),
                        resetRequestsHeader = response.header("x-ratelimit-reset-requests"),
                    )
                    Triple(response.code, body, headerWaitMs)
                }
                if (code == 429 && attempt < 2 && !eu.kanade.translation.translator.isGroqDailyQuotaExhausted(responseBody)) {
                    val delayMs = waitMs ?: ((attempt + 1) * 3_000L)
                    if (delayMs in 0..35_000L) {
                        attempt++
                        delay(delayMs + 250L)
                        continue
                    }
                }
                if (code !in 200..299 || responseBody.isBlank()) return null
                val json = JSONObject(responseBody)
                val rawContent = json.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                val cleaned = eu.kanade.translation.translator.stripGroqThinkingTags(rawContent)
                return cleaned.ifBlank { null }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(throwable = e) { "Failed to translate chunk with Groq" }
            return null
        }
    }

    private fun translateWithDeepL(
        text: String,
        sl: String,
        tl: String,
        previousContext: String? = null,
    ): String? {
        return try {
            val apiKey = translationPreferences.deepLApiKey().get()
                .trim()
                .removePrefix("DeepL-Auth-Key ")
                .removePrefix("Bearer ")
                .trim()
                .replace(Regex("[\\r\\n]"), "")
            if (apiKey.isBlank()) return null
            val endpoint = if (apiKey.endsWith(":fx")) {
                "https://api-free.deepl.com/v2/translate"
            } else {
                "https://api.deepl.com/v2/translate"
            }
            val deepLTarget = when {
                tl.startsWith("pt", ignoreCase = true) -> "PT-BR"
                tl.startsWith("en", ignoreCase = true) -> "EN-US"
                tl.equals("zh-cn", ignoreCase = true) -> "ZH-HANS"
                tl.equals("zh-tw", ignoreCase = true) -> "ZH-HANT"
                else -> tl.uppercase()
            }
            val formBuilder = FormBody.Builder()
                .add("target_lang", deepLTarget)
                .add("preserve_formatting", "1")
                .add("text", text)
            if (sl != "auto" && sl.isNotBlank()) {
                formBuilder.add("source_lang", sl.uppercase().take(2))
            }
            previousContext?.trim()?.takeIf { it.isNotEmpty() }?.let {
                formBuilder.add("context", it)
            }
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "DeepL-Auth-Key $apiKey")
                .post(formBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val responseBody = response.body?.string() ?: return null
                val translations = JSONObject(responseBody).optJSONArray("translations") ?: return null
                val result = buildString {
                    for (i in 0 until translations.length()) {
                        append(translations.optJSONObject(i)?.optString("text").orEmpty())
                    }
                }.trim()
                if (result.isNotBlank()) result else null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(throwable = e) { "Failed to translate chunk with DeepL" }
            null
        }
    }
}
