package eu.kanade.translation.presentation

import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.toFontFamily
import androidx.core.view.isVisible
import eu.kanade.translation.data.TranslationFont
import eu.kanade.translation.detection.DbnetCleanupMask
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationLayoutCollisionResolver
import eu.kanade.translation.model.TranslationPageEditor
import eu.kanade.translation.model.TranslationRegion
import kotlinx.coroutines.flow.MutableStateFlow

class PagerTranslationsView : AbstractComposeView {

    private val translation: PageTranslation
    private val font: TranslationFont
    private val fontFamily: FontFamily
    private val editor: TranslationPageEditor?

    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(
        context,
        attrs,
        defStyleAttr,
    ) {
        translation = PageTranslation.EMPTY
        font = TranslationFont.ANIME_ACE
        editor = null
        fontFamily = Font(resId = font.res, weight = FontWeight.Bold).toFontFamily()
    }

    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
        translation: PageTranslation,
        font: TranslationFont? = null,
        editor: TranslationPageEditor? = null,
    ) : super(context, attrs, defStyleAttr) {
        this.translation = translation
        this.font = font ?: TranslationFont.ANIME_ACE
        this.editor = editor
        fontFamily = Font(resId = this.font.res, weight = FontWeight.Bold).toFontFamily()
    }

    val scaleState = MutableStateFlow(1f)
    val viewTLState = MutableStateFlow(PointF())
    private var refreshRevision by mutableStateOf(0)
    private var editingBlockIndexState by mutableStateOf<Int?>(null)

    fun refreshContent() {
        refreshRevision++
    }

    fun openBlockEditorAt(imageX: Float, imageY: Float): Boolean {
        if (!isVisible || editor == null || translation.blocks.isEmpty()) return false
        val layouts = TranslationLayoutCollisionResolver.computeNonOverlappingLayouts(
            translation.blocks,
            translation.imgWidth,
            translation.imgHeight,
        )
        val hitIndex = translation.blocks.indices.reversed().firstOrNull { index ->
            val block = translation.blocks[index]
            val region = layouts.getOrNull(index)
                ?: block.layoutRegion
                ?: TranslationRegion(block.x, block.y, block.width, block.height)
            imageX in region.x..(region.x + region.width) &&
                imageY in region.y..(region.y + region.height)
        } ?: return false
        editingBlockIndexState = hitIndex
        return true
    }

    @Composable
    override fun Content() {
        val viewTLCompose = viewTLState.collectAsState()
        val scaleCompose = scaleState.collectAsState()
        var contentRevision by remember { mutableStateOf(0) }
        val pageW = translation.imgWidth.takeIf { it > 0f } ?: 1f
        val pageH = translation.imgHeight.takeIf { it > 0f } ?: 1f
        Box(
            modifier = Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(pageW.pxToDp(), pageH.pxToDp())
                .graphicsLayer {
                    val tl = viewTLCompose.value
                    val s = scaleCompose.value
                    translationX = tl.x
                    translationY = tl.y
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            val visibleRevision = contentRevision + refreshRevision
            key(visibleRevision) {
                TextBlockBackground(1f)
                TextBlockContent(
                    zoomScale = 1f,
                    contentRevision = visibleRevision,
                    onEdit = editor?.let {
                        { index -> editingBlockIndexState = index }
                    },
                )
            }
        }
        val blockIndex = editingBlockIndexState
        if (blockIndex != null) {
            val block = translation.blocks.getOrNull(blockIndex)
            val activeEditor = editor
            if (block != null && activeEditor != null) {
                TranslationBlockEditorDialog(
                    blockIndex = blockIndex,
                    block = block,
                    editor = activeEditor,
                    onTranslationChanged = { translatedText ->
                        block.translation = translatedText
                        contentRevision++
                    },
                    onBlockDeleted = {
                        if (blockIndex in translation.blocks.indices) {
                            translation.blocks.removeAt(blockIndex)
                        }
                        contentRevision++
                        editingBlockIndexState = null
                    },
                    onDismissRequest = { editingBlockIndexState = null },
                )
            }
        }
    }

    @Composable
    private fun TextBlockBackground(zoomScale: Float) {
        val experimentalMaskPageValid = DbnetCleanupMask.areValidForPage(
            translation.blocks.asSequence().mapNotNull { it.dbnetCleanupMask },
            translation.imgWidth,
            translation.imgHeight,
        )
        translation.blocks.filter { it.translation.isNotBlank() }.forEach { block ->
            TranslationCleanupBlock(
                block = block,
                scaleFactor = zoomScale,
                pageWidth = translation.imgWidth,
                pageHeight = translation.imgHeight,
                experimentalMaskPageValid = experimentalMaskPageValid,
            )
        }
    }

    @Composable
    private fun TextBlockContent(
        zoomScale: Float,
        contentRevision: Int,
        onEdit: ((Int) -> Unit)?,
    ) {
        key(contentRevision) {
            val resolvedLayouts = remember(contentRevision, translation.blocks.size) {
                TranslationLayoutCollisionResolver.computeNonOverlappingLayouts(
                    translation.blocks,
                    translation.imgWidth,
                    translation.imgHeight,
                )
            }
            translation.blocks.forEachIndexed { index, block ->
                SmartTranslationBlock(
                    block = block,
                    scaleFactor = zoomScale,
                    pageWidth = translation.imgWidth,
                    pageHeight = translation.imgHeight,
                    fontFamily = fontFamily,
                    layoutRegionOverride = resolvedLayouts.getOrNull(index),
                    onLongClick = onEdit?.let { { it(index) } },
                )
            }
        }
    }

    fun show() {
        isVisible = true
    }
    fun hide() {
        isVisible = false
    }
}
