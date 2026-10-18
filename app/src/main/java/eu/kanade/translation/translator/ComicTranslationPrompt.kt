package eu.kanade.translation.translator

import eu.kanade.translation.memory.TranslationGlossary
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.normalizeTranslationText
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import org.json.JSONObject

internal fun normalizeOcrText(text: String): String {
    return normalizeTranslationText(text)
}

internal fun TextTranslatorLanguage.translationApiCode(): String {
    return if (this == TextTranslatorLanguage.PORTUGUESE) "pt-BR" else code
}

internal fun buildComicInputJson(
    pages: Map<String, PageTranslation>,
    textForBlock: (TranslationBlock) -> String = { normalizeOcrText(it.text) },
): String {
    return buildJsonObject {
        pages.forEach { (fileName, page) ->
            putJsonArray(fileName) {
                page.blocks.forEach { block -> add(textForBlock(block)) }
            }
        }
    }.toString()
}

internal fun buildComicTranslationPrompt(
    fromLang: TextRecognizerLanguage,
    toLang: TextTranslatorLanguage,
): String {
    val targetLanguage = if (toLang == TextTranslatorLanguage.PORTUGUESE) {
        "Brazilian Portuguese (pt-BR)"
    } else {
        toLang.label
    }
    val brazilianPortugueseRules = if (toLang == TextTranslatorLanguage.PORTUGUESE) {
        """
        Brazilian Portuguese requirements (CRITICAL FOR COMIC BUBBLE FIT):
        - Write exclusively in natural, modern, colloquial Brazilian Portuguese (pt-BR).
        - AVOID VERBOSE OR FORMAL PHRASING: Portuguese text naturally tends to be longer than English. You MUST actively condense sentences so the text fits comfortably inside small comic speech bubbles.
        - Use natural spoken Brazilian contractions and idioms (e.g., use 'tá', 'tô', 'pra', 'pro', 'ce', 'né', 'num', 'numa', 'dali', 'daqui' in casual dialogue).
        - NEVER use European Portuguese grammar, vocabulary, or syntax (no 'está a fazer', 'rapariga', 'miúdo', 'ecrã', etc.).
        - Use direct, expressive, informal Brazilian comic phrasing (gírias e expressões naturais de quadrinhos e mangás brasileiros, ex: 'beleza', 'cara', 'droga', 'valeu', 'bora').
        - Prefer shorter synonyms and punchy, direct sentence structures over literal, wordy translations.
        - Keep proper names unchanged and adapt kinship terms and honorifics naturally (ex: 'hyung/senpai' -> contexto natural ou nome, 'irmão', 'chefe', etc.).
        - Dialogue length constraint: Keep translated text length as close as possible to the visual weight of the source. If a phrase is too long, rephrase into a shorter, punchier Brazilian equivalent that fits inside the bubble.
        """.trimIndent()
    } else {
        ""
    }

    return """
        You are a professional translator and dialogue editor for manga, manhwa, and manhua.

        The source OCR language is ${fromLang.label}. Translate the chapter to $targetLanguage.
        The input is a JSON object. Keys are page filenames in reading order; each value is an array of text regions in visual reading order.

        Context and editing rules:
        - Read the entire sequential chapter before deciding ambiguous wording.
        - Use nearby regions and adjacent pages to infer speaker, gender, relationship, tone, pronouns, and implied subjects.
        - Treat line breaks inside an OCR region as visual wrapping, not separate sentences.
        - Silently repair only obvious OCR mistakes when the surrounding context makes the intended text clear.
        - Produce fluent comic dialogue that sounds written by a human editor, never a literal sequence of dictionary substitutions.
        - Preserve meaning, emotion, politeness level, jokes, names, honorifics, and recurring terminology consistently.
        - Do not invent information that is absent from the source.
        - Be highly concise. Prefer shorter words and direct phrasing. The translated text must fit inside small comic speech bubbles.
        - Internally perform a second editing pass and rewrite any stiff, contradictory, or unnatural sentence before returning it.

        $brazilianPortugueseRules

        Output rules:
        - Return only one valid JSON object, without Markdown or explanations.
        - Preserve every input key, array order, and array length exactly.
        - Every output item must be a string containing only its final translation.
        - Never translate watermarks, credits, chapter notes, scanlation group names, or site links; return an empty string "" for any such element.
    """.trimIndent()
}

internal fun buildComicTranslationRequest(
    context: ComicTranslationContext,
    inputJson: String,
): String {
    val workContext = buildList {
        context.mangaTitle.takeIf { it.isNotBlank() }?.let { add("Series: $it") }
        context.chapterName.takeIf { it.isNotBlank() }?.let { add("Chapter: $it") }
    }.joinToString("\n")
    val glossary = TranslationGlossary.instructions(context)

    return buildString {
        if (workContext.isNotBlank()) {
            appendLine(workContext)
        }
        if (glossary.isNotBlank()) {
            appendLine()
            appendLine(glossary)
            appendLine()
        }
        appendLine("Translate the following sequential chapter JSON:")
        append(inputJson)
    }
}

internal fun parseComicTranslationResponse(response: String): JSONObject {
    val clean = response.trim()
        .removePrefix("```json")
        .removePrefix("```JSON")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()

    val firstBrace = clean.indexOf('{')
    val lastBrace = clean.lastIndexOf('}')

    if (firstBrace >= 0 && lastBrace > firstBrace) {
        val candidate = clean.substring(firstBrace, lastBrace + 1)
        val directResult = runCatching { JSONObject(candidate) }.getOrNull()
        if (directResult != null) return directResult
    }

    // Fallback: Salvage valid pages from truncated or malformed LLM JSON stream
    return repairTruncatedComicTranslationJson(clean)
}

internal fun repairTruncatedComicTranslationJson(raw: String): JSONObject {
    val recovered = JSONObject()
    val pageEntryRegex = Regex(""""([^"\\]*(?:\\.[^"\\]*)*)"\s*:\s*\[([^\]]*)(\]|\z)""", RegexOption.DOT_MATCHES_ALL)
    val stringElementRegex = Regex(""""([^"\\]*(?:\\.[^"\\]*)*)"""")

    for (match in pageEntryRegex.findAll(raw)) {
        val rawPageKey = match.groupValues[1]
        val arrayContent = match.groupValues[2]

        val elements = stringElementRegex.findAll(arrayContent).map { strMatch ->
            try {
                // Decode JSON string escaping safely
                JSONObject("""{"v":"${strMatch.groupValues[1]}"}""").getString("v")
            } catch (_: Exception) {
                strMatch.groupValues[1]
            }
        }.toList()

        if (elements.isNotEmpty()) {
            val jsonArray = org.json.JSONArray()
            elements.forEach { jsonArray.put(it) }
            recovered.put(rawPageKey, jsonArray)
        }
    }

    require(recovered.length() > 0) { "Translation response does not contain recoverable JSON: ${raw.take(120)}" }
    return recovered
}
