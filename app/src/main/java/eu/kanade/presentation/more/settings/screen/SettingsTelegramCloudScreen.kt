package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import tachiyomi.domain.telegram.TelegramPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsTelegramCloudScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_telegram_cloud

    @Composable
    override fun getPreferences(): List<Preference> {
        val preferences = remember { Injekt.get<TelegramPreferences>() }
        val navigator = LocalNavigator.currentOrThrow

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = preferences.enableTelegramCloud,
                title = stringResource(MR.strings.pref_enable_telegram_cloud),
                subtitle = stringResource(MR.strings.pref_enable_telegram_cloud_summary),
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = preferences.botToken,
                title = stringResource(MR.strings.pref_telegram_bot_token),
                subtitle = stringResource(MR.strings.pref_telegram_bot_token_summary),
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = preferences.chatId,
                title = stringResource(MR.strings.pref_telegram_chat_id),
                subtitle = stringResource(MR.strings.pref_telegram_chat_id_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = preferences.deleteLocalAfterUpload,
                title = stringResource(MR.strings.pref_telegram_delete_local),
                subtitle = stringResource(MR.strings.pref_telegram_delete_local_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = preferences.restoreToLocalSource,
                title = stringResource(MR.strings.pref_telegram_restore_to_local_source),
                subtitle = stringResource(MR.strings.pref_telegram_restore_to_local_source_summary),
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_telegram_cloud_manager),
                subtitle = stringResource(MR.strings.pref_telegram_cloud_manager_summary),
                onClick = {
                    navigator.push(TelegramCloudManagerScreen())
                },
            ),
        )
    }
}
