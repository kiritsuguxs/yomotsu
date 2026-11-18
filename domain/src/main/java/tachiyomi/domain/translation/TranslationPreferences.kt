package tachiyomi.domain.translation

import tachiyomi.core.common.preference.PreferenceStore

enum class TranslationLlmProvider(
    val preferenceKey: String,
    val defaultModel: String,
) {
    GEMINI("gemini", "gemini-3.8-flash"),
    GROQ("groq", "qwen/qwen3.8-27b"),
}

class TranslationPreferences(
    private val preferenceStore: PreferenceStore,
) {

    private val llmApiKeys = TranslationLlmProvider.entries.associateWith { provider ->
        preferenceStore.getString("translation_${provider.preferenceKey}_api_key", "")
    }
    private val llmModels = TranslationLlmProvider.entries.associateWith { provider ->
        preferenceStore.getString("translation_${provider.preferenceKey}_model", provider.defaultModel)
    }
    private val novelLlmModels = TranslationLlmProvider.entries.associateWith { provider ->
        preferenceStore.getString("novel_translation_${provider.preferenceKey}_model", provider.defaultModel)
    }

    fun autoTranslateAfterDownload() = preferenceStore.getBoolean("auto_translate_after_download", false)
    fun autoTranslateNovels() = preferenceStore.getBoolean("auto_translate_novels", true)
    fun novelTranslationEngine() = preferenceStore.getInt("novel_translation_engine", 1)
    fun autoTranslateManga(mangaId: Long) =
        preferenceStore.getBoolean("auto_translate_manga_$mangaId", false)
    fun translateSoundEffects() = preferenceStore.getBoolean("translate_sound_effects", false)
    fun translateFromLanguage() = preferenceStore.getString("translate_language_from", "ENGLISH")
    fun translateToLanguage() = preferenceStore.getString("translate_language_to", "PORTUGUESE")
    fun translationFont() = preferenceStore.getInt("translation_font", 0)

    // Stable OCR values: 0 = ML Kit, 1 = PaddleOCR.
    fun dbnetExperimental() = preferenceStore.getBoolean("dbnet_experimental", false)

    fun inpainterAot() = preferenceStore.getBoolean("inpainter_aot", false)

    fun ocrEngine() = preferenceStore.getInt("ocr_engine", 0)

    fun translationEngine() = preferenceStore.getInt("translation_engine", 0)
    fun translationFallbackEngine() = preferenceStore.getInt("translation_fallback_engine", 0)
    fun translationEngineModel() = preferenceStore.getString("translation_engine_model", "gemini-3.8-flash")
    fun translationEngineApiKey() = preferenceStore.getString("translation_engine_api_key", "")
    fun translationEngineTemperature() = preferenceStore.getString("translation_engine_temperature", "0.3")
    fun translationEngineMaxOutputTokens() = preferenceStore.getString("translation_engine_output_tokens", "8192")

    // DeepL uses its own authentication and translation endpoint rather than an LLM API.
    fun deepLApiKey() = preferenceStore.getString("deepl_api_key", "")

    fun llmApiKey(provider: TranslationLlmProvider) = llmApiKeys.getValue(provider)

    fun llmModel(provider: TranslationLlmProvider) = llmModels.getValue(provider)

    fun novelLlmModel(provider: TranslationLlmProvider) = novelLlmModels.getValue(provider)

    fun migrateLegacyLlmSettings(provider: TranslationLlmProvider) {
        val providerApiKey = llmApiKeys.getValue(provider)
        val providerModel = llmModels.getValue(provider)

        // Heal previously misrouted Gemini credentials/models copied into Groq preferences.
        if (provider == TranslationLlmProvider.GROQ) {
            if (providerApiKey.isSet() && providerApiKey.get().trim().startsWith("AIza")) {
                providerApiKey.delete()
            }
            if (providerModel.isSet() && providerModel.get().trim().startsWith("gemini-", ignoreCase = true)) {
                providerModel.delete()
            }
        } else if (provider == TranslationLlmProvider.GEMINI) {
            if (providerApiKey.isSet() && providerApiKey.get().trim().startsWith("gsk_")) {
                providerApiKey.delete()
            }
        }

        val legacyApiKey = translationEngineApiKey()
        if (!providerApiKey.isSet() && legacyApiKey.isSet()) {
            val rawKey = legacyApiKey.get().trim()
            val compatibleKey = when (provider) {
                TranslationLlmProvider.GEMINI -> !rawKey.startsWith("gsk_")
                TranslationLlmProvider.GROQ -> !rawKey.startsWith("AIza")
            }
            if (compatibleKey) {
                providerApiKey.set(legacyApiKey.get())
            }
        }

        val legacyModel = translationEngineModel()
        if (!providerModel.isSet() && legacyModel.isSet()) {
            val rawModel = legacyModel.get().trim()
            val compatibleModel = when (provider) {
                TranslationLlmProvider.GEMINI -> !rawModel.contains("/")
                TranslationLlmProvider.GROQ -> !rawModel.startsWith("gemini-", ignoreCase = true)
            }
            if (compatibleModel) {
                providerModel.set(legacyModel.get())
            }
        }
    }
}
