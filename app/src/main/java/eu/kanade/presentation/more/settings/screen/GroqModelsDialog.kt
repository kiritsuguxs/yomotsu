package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import eu.kanade.translation.translator.GroqModelInfo
import eu.kanade.translation.translator.GroqModelsCatalog
import tachiyomi.core.common.preference.Preference
import tachiyomi.i18n.at.ATMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun GroqModelsDialog(
    preference: Preference<String>,
    apiKey: String,
    onDismissRequest: () -> Unit,
) {
    LlmModelsPickerDialog(
        preference = preference,
        apiKey = apiKey,
        title = stringResource(ATMR.strings.groq_models_title),
        loadingText = stringResource(ATMR.strings.groq_models_loading),
        errorText = stringResource(ATMR.strings.groq_models_error),
        missingKeyHint = stringResource(ATMR.strings.groq_models_missing_key_hint),
        emptyText = stringResource(ATMR.strings.groq_models_empty),
        initialModels = GroqModelsCatalog.curatedDefaults.map { it.toDialogItem() },
        fetchModels = { key -> GroqModelsCatalog.fetchModels(key).map { it.toDialogItem() } },
        showFreeBadge = false,
        onDismissRequest = onDismissRequest,
    )
}

private fun GroqModelInfo.toDialogItem() = LlmModelDialogItem(
    id = id,
    name = name,
    contextLength = contextLength,
    supportsVision = supportsVision,
    isRecommended = isRecommended,
)
