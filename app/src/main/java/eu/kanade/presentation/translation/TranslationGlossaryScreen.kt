package eu.kanade.presentation.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.at.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.translation.memory.GlossarySaveResult
import eu.kanade.translation.memory.TranslationGlossaryManager
import eu.kanade.translation.memory.TranslationMemoryEntry
import eu.kanade.translation.memory.TranslationMemoryEntryType
import eu.kanade.translation.translator.ComicTranslationContext

class TranslationGlossaryScreen(
    private val mangaTitle: String,
) : Screen() {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val translationContext = remember(mangaTitle) {
            ComicTranslationContext(mangaTitle = mangaTitle, chapterName = "")
        }
        var entries by remember { mutableStateOf(TranslationGlossaryManager.list(translationContext)) }
        var source by remember { mutableStateOf("") }
        var target by remember { mutableStateOf("") }
        var message by remember { mutableStateOf<String?>(null) }
        var showClearDialog by remember { mutableStateOf(false) }
        var showBulkDialog by remember { mutableStateOf(false) }
        var entryType by remember { mutableStateOf(TranslationMemoryEntryType.TERM) }
        var isProtected by remember { mutableStateOf(false) }
        var learnedCorrectionCount by remember {
            mutableStateOf(TranslationGlossaryManager.learnedCorrectionCount(translationContext))
        }

        fun refresh() {
            entries = TranslationGlossaryManager.list(translationContext)
            learnedCorrectionCount = TranslationGlossaryManager.learnedCorrectionCount(translationContext)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(AYMR.strings.translation_glossary_title)) },
                    navigationIcon = {
                        TextButton(onClick = navigator::pop) {
                            Text(stringResource(MR.strings.action_webview_back))
                        }
                    },
                    actions = {
                        if (entries.isNotEmpty()) {
                            TextButton(onClick = { showClearDialog = true }) {
                                Text(stringResource(MR.strings.action_clear))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { contentPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Column {
                        Text(
                            text = mangaTitle,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                        Text(
                            text = stringResource(AYMR.strings.translation_glossary_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        if (learnedCorrectionCount > 0) {
                            Text(
                                text = stringResource(AYMR.strings.translation_glossary_learned_corrections, learnedCorrectionCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }

                        GlossaryTypeSelector(
                            selected = entryType,
                            onSelected = {
                                entryType = it
                                if (it == TranslationMemoryEntryType.NAME) isProtected = true
                            },
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Checkbox(
                                checked = isProtected,
                                onCheckedChange = { isProtected = it },
                            )
                            Text(
                                text = stringResource(AYMR.strings.translation_glossary_protect_summary),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        OutlinedTextField(
                            value = source,
                            onValueChange = { source = it },
                            label = { Text(stringResource(AYMR.strings.translation_glossary_original_text)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = target,
                            onValueChange = { target = it },
                            label = { Text(stringResource(AYMR.strings.translation_glossary_preferred_translation)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                        ) {
                            Button(
                                onClick = {
                                    val (msg, isSuccess) = when (
                                        TranslationGlossaryManager.save(
                                            context = translationContext,
                                            source = source,
                                            target = target,
                                            type = entryType,
                                            isProtected = isProtected,
                                        )
                                    ) {
                                        GlossarySaveResult.CREATED -> context.stringResource(AYMR.strings.translation_glossary_result_created) to true
                                        GlossarySaveResult.UPDATED -> context.stringResource(AYMR.strings.translation_glossary_result_updated) to true
                                        GlossarySaveResult.INVALID_SOURCE -> context.stringResource(AYMR.strings.translation_glossary_result_invalid_source) to false
                                        GlossarySaveResult.INVALID_TARGET -> context.stringResource(AYMR.strings.translation_glossary_result_invalid_target) to false
                                        GlossarySaveResult.SAME_TEXT -> context.stringResource(AYMR.strings.translation_glossary_result_same_text) to false
                                    }
                                    message = msg
                                    if (isSuccess) {
                                        source = ""
                                        target = ""
                                        refresh()
                                    }
                                },
                            ) {
                                Text(stringResource(AYMR.strings.translation_glossary_save_term))
                            }
                            TextButton(onClick = { showBulkDialog = true }) {
                                Text(stringResource(AYMR.strings.translation_glossary_add_multiple))
                            }
                        }
                        message?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                    }
                }

                if (entries.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(AYMR.strings.translation_glossary_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                } else {
                    items(entries, key = TranslationMemoryEntry::source) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.source, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = entry.type.displayLabel(entry.isProtected),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = entry.target,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = {
                                    TranslationGlossaryManager.remove(translationContext, entry.source)
                                    refresh()
                                    message = context.stringResource(AYMR.strings.translation_glossary_term_removed)
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(AYMR.strings.translation_glossary_remove_term),
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        text = "",
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text(stringResource(AYMR.strings.translation_glossary_clear_confirm_title)) },
                text = { Text(stringResource(AYMR.strings.translation_glossary_clear_confirm_text)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            TranslationGlossaryManager.clear(translationContext)
                            refresh()
                            showClearDialog = false
                            message = context.stringResource(AYMR.strings.translation_glossary_cleared)
                        },
                    ) {
                        Text(stringResource(MR.strings.action_clear))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        if (showBulkDialog) {
            BulkGlossaryDialog(
                type = entryType,
                isProtected = isProtected,
                onDismiss = { showBulkDialog = false },
                onSave = { bulkText ->
                    val result = TranslationGlossaryManager.saveMany(
                        context = translationContext,
                        text = bulkText,
                        type = entryType,
                        isProtected = isProtected,
                    )
                    refresh()
                    showBulkDialog = false
                    message = when {
                        result.saved == 0 -> context.stringResource(AYMR.strings.translation_glossary_bulk_none)
                        result.skipped > 0 -> context.stringResource(AYMR.strings.translation_glossary_bulk_saved_skipped, result.saved, result.skipped)
                        else -> context.stringResource(AYMR.strings.translation_glossary_bulk_saved, result.saved)
                    }
                },
            )
        }
    }
}

@Composable
private fun GlossaryTypeSelector(
    selected: TranslationMemoryEntryType,
    onSelected: (TranslationMemoryEntryType) -> Unit,
) {
    Text(stringResource(AYMR.strings.translation_glossary_type_label), style = MaterialTheme.typography.labelLarge)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlossaryTypeChip(TranslationMemoryEntryType.TERM, stringResource(AYMR.strings.translation_glossary_type_term), selected, onSelected)
            GlossaryTypeChip(TranslationMemoryEntryType.NAME, stringResource(AYMR.strings.translation_glossary_type_name), selected, onSelected)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlossaryTypeChip(TranslationMemoryEntryType.TITLE, stringResource(AYMR.strings.translation_glossary_type_title), selected, onSelected)
            GlossaryTypeChip(TranslationMemoryEntryType.TECHNIQUE, stringResource(AYMR.strings.translation_glossary_type_technique), selected, onSelected)
        }
    }
}

@Composable
private fun GlossaryTypeChip(
    type: TranslationMemoryEntryType,
    label: String,
    selected: TranslationMemoryEntryType,
    onSelected: (TranslationMemoryEntryType) -> Unit,
) {
    FilterChip(
        selected = selected == type,
        onClick = { onSelected(type) },
        label = { Text(label) },
    )
}

@Composable
private fun BulkGlossaryDialog(
    type: TranslationMemoryEntryType,
    isProtected: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val protectedNameHint = if (type == TranslationMemoryEntryType.NAME && isProtected) {
        stringResource(AYMR.strings.translation_glossary_add_multiple_hint)
    } else {
        ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AYMR.strings.translation_glossary_add_multiple_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(AYMR.strings.translation_glossary_add_multiple_description, protectedNameHint))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(AYMR.strings.translation_glossary_terms_list)) },
                    placeholder = { Text("Shadow Monarch => Monarca das Sombras") },
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onSave(text) },
            ) {
                Text(stringResource(AYMR.strings.translation_glossary_save_list))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun TranslationMemoryEntryType.displayLabel(isProtected: Boolean): String {
    val label = when (this) {
        TranslationMemoryEntryType.TERM -> stringResource(AYMR.strings.translation_glossary_type_term)
        TranslationMemoryEntryType.NAME -> stringResource(AYMR.strings.translation_glossary_type_name)
        TranslationMemoryEntryType.TITLE -> stringResource(AYMR.strings.translation_glossary_type_title)
        TranslationMemoryEntryType.TECHNIQUE -> stringResource(AYMR.strings.translation_glossary_type_technique)
        TranslationMemoryEntryType.MANUAL_CORRECTION -> stringResource(AYMR.strings.translation_glossary_type_manual_correction)
    }
    return if (isProtected) stringResource(AYMR.strings.translation_glossary_protected_suffix, label) else label
}
