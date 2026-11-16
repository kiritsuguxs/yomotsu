package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import eu.kanade.translation.translator.GeminiModelInfo
import eu.kanade.translation.translator.GeminiModelsCatalog
import tachiyomi.core.common.preference.Preference
import tachiyomi.i18n.at.ATMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun GeminiModelsDialog(
    preference: Preference<String>,
    apiKey: String,
    onDismissRequest: () -> Unit,
) {
    LlmModelsPickerDialog(
        preference = preference,
        apiKey = apiKey,
        title = stringResource(ATMR.strings.gemini_models_title),
        loadingText = stringResource(ATMR.strings.gemini_models_loading),
        errorText = stringResource(ATMR.strings.gemini_models_error),
        missingKeyHint = stringResource(ATMR.strings.gemini_models_missing_key_hint),
        emptyText = stringResource(ATMR.strings.gemini_models_empty),
        initialModels = GeminiModelsCatalog.curatedDefaults.map { it.toDialogItem() },
        fetchModels = { key -> GeminiModelsCatalog.fetchModels(key).map { it.toDialogItem() } },
        showFreeBadge = false,
        onDismissRequest = onDismissRequest,
    )
}

private fun GeminiModelInfo.toDialogItem() = LlmModelDialogItem(
    id = id,
    name = name,
    contextLength = contextLength,
    supportsVision = supportsVision,
    isRecommended = isRecommended,
)
