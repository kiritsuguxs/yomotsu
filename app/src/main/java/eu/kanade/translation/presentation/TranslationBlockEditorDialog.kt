package eu.kanade.translation.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.TranslationPageEditor
import eu.kanade.translation.model.applyCustomizationGeometry
import tachiyomi.i18n.MR
import tachiyomi.i18n.at.ATMR
import tachiyomi.presentation.core.i18n.stringResource

private enum class BalloonBgColor {
    WHITE, BLACK, TRANSPARENT
}

private enum class BalloonFgColor {
    BLACK, WHITE
}

internal fun formatHorizontalPositionOffset(offsetPercent: Int): String = when {
    offsetPercent < 0 -> "$offsetPercent% (←)"
    offsetPercent > 0 -> "+$offsetPercent% (→)"
    else -> "0%"
}

internal fun formatVerticalPositionOffset(offsetPercent: Int): String = when {
    offsetPercent < 0 -> "$offsetPercent% (↑)"
    offsetPercent > 0 -> "+$offsetPercent% (↓)"
    else -> "0%"
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

    val hasAotPatch = !block.aotPatchBase64.isNullOrBlank()
    val initialBg = remember(blockIndex) {
        if (hasAotPatch && block.backgroundColor == null) {
            null
        } else {
            when (block.backgroundColor) {
                Color.Black.toArgb() -> BalloonBgColor.BLACK
                Color.Transparent.toArgb(), 0 -> BalloonBgColor.TRANSPARENT
                else -> BalloonBgColor.WHITE
            }
        }
    }
    val initialFg = remember(blockIndex) {
        when (block.foregroundColor) {
            Color.White.toArgb() -> BalloonFgColor.WHITE
            else -> if (initialBg == BalloonBgColor.BLACK) BalloonFgColor.WHITE else BalloonFgColor.BLACK
        }
    }
    var selectedBg by remember(blockIndex) { mutableStateOf<BalloonBgColor?>(initialBg) }
    var selectedFg by remember(blockIndex) { mutableStateOf(initialFg) }
    var bgTouched by remember(blockIndex) { mutableStateOf(false) }
    var fgTouched by remember(blockIndex) { mutableStateOf(false) }

    var widthScalePercent by remember(blockIndex) { mutableIntStateOf(100) }
    var heightScalePercent by remember(blockIndex) { mutableIntStateOf(100) }
    var offsetXPercent by remember(blockIndex) { mutableIntStateOf(0) }
    var offsetYPercent by remember(blockIndex) { mutableIntStateOf(0) }

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

        val newBgColor = if (bgTouched) {
            when (selectedBg) {
                BalloonBgColor.WHITE -> Color.White.toArgb()
                BalloonBgColor.BLACK -> Color.Black.toArgb()
                BalloonBgColor.TRANSPARENT -> Color.Transparent.toArgb()
                null -> block.backgroundColor
            }
        } else {
            block.backgroundColor
        }
        val newFgColor = if (fgTouched || bgTouched) {
            when (selectedFg) {
                BalloonFgColor.BLACK -> Color.Black.toArgb()
                BalloonFgColor.WHITE -> Color.White.toArgb()
            }
        } else {
            block.foregroundColor
        }
        val wScale = widthScalePercent / 100f
        val hScale = heightScalePercent / 100f
        val offX = offsetXPercent / 100f
        val offY = offsetYPercent / 100f

        editor.updateBlockCustomization(
            blockIndex,
            cleanTranslation,
            newBgColor,
            newFgColor,
            wScale,
            hScale,
            offX,
            offY,
        ) { result ->
            isWorking = false
            result.onSuccess { storedTranslation ->
                if (newBgColor != block.backgroundColor) {
                    block.aotPatchBase64 = null
                    block.aotPatchRegion = null
                }
                block.translation = storedTranslation
                block.backgroundColor = newBgColor
                block.foregroundColor = newFgColor
                block.applyCustomizationGeometry(wScale, hScale, offX, offY)
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
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterChip(
                        selected = selectedBg == BalloonBgColor.WHITE,
                        onClick = {
                            bgTouched = true
                            selectedBg = BalloonBgColor.WHITE
                            selectedFg = BalloonFgColor.BLACK
                        },
                        label = {
                            Text(
                                text = stringResource(ATMR.strings.translation_editor_color_white),
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                    )
                    FilterChip(
                        selected = selectedBg == BalloonBgColor.BLACK,
                        onClick = {
                            bgTouched = true
                            selectedBg = BalloonBgColor.BLACK
                            selectedFg = BalloonFgColor.WHITE
                        },
                        label = {
                            Text(
                                text = stringResource(ATMR.strings.translation_editor_color_black),
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                    )
                    FilterChip(
                        selected = selectedBg == BalloonBgColor.TRANSPARENT,
                        onClick = {
                            bgTouched = true
                            selectedBg = BalloonBgColor.TRANSPARENT
                        },
                        label = {
                            Text(
                                text = stringResource(ATMR.strings.translation_editor_color_transparent),
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                    )
                }

                if (selectedBg == BalloonBgColor.TRANSPARENT || selectedBg == null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(ATMR.strings.translation_editor_text_color),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilterChip(
                            selected = selectedFg == BalloonFgColor.BLACK,
                            onClick = {
                                fgTouched = true
                                selectedFg = BalloonFgColor.BLACK
                            },
                            label = {
                                Text(
                                    text = stringResource(ATMR.strings.translation_editor_color_black),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            },
                        )
                        FilterChip(
                            selected = selectedFg == BalloonFgColor.WHITE,
                            onClick = {
                                fgTouched = true
                                selectedFg = BalloonFgColor.WHITE
                            },
                            label = {
                                Text(
                                    text = stringResource(ATMR.strings.translation_editor_color_white),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            },
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
                        modifier = Modifier.weight(1f),
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
                        modifier = Modifier.weight(1f),
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

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(ATMR.strings.translation_editor_position),
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
                        text = stringResource(
                            ATMR.strings.translation_editor_move_horizontal,
                            formatHorizontalPositionOffset(offsetXPercent),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (offsetXPercent > -100) offsetXPercent -= 10 },
                            enabled = !isWorking && offsetXPercent > -100,
                        ) {
                            Icon(Icons.Outlined.Remove, contentDescription = "←")
                        }
                        IconButton(
                            onClick = { if (offsetXPercent < 100) offsetXPercent += 10 },
                            enabled = !isWorking && offsetXPercent < 100,
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "→")
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(
                            ATMR.strings.translation_editor_move_vertical,
                            formatVerticalPositionOffset(offsetYPercent),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (offsetYPercent > -100) offsetYPercent -= 10 },
                            enabled = !isWorking && offsetYPercent > -100,
                        ) {
                            Icon(Icons.Outlined.Remove, contentDescription = "↑")
                        }
                        IconButton(
                            onClick = { if (offsetYPercent < 100) offsetYPercent += 10 },
                            enabled = !isWorking && offsetYPercent < 100,
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "↓")
                        }
                    }
                }
            }
        },
        confirmButton = {
            val hasChanges = translation.trim() != block.translation.trim() ||
                bgTouched ||
                fgTouched ||
                widthScalePercent != 100 ||
                heightScalePercent != 100 ||
                offsetXPercent != 0 ||
                offsetYPercent != 0
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
