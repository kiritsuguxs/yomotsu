package eu.kanade.translation.translator

import eu.kanade.tachiyomi.network.await
import eu.kanade.translation.memory.PreparedGlossaryText
import eu.kanade.translation.memory.TranslationCache
import eu.kanade.translation.memory.TranslationGlossary
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationRecoveryPolicy
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.coroutines.CancellationException
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

class DeepLTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
    private val apiKey: String,
) : TextTranslator {

    private val okHttpClient by lazy { TranslationNetworkHelper.sharedClient }

    override suspend fun translate(
        pages: MutableMap<String, PageTranslation>,
        context: ComicTranslationContext,
    ) {
        if (apiKey.isBlank()) throw TranslationProviderFailureMapper.missingKey(TranslationProviderId.DEEPL)
        try {
            translateInternal(pages, context)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw TranslationProviderFailureMapper.fromThrowable(TranslationProviderId.DEEPL, error)
        }
    }

    private suspend fun translateInternal(
        pages: MutableMap<String, PageTranslation>,
        context: ComicTranslationContext,
    ) {
        val blocks = pages.values.flatMap { it.blocks }
        if (blocks.isEmpty()) return

        val normalizedSources = blocks.map { normalizeOcrText(it.text) }
        val missing = mutableListOf<Triple<Int, String, PreparedGlossaryText>>()
        val resolved = arrayOfNulls<String>(blocks.size)

        blocks.forEachIndexed { index, _ ->
            val source = normalizedSources[index]
            val cached = TranslationCache.get(context, fromLang, toLang, source)
            if (cached != null) {
                resolved[index] = cached
            } else {
                missing += Triple(index, source, TranslationGlossary.prepare(source, context))
            }
        }

        if (missing.isNotEmpty()) {
            missing.chunked(MAX_TEXTS_PER_REQUEST).forEach { chunk ->
                val firstIndexInChunk = chunk.first().first
                val contextWindow = buildDeepLContextWindow(
                    precedingSources = normalizedSources.subList(0, firstIndexInChunk),
                )
                val translated = translateTexts(
                    texts = chunk.map { it.third.textForTranslation },
                    contextText = contextWindow,
                )
                chunk.forEachIndexed { translatedIndex, (blockIndex, source, prepared) ->
                    val block = blocks[blockIndex]
                    val rawMachine = translated.getOrElse(translatedIndex) { block.text }
                    val machineTranslation = postEditMachineTranslation(
                        sourceText = block.text,
                        translatedText = rawMachine,
                        targetLanguage = toLang,
                    )
                    val resolvedTranslation = TranslationGlossary.resolve(
                        prepared = prepared,
                        translatedText = machineTranslation,
                        context = context,
                    )
                    if (resolvedTranslation.contains("RTMTH", ignoreCase = true) ||
                        TranslationRecoveryPolicy.isWatermarkOrCredit(block.text) ||
                        TranslationRecoveryPolicy.isWatermarkOrCredit(resolvedTranslation)
                    ) {
                        resolved[blockIndex] = ""
                    } else {
                        resolved[blockIndex] = resolvedTranslation
                        if (resolvedTranslation.isNotBlank()) {
                            TranslationCache.put(context, fromLang, toLang, source, resolvedTranslation)
                        }
                    }
                }
            }
        }

        blocks.forEachIndexed { index, block ->
            val translatedText = resolved[index] ?: ""
            if (translatedText.contains("RTMTH", ignoreCase = true) ||
                TranslationRecoveryPolicy.isWatermarkOrCredit(block.text) ||
                TranslationRecoveryPolicy.isWatermarkOrCredit(translatedText)
            ) {
                block.translation = ""
            } else {
                block.translation = TranslationGlossary.apply(
                    sourceText = block.text,
                    translatedText = translatedText,
                    context = context,
                )
            }
        }
    }

    private suspend fun translateTexts(
        texts: List<String>,
        contextText: String? = null,
    ): List<String> {
        val result = mutableListOf<String>()
        texts.chunked(MAX_TEXTS_PER_REQUEST).forEach { chunk ->
            val formBuilder = FormBody.Builder()
            buildDeepLComicFormParameters(
                texts = chunk,
                fromLang = fromLang,
                toLang = toLang,
                contextText = contextText,
            ).forEach { (key, value) ->
                formBuilder.add(key, value)
            }

            val request = Request.Builder()
                .url(endpointFor(apiKey))
                .header("Authorization", "DeepL-Auth-Key $apiKey")
                .post(formBuilder.build())
                .build()

            val response = okHttpClient.newCall(request).await()
            val body = response.use {
                val responseBody = it.body.string()
                if (!it.isSuccessful) {
                    throw TranslationProviderFailureMapper.fromHttp(
                        provider = TranslationProviderId.DEEPL,
                        statusCode = it.code,
                        responseBody = responseBody,
                    )
                }
                responseBody
            }

            val translations = JSONObject(body).getJSONArray("translations")
            for (i in 0 until translations.length()) {
                result += translations.getJSONObject(i).getString("text")
            }
        }
        return result
    }

    private fun endpointFor(key: String): String =
        if (key.endsWith(":fx")) FREE_ENDPOINT else PRO_ENDPOINT

    override fun close() = Unit

    private companion object {
        const val FREE_ENDPOINT = "https://api-free.deepl.com/v2/translate"
        const val PRO_ENDPOINT = "https://api.deepl.com/v2/translate"
        const val MAX_TEXTS_PER_REQUEST = 40
    }
}

internal fun buildDeepLContextWindow(
    precedingSources: List<String>,
    maxBlocks: Int = DEEPL_MAX_CONTEXT_BLOCKS,
    maxChars: Int = DEEPL_MAX_CONTEXT_CHARS,
): String? {
    if (precedingSources.isEmpty() || maxBlocks <= 0 || maxChars <= 0) return null
    val selected = ArrayDeque<String>()
    var totalChars = 0
    for (source in precedingSources.asReversed()) {
        val cleaned = source.trim()
        if (cleaned.isEmpty()) continue
        val extra = if (selected.isEmpty()) cleaned.length else cleaned.length + 1
        if (selected.isNotEmpty() && (selected.size >= maxBlocks || totalChars + extra > maxChars)) {
            break
        }
        selected.addFirst(cleaned)
        totalChars += extra
        if (totalChars >= maxChars) break
    }
    if (selected.isEmpty()) return null
    return selected.joinToString("\n").take(maxChars)
}

internal fun buildDeepLComicFormParameters(
    texts: List<String>,
    fromLang: TextRecognizerLanguage,
    toLang: TextTranslatorLanguage,
    contextText: String? = null,
): List<Pair<String, String>> = buildList {
    add("target_lang" to deepLTargetLanguage(toLang))
    deepLSourceLanguage(fromLang)?.let { add("source_lang" to it) }
    if (deepLSupportsFormality(toLang)) {
        add("formality" to "prefer_less")
    }
    contextText?.trim()?.takeIf { it.isNotEmpty() }?.let {
        add("context" to it.take(DEEPL_MAX_CONTEXT_CHARS))
    }
    texts.forEach { add("text" to it) }
}

internal fun deepLTargetLanguage(language: TextTranslatorLanguage): String = when (language) {
    TextTranslatorLanguage.PORTUGUESE -> "PT-BR"
    TextTranslatorLanguage.ENGLISH -> "EN-US"
    TextTranslatorLanguage.CHINESESIM -> "ZH-HANS"
    TextTranslatorLanguage.CHINESETRAD -> "ZH-HANT"
    else -> language.code.uppercase()
}

internal fun deepLSourceLanguage(language: TextRecognizerLanguage): String? = when (language.code.lowercase()) {
    "zh", "zh-cn", "zh-tw" -> "ZH"
    "en" -> "EN"
    "ja" -> "JA"
    "ko" -> "KO"
    "pt" -> "PT"
    "de" -> "DE"
    "fr" -> "FR"
    "es" -> "ES"
    "it" -> "IT"
    "ru" -> "RU"
    else -> null
}

internal fun deepLSupportsFormality(language: TextTranslatorLanguage): Boolean = when (language) {
    TextTranslatorLanguage.PORTUGUESE,
    TextTranslatorLanguage.GERMAN,
    TextTranslatorLanguage.FRENCH,
    TextTranslatorLanguage.ITALIAN,
    TextTranslatorLanguage.SPANISH,
    TextTranslatorLanguage.DUTCH,
    TextTranslatorLanguage.POLISH,
    TextTranslatorLanguage.JAPANESE,
    TextTranslatorLanguage.RUSSIAN,
    -> true
    else -> false
}

private const val DEEPL_MAX_CONTEXT_BLOCKS = 8
private const val DEEPL_MAX_CONTEXT_CHARS = 1500
