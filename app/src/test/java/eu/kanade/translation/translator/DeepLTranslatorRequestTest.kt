package eu.kanade.translation.translator

import eu.kanade.translation.recognizer.TextRecognizerLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeepLTranslatorRequestTest {

    @Test
    fun `buildDeepLContextWindow keeps latest lines within block and char limits`() {
        assertNull(buildDeepLContextWindow(emptyList()))

        val window = buildDeepLContextWindow(
            precedingSources = listOf("Line 1", "Line 2", "  ", "Line 3", "Line 4"),
            maxBlocks = 2,
            maxChars = 100,
        )
        assertEquals("Line 3\nLine 4", window)

        val charCapped = buildDeepLContextWindow(
            precedingSources = listOf("First very long sentence", "Short 1", "Short 2"),
            maxBlocks = 8,
            maxChars = 16,
        )
        assertEquals("Short 1\nShort 2", charCapped)
    }

    @Test
    fun `buildDeepLComicFormParameters includes prefer_less formality and context for PT-BR`() {
        val params = buildDeepLComicFormParameters(
            texts = listOf("Wait!", "Are you sure?"),
            fromLang = TextRecognizerLanguage.ENGLISH,
            toLang = TextTranslatorLanguage.PORTUGUESE,
            contextText = "Where is he going?",
        )

        assertTrue(("target_lang" to "PT-BR") in params)
        assertTrue(("source_lang" to "EN") in params)
        assertTrue(("formality" to "prefer_less") in params)
        assertTrue(("context" to "Where is he going?") in params)
        assertEquals(listOf("Wait!", "Are you sure?"), params.filter { it.first == "text" }.map { it.second })
    }

    @Test
    fun `buildDeepLComicFormParameters omits formality for unsupported target languages`() {
        val params = buildDeepLComicFormParameters(
            texts = listOf("Hello"),
            fromLang = TextRecognizerLanguage.ENGLISH,
            toLang = TextTranslatorLanguage.KOREAN,
            contextText = null,
        )

        assertFalse(params.any { it.first == "formality" })
        assertFalse(params.any { it.first == "context" })
        assertTrue(("target_lang" to "KO") in params)
    }
}
