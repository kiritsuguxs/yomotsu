package eu.kanade.presentation.more.settings.screen.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object PlayerSettingsSubtitleScreen : SearchableSettings {
    @Suppress("unused")
    private fun readResolve(): Any = PlayerSettingsSubtitleScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = AYMR.strings.pref_player_subtitle

    @Composable
    override fun getPreferences(): List<Preference> {
        val subtitlePreferences = remember { Injekt.get<SubtitlePreferences>() }

        val langPref = subtitlePreferences.preferredSubLanguages()
        val whitelist = subtitlePreferences.subtitleWhitelist()
        val blacklist = subtitlePreferences.subtitleBlacklist()
        val blackBars = subtitlePreferences.subtitleBlackBars()
        val systemFonts = subtitlePreferences.subtitleSystemFonts

        return listOf(
            Preference.PreferenceItem.EditTextPreference(
                preference = langPref,
                title = stringResource(AYMR.strings.pref_player_subtitle_lang),
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = whitelist,
                title = stringResource(AYMR.strings.pref_player_subtitle_whitelist),
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = blacklist,
                title = stringResource(AYMR.strings.pref_player_subtitle_blacklist),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = blackBars,
                title = stringResource(AYMR.strings.player_pref_subtitle_black_bars),
                subtitle = stringResource(AYMR.strings.player_pref_subtitle_black_bars_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = systemFonts,
                title = stringResource(AYMR.strings.player_pref_subtitle_system_fonts),
            ),
        )
    }
}
