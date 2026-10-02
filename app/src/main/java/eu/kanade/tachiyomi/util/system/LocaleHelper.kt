package eu.kanade.tachiyomi.util.system

import android.content.Context
import androidx.core.os.LocaleListCompat
import eu.kanade.tachiyomi.ui.browse.source.SourcesViewModel
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.util.Locale

/**
 * Utility class to change the application's language in runtime.
 */
object LocaleHelper {

    /**
     * Sorts by display name, except keeps the "all" (displayed as "Multi") locale at the top.
     */
    val comparator = { a: String, b: String ->
        if (a == "all") {
            -1
        } else if (b == "all") {
            1
        } else {
            getLocalizedDisplayName(a).compareTo(getLocalizedDisplayName(b))
        }
    }

    /**
     * Normalizes novel plugin language strings/names to standard BCP-47 / ISO language codes.
     */
    fun mapNovelLangToCode(rawLang: String): String {
        val clean = rawLang.trim().removePrefix("\u200e").removePrefix("\u200f").lowercase()
        return when {
            clean.contains("portugu") || clean.startsWith("pt") -> "pt-BR"
            clean.contains("english") || clean == "en" -> "en"
            clean.contains("español") || clean.contains("espanol") || clean.contains("spanish") || clean == "es" -> "es"
            clean.contains("franç") || clean.contains("franc") || clean.contains("french") || clean == "fr" -> "fr"
            clean.contains("deutsch") || clean.contains("german") || clean == "de" -> "de"
            clean.contains("italian") || clean == "it" -> "it"
            clean.contains("indonesia") || clean == "id" -> "id"
            clean.contains("polski") || clean.contains("polish") || clean == "pl" -> "pl"
            clean.contains("việt") || clean.contains("viet") || clean == "vi" -> "vi"
            clean.contains("türk") || clean.contains("turk") || clean == "tr" -> "tr"
            clean.contains("русский") || clean.contains("russian") || clean == "ru" -> "ru"
            clean.contains("укра") || clean.contains("ukrainian") || clean == "uk" -> "uk"
            clean.contains("ไทย") || clean.contains("thai") || clean == "th" -> "th"
            clean.contains("عرب") || clean.contains("arabic") || clean == "ar" -> "ar"
            clean.contains("中文") || clean.contains("chinese") || clean.startsWith("zh") -> "zh-Hans"
            clean.contains("日本") || clean.contains("japanese") || clean == "ja" -> "ja"
            clean.contains("한국") || clean.contains("조선") || clean.contains("korean") || clean == "ko" -> "ko"
            clean.contains("multi") || clean == "all" -> "all"
            clean.contains("dansk") || clean.contains("danish") || clean == "da" -> "da"
            clean.contains("filipino") || clean.contains("tagalog") || clean == "fil" || clean == "tl" -> "fil"
            clean.contains("magyar") || clean.contains("hungarian") || clean == "hu" -> "hu"
            clean.contains("melayu") || clean.contains("malay") || clean == "ms" -> "ms"
            clean.contains("nederlands") || clean.contains("dutch") || clean == "nl" -> "nl"
            clean.contains("norsk") || clean.contains("norwegian") || clean == "no" || clean == "nb" -> "no"
            clean.contains("român") || clean.contains("romanian") || clean == "ro" -> "ro"
            clean.contains("suomi") || clean.contains("finnish") || clean == "fi" -> "fi"
            clean.contains("svenska") || clean.contains("swedish") || clean == "sv" -> "sv"
            clean.contains("čeština") || clean.contains("czech") || clean == "cs" -> "cs"
            clean.contains("ελλην") || clean.contains("greek") || clean == "el" -> "el"
            clean.contains("българ") || clean.contains("bulgarian") || clean == "bg" -> "bg"
            clean.contains("срп") || clean.contains("serbian") || clean == "sr" -> "sr"
            clean.contains("עברית") || clean.contains("hebrew") || clean == "he" -> "he"
            clean.contains("मराठी") || clean.contains("marathi") || clean == "mr" -> "mr"
            clean.contains("हिन्दी") || clean.contains("hindi") || clean == "hi" -> "hi"
            clean.contains("বাংলা") || clean.contains("bengali") || clean == "bn" -> "bn"
            clean.contains("தமிழ்") || clean.contains("tamil") || clean == "ta" -> "ta"
            clean.contains("తెలుగు") || clean.contains("telugu") || clean == "te" -> "te"
            clean.contains("മലയാളം") || clean.contains("malayalam") || clean == "ml" -> "ml"
            clean.length in 2..3 && clean.all { it in 'a'..'z' } -> clean
            else -> clean
        }
    }

    /**
     * Returns display name of a string language code.
     */
    fun getSourceDisplayName(lang: String?, context: Context): String {
        val normalizedCode = if (lang != null) mapNovelLangToCode(lang) else null
        return when (normalizedCode) {
            SourcesViewModel.LAST_USED_KEY -> context.stringResource(MR.strings.last_used_source)
            SourcesViewModel.PINNED_KEY -> context.stringResource(MR.strings.pinned_sources)
            "other" -> context.stringResource(MR.strings.other_source)
            "all" -> context.stringResource(MR.strings.multi_lang)
            else -> getLocalizedDisplayName(lang)
        }
    }

    fun getDisplayName(lang: String): String {
        val normalizedLang = when (lang) {
            "zh-CN" -> "zh-Hans"
            "zh-TW" -> "zh-Hant"
            else -> lang
        }

        return Locale.forLanguageTag(normalizedLang).displayName
    }

    fun getShortDisplayName(lang: String?, uppercase: Boolean = false): String {
        return when (lang) {
            null -> ""
            "es-419" -> "es-la"
            "zh-CN" -> "zh-hans"
            "zh-TW" -> "zh-hant"
            else -> lang
        }
            .let { if (uppercase) it.uppercase(Locale.ENGLISH) else it }
    }

    /**
     * Returns display name of a string language code.
     *
     * @param lang empty for system language
     */
    fun getLocalizedDisplayName(lang: String?): String {
        if (lang == null) {
            return ""
        }

        val normalized = mapNovelLangToCode(lang)
        val locale = when (normalized) {
            "" -> LocaleListCompat.getAdjustedDefault()[0]
            "zh-CN", "zh" -> Locale.forLanguageTag("zh-Hans")
            "zh-TW" -> Locale.forLanguageTag("zh-Hant")
            else -> Locale.forLanguageTag(normalized)
        }
        val name = locale?.getDisplayName(locale)?.replaceFirstChar { it.uppercase(locale) }.orEmpty()
        if (name.isNotBlank()) {
            return name
        }

        return lang.trim().removePrefix("\u200e").removePrefix("\u200f").replaceFirstChar { it.uppercase() }
    }

    /**
     * Return the default languages enabled for the sources.
     */
    fun getDefaultEnabledLanguages(): Set<String> {
        return setOf("all", "en", Locale.getDefault().language)
    }
}
