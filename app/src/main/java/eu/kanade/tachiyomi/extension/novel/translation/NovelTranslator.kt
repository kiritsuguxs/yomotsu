package eu.kanade.tachiyomi.extension.novel.translation

import android.app.Application
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.translation.memory.TranslationGlossary
import eu.kanade.translation.translator.ComicTranslationContext
import eu.kanade.translation.translator.TextTranslatorLanguage
import eu.kanade.translation.translator.TextTranslators
import kotlinx.coroutines.Dispatchers
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
import java.util.concurrent.ConcurrentHashMap

object NovelTranslator {

    private val client: OkHttpClient by lazy { Injekt.get<NetworkHelper>().client }
    private val translationCache = ConcurrentHashMap<Long, String>()
    private val translationPreferences: TranslationPreferences by lazy { Injekt.get() }
    private val context: Application by lazy { Injekt.get() }
    private val cacheDir by lazy { File(context.cacheDir, "novel_translations").apply { mkdirs() } }

    fun getCached(chapterId: Long): String? {
        translationCache[chapterId]?.let { return it }
        val file = File(cacheDir, "$chapterId.txt")
        if (file.exists()) {
            val text = file.readText()
            translationCache[chapterId] = text
            return text
        }
        return null
    }

    fun clearCached(chapterId: Long) {
        translationCache.remove(chapterId)
        val file = File(cacheDir, "$chapterId.txt")
        if (file.exists()) file.delete()
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

        getCached(chapterId)?.let { return@withContext it }

        val resolvedTargetLang = targetLang.ifBlank { resolveTargetLanguageCode("pt") }
        val engineIndex = runCatching { translationPreferences.novelTranslationEngine().get() }.getOrDefault(TextTranslators.GOOGLE.ordinal)
        val selectedEngine = TextTranslators.entries.getOrNull(engineIndex) ?: TextTranslators.GOOGLE
        val glossaryContext = seriesTitle.takeIf { it.isNotBlank() }?.let {
            ComicTranslationContext(mangaTitle = it, chapterName = "")
        }

        val maxChunkLen = when (selectedEngine) {
            TextTranslators.GEMINI, TextTranslators.OPENROUTER -> 8000
            else -> 3000
        }
        val chunks = chunkText(text, maxChunkLen)
        val translatedChunks = chunks.map { chunk ->
            if (chunk.isBlank()) {
                ""
            } else if (glossaryContext != null) {
                val prepared = TranslationGlossary.prepare(chunk, glossaryContext)
                val rawTranslated = translateChunk(prepared.textForTranslation, sourceLang, resolvedTargetLang, selectedEngine)
                TranslationGlossary.resolve(prepared, rawTranslated, glossaryContext)
            } else {
                translateChunk(chunk, sourceLang, resolvedTargetLang, selectedEngine)
            }
        }
        val result = translatedChunks.joinToString("\n\n")

        translationCache[chapterId] = result
        try {
            File(cacheDir, "$chapterId.txt").writeText(result)
        } catch (e: Exception) {
            logcat(throwable = e) { "Failed to write translation cache" }
        }
        result
    }

    private fun chunkText(text: String, maxLength: Int): List<String> {
        val paragraphs = text.split("\n\n")
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        for (p in paragraphs) {
            if (currentChunk.length + p.length > maxLength && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString().trim())
                currentChunk = StringBuilder()
            }
            currentChunk.append(p).append("\n\n")
        }
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }
        return chunks
    }

    private fun translateChunk(
        text: String,
        sl: String,
        tl: String,
        engine: TextTranslators = TextTranslators.GOOGLE,
    ): String {
        return when (engine) {
            TextTranslators.GEMINI -> translateWithGemini(text, tl) ?: translateWithGoogle(text, sl, tl)
            TextTranslators.OPENROUTER -> translateWithOpenRouter(text, tl) ?: translateWithGoogle(text, sl, tl)
            TextTranslators.DEEPL -> translateWithDeepL(text, sl, tl) ?: translateWithGoogle(text, sl, tl)
            else -> translateWithGoogle(text, sl, tl)
        }
    }

    private fun translateWithGoogle(text: String, sl: String, tl: String): String {
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
                val responseBody = response.body?.string() ?: return text
                val jsonArray = JSONArray(responseBody)
                val sentences = jsonArray.getJSONArray(0)

                buildString {
                    for (i in 0 until sentences.length()) {
                        val sentence = sentences.optJSONArray(i)
                        if (sentence != null) {
                            append(sentence.optString(0, ""))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logcat(throwable = e) { "Failed to translate chunk with Google: $text" }
            text
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
            val apiKey = translationPreferences.llmApiKey(TranslationLlmProvider.GEMINI).get().trim()
            if (apiKey.isBlank()) return null

            val modelName = translationPreferences.llmModel(TranslationLlmProvider.GEMINI).get().ifBlank { "gemini-1.5-flash" }
            val normalizedModel = modelName.trim().removePrefix("models/")
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$normalizedModel:generateContent?key=$apiKey"

            val targetLanguageName = targetLanguagePromptName(tl)
            val systemInstruction = "You are a professional literary translator for light novels and web novels. Translate the following text into natural $targetLanguageName. Preserve all paragraph breaks, HTML/image tags if any, and protected tokens (such as [[YOMOTSU_TERM_0]]). Output ONLY the translated text without commentary."

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "$systemInstruction\n\n$text")
                            })
                        })
                    })
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val responseBody = response.body?.string() ?: return null
                val resObj = JSONObject(responseBody)
                val parts = resObj.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                val candidateText = buildString {
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            append(parts.optJSONObject(i)?.optString("text").orEmpty())
                        }
                    }
                }.trim()

                if (candidateText.isNotBlank()) candidateText else null
            }
        } catch (e: Exception) {
            logcat(throwable = e) { "Failed to translate chunk with Gemini" }
            null
        }
    }

    private fun translateWithOpenRouter(text: String, tl: String): String? {
        return try {
            translationPreferences.migrateLegacyLlmSettings(TranslationLlmProvider.OPENROUTER)
            val apiKey = translationPreferences.llmApiKey(TranslationLlmProvider.OPENROUTER).get().trim()
            val modelName = translationPreferences.llmModel(TranslationLlmProvider.OPENROUTER).get().trim()
            if (apiKey.isBlank() || modelName.isBlank()) return null

            val targetLanguageName = targetLanguagePromptName(tl)
            val systemInstruction = "You are a professional literary translator for light novels and web novels. Translate the user's text into natural $targetLanguageName. Preserve all paragraph breaks, HTML/image tags if any, and protected tokens (such as [[YOMOTSU_TERM_0]]). Output ONLY the translated text without commentary."

            val jsonBody = JSONObject().apply {
                put("model", modelName)
                put("temperature", 0.3)
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
                .url("https://openrouter.ai/api/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val responseBody = response.body?.string() ?: return null
                val json = JSONObject(responseBody)
                val content = json.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()
                if (!content.isNullOrBlank()) content else null
            }
        } catch (e: Exception) {
            logcat(throwable = e) { "Failed to translate chunk with OpenRouter" }
            null
        }
    }

    private fun translateWithDeepL(text: String, sl: String, tl: String): String? {
        return try {
            val apiKey = translationPreferences.deepLApiKey().get().trim()
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
                .add("text", text)
            if (sl != "auto" && sl.isNotBlank()) {
                formBuilder.add("source_lang", sl.uppercase().take(2))
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
        } catch (e: Exception) {
            logcat(throwable = e) { "Failed to translate chunk with DeepL" }
            null
        }
    }
}
