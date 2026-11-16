package tachiyomi.domain.translation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference

class TranslationLlmPreferencesTest {

    @Test
    fun `Gemini and Groq retain independent API keys and models and updated defaults`() {
        val preferences = TranslationPreferences(InMemoryPreferenceStore())
        val geminiApiKey = preferences.llmApiKey(TranslationLlmProvider.GEMINI)
        val geminiModel = preferences.llmModel(TranslationLlmProvider.GEMINI)
        val groqApiKey = preferences.llmApiKey(TranslationLlmProvider.GROQ)
        val groqModel = preferences.llmModel(TranslationLlmProvider.GROQ)

        assertEquals("gemini-3.8-flash", geminiModel.get())
        assertEquals("qwen/qwen3.8-27b", groqModel.get())
        assertNotEquals(geminiApiKey.key(), groqApiKey.key())
        assertNotEquals(geminiModel.key(), groqModel.key())

        geminiApiKey.set("gemini-key")
        geminiModel.set("gemini-model")
        groqApiKey.set("groq-key")
        groqModel.set("groq-model")

        assertEquals("gemini-key", preferences.llmApiKey(TranslationLlmProvider.GEMINI).get())
        assertEquals("gemini-model", preferences.llmModel(TranslationLlmProvider.GEMINI).get())
        assertEquals("groq-key", preferences.llmApiKey(TranslationLlmProvider.GROQ).get())
        assertEquals("groq-model", preferences.llmModel(TranslationLlmProvider.GROQ).get())
    }

    @Test
    fun `legacy shared values migrate only to the selected provider`() {
        val store = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreference("translation_engine_api_key", "legacy-key", ""),
                InMemoryPreference("translation_engine_model", "legacy-model", "gemini-3.8-flash"),
            ),
        )
        val preferences = TranslationPreferences(store)

        preferences.migrateLegacyLlmSettings(TranslationLlmProvider.GEMINI)

        assertEquals("legacy-key", preferences.llmApiKey(TranslationLlmProvider.GEMINI).get())
        assertEquals("legacy-model", preferences.llmModel(TranslationLlmProvider.GEMINI).get())
        assertFalse(preferences.llmApiKey(TranslationLlmProvider.GROQ).isSet())
        assertFalse(preferences.llmModel(TranslationLlmProvider.GROQ).isSet())
    }

    @Test
    fun `legacy migration never overwrites provider values`() {
        val store = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreference("translation_engine_api_key", "legacy-key", ""),
                InMemoryPreference("translation_engine_model", "legacy-model", "gemini-3.8-flash"),
                InMemoryPreference("translation_groq_api_key", "saved-key", ""),
                InMemoryPreference("translation_groq_model", "saved-model", "qwen/qwen3.8-27b"),
            ),
        )
        val preferences = TranslationPreferences(store)

        preferences.migrateLegacyLlmSettings(TranslationLlmProvider.GROQ)

        assertEquals("saved-key", preferences.llmApiKey(TranslationLlmProvider.GROQ).get())
        assertEquals("saved-model", preferences.llmModel(TranslationLlmProvider.GROQ).get())
    }
}
