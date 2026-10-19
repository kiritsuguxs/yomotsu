package eu.kanade.translation.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.translation.model.CURRENT_TRANSLATION_GEOMETRY_VERSION
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.TranslationPageEditor
import eu.kanade.translation.model.TranslationRegion
import eu.kanade.translation.model.sourceRegion
import tachiyomi.i18n.MR
import tachiyomi.i18n.at.ATMR
import tachiyomi.presentation.core.i18n.stringResource

private enum class BalloonBgColor {
    WHITE, BLACK, TRANSPARENT
}

private enum class BalloonFgColor {
    BLACK, WHITE
}

@Composable
fun TranslationBlockEditorDialog(
    blockIndex: Int,
    block: TranslationBlock,
    editor: TranslationPageEditor,
    onTranslationChanged: (String) -> Unit,
    onBlockDeleted: () -> Unit = {},
    onDismissRequest: () -> Unit,
) {
    var translation by remember(blockIndex) { mutableStateOf(block.translation) }
    var isWorking by remember(blockIndex) { mutableStateOf(false) }
    var errorMessage by remember(blockIndex) { mutableStateOf<String?>(null) }

    val initialBg = remember(blockIndex) {
        when (block.backgroundColor) {
            Color.Black.toArgb() -> BalloonBgColor.BLACK
            Color.Transparent.toArgb(), 0 -> BalloonBgColor.TRANSPARENT
            else -> BalloonBgColor.WHITE
        }
    }
    val initialFg = remember(blockIndex) {
        when (block.foregroundColor) {
            Color.White.toArgb() -> BalloonFgColor.WHITE
            else -> if (initialBg == BalloonBgColor.BLACK) BalloonFgColor.WHITE else BalloonFgColor.BLACK
        }
    }
    var selectedBg by remember(blockIndex) { mutableStateOf(initialBg) }
    var selectedFg by remember(blockIndex) { mutableStateOf(initialFg) }

    var widthScalePercent by remember(blockIndex) { mutableIntStateOf(100) }
    var heightScalePercent by remember(blockIndex) { mutableIntStateOf(100) }

    fun handleDelete() {
        isWorking = true
        errorMessage = null
        editor.deleteTranslation(blockIndex) { result ->
            isWorking = false
            result.onSuccess {
                onBlockDeleted()
                onDismissRequest()
            }.onFailure { error ->
                errorMessage = error.localizedMessage?.takeIf(String::isNotBlank) ?: ""
            }
        }
    }

    fun handleSave() {
        val cleanTranslation = translation.trim()
        if (cleanTranslation.isBlank()) return
        isWorking = true
        errorMessage = null

        val newBgColor = when (selectedBg) {
            BalloonBgColor.WHITE -> Color.White.toArgb()
            BalloonBgColor.BLACK -> Color.Black.toArgb()
            BalloonBgColor.TRANSPARENT -> Color.Transparent.toArgb()
        }
        val newFgColor = when (selectedFg) {
            BalloonFgColor.BLACK -> Color.Black.toArgb()
            BalloonFgColor.WHITE -> Color.White.toArgb()
        }
        val wScale = widthScalePercent / 100f
        val hScale = heightScalePercent / 100f

        editor.updateBlockCustomization(
            blockIndex,
            cleanTranslation,
            newBgColor,
            newFgColor,
            wScale,
            hScale,
        ) { result ->
            isWorking = false
            result.onSuccess { storedTranslation ->
                block.translation = storedTranslation
                block.backgroundColor = newBgColor
                block.foregroundColor = newFgColor
                if (wScale != 1.0f || hScale != 1.0f) {
                    val currentLayout = block.layoutRegion ?: block.sourceRegion()
                    val newWidth = (currentLayout.width * wScale).coerceAtLeast(10f)
                    val newHeight = (currentLayout.height * hScale).coerceAtLeast(10f)
                    val diffX = (newWidth - currentLayout.width) / 2f
                    val diffY = (newHeight - currentLayout.height) / 2f
                    val newX = (currentLayout.x - diffX).coerceAtLeast(0f)
                    val newY = (currentLayout.y - diffY).coerceAtLeast(0f)
                    val newRegion = TranslationRegion(newX, newY, newWidth, newHeight)
                    block.layoutRegion = newRegion
                    block.cleanupRegion = newRegion
                    block.geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION
                }
                onTranslationChanged(storedTranslation)
                onDismissRequest()
            }.onFailure { error ->
                errorMessage = error.localizedMessage?.takeIf(String::isNotBlank) ?: ""
            }
        }
    }

    fun handleRetranslate() {
        isWorking = true
        errorMessage = null
        editor.retranslate(blockIndex) { result ->
            isWorking = false
            result.onSuccess { storedTranslation ->
                translation = storedTranslation
                block.translation = storedTranslation
                onTranslationChanged(storedTranslation)
            }.onFailure { error ->
                errorMessage = error.localizedMessage?.takeIf(String::isNotBlank) ?: ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismissRequest() },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(ATMR.strings.translation_editor_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(
                    onClick = ::handleDelete,
                    enabled = !isWorking,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(ATMR.strings.translation_editor_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (block.text.isNotBlank()) {
                    Text(
                        text = stringResource(ATMR.strings.translation_editor_original, block.text),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = translation,
                    onValueChange = {
                        translation = it
                        errorMessage = null
                    },
                    enabled = !isWorking,
                    label = { Text(stringResource(ATMR.strings.translation_editor_field)) },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { message ->
                        {
                            val visibleMessage = if (message.isBlank()) {
                                stringResource(ATMR.strings.translation_editor_error)
                            } else {
                                message
                            }
                            Text(visibleMessage)
                        }
                    },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(ATMR.strings.translation_editor_background),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedBg == BalloonBgColor.WHITE,
                        onClick = {
                            selectedBg = BalloonBgColor.WHITE
                            selectedFg = BalloonFgColor.BLACK
                        },
                        label = { Text(stringResource(ATMR.strings.translation_editor_color_white)) },
                    )
                    FilterChip(
                        selected = selectedBg == BalloonBgColor.BLACK,
                        onClick = {
                            selectedBg = BalloonBgColor.BLACK
                            selectedFg = BalloonFgColor.WHITE
                        },
                        label = { Text(stringResource(ATMR.strings.translation_editor_color_black)) },
                    )
                    FilterChip(
                        selected = selectedBg == BalloonBgColor.TRANSPARENT,
                        onClick = {
                            selectedBg = BalloonBgColor.TRANSPARENT
                        },
                        label = { Text(stringResource(ATMR.strings.translation_editor_color_transparent)) },
                    )
                }

                if (selectedBg == BalloonBgColor.TRANSPARENT) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(ATMR.strings.translation_editor_text_color),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selectedFg == BalloonFgColor.BLACK,
                            onClick = { selectedFg = BalloonFgColor.BLACK },
                            label = { Text(stringResource(ATMR.strings.translation_editor_color_black)) },
                        )
                        FilterChip(
                            selected = selectedFg == BalloonFgColor.WHITE,
                            onClick = { selectedFg = BalloonFgColor.WHITE },
                            label = { Text(stringResource(ATMR.strings.translation_editor_color_white)) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(ATMR.strings.translation_editor_size),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(ATMR.strings.translation_editor_width, widthScalePercent),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (widthScalePercent > 50) widthScalePercent -= 10 },
                            enabled = !isWorking && widthScalePercent > 50,
                        ) {
                            Icon(Icons.Outlined.Remove, contentDescription = "-")
                        }
                        IconButton(
                            onClick = { if (widthScalePercent < 200) widthScalePercent += 10 },
                            enabled = !isWorking && widthScalePercent < 200,
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "+")
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(ATMR.strings.translation_editor_height, heightScalePercent),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (heightScalePercent > 50) heightScalePercent -= 10 },
                            enabled = !isWorking && heightScalePercent > 50,
                        ) {
                            Icon(Icons.Outlined.Remove, contentDescription = "-")
                        }
                        IconButton(
                            onClick = { if (heightScalePercent < 200) heightScalePercent += 10 },
                            enabled = !isWorking && heightScalePercent < 200,
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "+")
                        }
                    }
                }
            }
        },
        confirmButton = {
            val hasChanges = translation.trim() != block.translation.trim() ||
                selectedBg != initialBg ||
                selectedFg != initialFg ||
                widthScalePercent != 100 ||
                heightScalePercent != 100
            TextButton(
                enabled = !isWorking && translation.isNotBlank() && hasChanges,
                onClick = ::handleSave,
            ) {
                Text(stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            Row {
                if (block.text.isNotBlank()) {
                    val retranslateLabel = if (block.translation.isBlank()) {
                        stringResource(ATMR.strings.translation_editor_translate)
                    } else {
                        stringResource(ATMR.strings.translation_editor_retranslate)
                    }
                    TextButton(
                        enabled = !isWorking,
                        onClick = ::handleRetranslate,
                    ) {
                        Text(
                            if (isWorking) {
                                stringResource(ATMR.strings.translation_editor_retranslating)
                            } else {
                                retranslateLabel
                            },
                        )
                    }
                }
                TextButton(
                    enabled = !isWorking,
                    onClick = onDismissRequest,
                ) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            }
        },
    )
}
