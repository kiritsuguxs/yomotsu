package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.translation.translator.GroqModelInfo
import eu.kanade.translation.translator.GroqModelsCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.core.common.preference.Preference
import tachiyomi.i18n.MR
import tachiyomi.i18n.at.ATMR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
internal fun GroqModelsDialog(
    preference: Preference<String>,
    apiKey: String,
    onDismissRequest: () -> Unit,
) {
    val currentModel by preference.collectAsState()
    var models by remember { mutableStateOf(GroqModelsCatalog.curatedDefaults) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var visionOnly by remember { mutableStateOf(false) }

    LaunchedEffect(reloadNonce, apiKey) {
        isLoading = true
        errorMessage = null
        val result = runCatching {
            withContext(Dispatchers.IO) {
                GroqModelsCatalog.fetchModels(apiKey)
            }
        }
        result.onSuccess { fetched ->
            models = fetched
        }.onFailure { error ->
            errorMessage = error.message
        }
        isLoading = false
    }

    val filteredModels = remember(models, searchQuery, visionOnly) {
        val needle = searchQuery.trim()
        models.filter { model ->
            (!visionOnly || model.supportsVision) &&
                (
                    needle.isEmpty() ||
                        model.name.contains(needle, ignoreCase = true) ||
                        model.id.contains(needle, ignoreCase = true)
                    )
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(ATMR.strings.groq_models_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(text = stringResource(ATMR.strings.groq_models_search)) },
                    leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Filled.Close, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = visionOnly,
                        onClick = { visionOnly = !visionOnly },
                        label = { Text(text = stringResource(ATMR.strings.groq_models_vision_only)) },
                    )
                    TextButton(
                        enabled = !isLoading,
                        onClick = { reloadNonce++ },
                    ) {
                        Text(text = stringResource(ATMR.strings.groq_models_refresh))
                    }
                }

                if (!isLoading) {
                    when {
                        errorMessage != null -> {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(ATMR.strings.groq_models_error),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                        errorMessage?.takeIf { it.isNotBlank() }?.let { detail ->
                                            Text(
                                                text = detail,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    TextButton(onClick = { reloadNonce++ }) {
                                        Text(text = stringResource(ATMR.strings.groq_models_retry))
                                    }
                                }
                            }
                        }
                        apiKey.isBlank() -> {
                            Text(
                                text = stringResource(ATMR.strings.groq_models_missing_key_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 360.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        isLoading -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                Text(
                                    text = stringResource(ATMR.strings.groq_models_loading),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        filteredModels.isEmpty() -> {
                            Text(
                                text = stringResource(ATMR.strings.groq_models_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> {
                            val listState = rememberLazyListState()
                            Box(modifier = Modifier.fillMaxWidth()) {
                                ScrollbarLazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    items(
                                        items = filteredModels,
                                        key = { it.id },
                                    ) { model ->
                                        GroqModelRow(
                                            model = model,
                                            isSelected = currentModel == model.id,
                                            onSelect = {
                                                preference.set(model.id)
                                                onDismissRequest()
                                            },
                                        )
                                    }
                                }
                                if (listState.canScrollBackward) {
                                    HorizontalDivider(modifier = Modifier.align(Alignment.TopCenter))
                                }
                                if (listState.canScrollForward) {
                                    HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun GroqModelRow(
    model: GroqModelInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .selectable(
                selected = isSelected,
                onClick = onSelect,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = model.id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                if (model.isRecommended) {
                    GroqModelBadge(
                        text = stringResource(ATMR.strings.groq_models_badge_recommended),
                        highlighted = true,
                    )
                } else {
                    GroqModelBadge(
                        text = stringResource(ATMR.strings.groq_models_badge_free),
                        highlighted = false,
                    )
                }
                if (model.supportsVision) {
                    GroqModelBadge(
                        text = stringResource(ATMR.strings.groq_models_badge_vision),
                        highlighted = true,
                    )
                }
                GroqModelsCatalog.formatContextLength(model.contextLength)?.let { ctx ->
                    GroqModelBadge(text = ctx, highlighted = false)
                }
            }
        }
    }
}

@Composable
private fun GroqModelBadge(
    text: String,
    highlighted: Boolean,
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
