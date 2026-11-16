package eu.kanade.translation.recognizer

import kotlin.math.abs

internal data class MlKitLineInput(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val symbolWidths: List<Float>,
    val symbolHeights: List<Float>,
    val angle: Float,
)

/**
 * Splits multi-lobe ML Kit TextBlocks (such as diagonal figure-8 double balloons)
 * into separate [OcrTextBlock]s while keeping normal centered multi-line paragraphs intact.
 */
internal object MlKitLineClusterer {

    fun clusterLines(
        lines: List<MlKitLineInput>,
        offsetY: Float = 0f,
    ): List<OcrTextBlock> {
        val valid = lines
            .filter { it.text.isNotBlank() && it.width > 0f && it.height > 0f }
            .sortedWith(compareBy<MlKitLineInput> { it.y }.thenBy { it.x })
        if (valid.isEmpty()) return emptyList()

        val clusters = mutableListOf<MutableList<MlKitLineInput>>()
        for (line in valid) {
            val current = clusters.lastOrNull()
            if (current == null || shouldSplitCluster(current, line)) {
                clusters += mutableListOf(line)
            } else {
                current += line
            }
        }

        return clusters.map { cluster ->
            val minX = cluster.minOf { it.x }
            val minY = cluster.minOf { it.y }
            val maxX = cluster.maxOf { it.x + it.width }
            val maxY = cluster.maxOf { it.y + it.height }
            val symWidths = cluster.flatMap { it.symbolWidths }.filter { it > 0f }.sorted()
            val symHeights = cluster.flatMap { it.symbolHeights }.filter { it > 0f }.sorted()
            val medianSymbolWidth = if (symWidths.isNotEmpty()) symWidths[symWidths.size / 2] else 1f
            val medianSymbolHeight = if (symHeights.isNotEmpty()) symHeights[symHeights.size / 2] else 1f

            OcrTextBlock(
                text = cluster.joinToString("\n") { it.text.trim() },
                x = minX,
                y = minY + offsetY,
                width = (maxX - minX).coerceAtLeast(1f),
                height = (maxY - minY).coerceAtLeast(1f),
                symbolWidth = medianSymbolWidth,
                symbolHeight = medianSymbolHeight,
                angle = cluster.first().angle,
            )
        }
    }

    private fun shouldSplitCluster(
        cluster: List<MlKitLineInput>,
        next: MlKitLineInput,
    ): Boolean {
        val clusterLeft = cluster.minOf { it.x }
        val clusterRight = cluster.maxOf { it.x + it.width }
        val clusterBottom = cluster.maxOf { it.y + it.height }
        val clusterWidth = (clusterRight - clusterLeft).coerceAtLeast(1f)
        val clusterCenterX = (clusterLeft + clusterRight) / 2f

        val nextRight = next.x + next.width
        val nextWidth = next.width.coerceAtLeast(1f)
        val nextCenterX = next.x + nextWidth / 2f

        val horizontalOverlap = (minOf(clusterRight, nextRight) - maxOf(clusterLeft, next.x)).coerceAtLeast(0f)
        val overlapRatio = horizontalOverlap / minOf(clusterWidth, nextWidth).coerceAtLeast(1f)
        val centerDiffX = abs(clusterCenterX - nextCenterX)
        val maxW = maxOf(clusterWidth, nextWidth)

        // Two horizontally offset lobes (e.g. diagonal figure-8 balloons) share little X span.
        if (overlapRatio < MIN_LOBE_HORIZONTAL_OVERLAP && centerDiffX > maxW * MIN_LOBE_CENTER_SHIFT_RATIO) {
            return true
        }

        val lastLine = cluster.last()
        val lastEndsSentence = endsWithTerminalPunctuation(lastLine.text)
        if (lastEndsSentence && overlapRatio < SENTENCE_SPLIT_HORIZONTAL_OVERLAP && centerDiffX > maxW * SENTENCE_SPLIT_CENTER_RATIO) {
            return true
        }

        val verticalGap = (next.y - clusterBottom).coerceAtLeast(0f)
        val symH = medianOrFallbackHeight(cluster, next)
        if (lastEndsSentence && verticalGap > symH * TERMINAL_PUNCTUATION_GAP_MULTIPLIER) {
            return true
        }

        return false
    }

    private fun endsWithTerminalPunctuation(text: String): Boolean {
        val trimmed = text.trimEnd().dropLastWhile { it in "\"'”’)]}" }
        return trimmed.lastOrNull()?.let { it in ".!?…" } == true
    }

    private fun medianOrFallbackHeight(cluster: List<MlKitLineInput>, next: MlKitLineInput): Float {
        val heights = (cluster.flatMap { it.symbolHeights } + next.symbolHeights).filter { it > 0f }.sorted()
        if (heights.isNotEmpty()) return heights[heights.size / 2].coerceAtLeast(1f)
        return minOf(cluster.last().height, next.height).coerceAtLeast(1f)
    }

    private const val MIN_LOBE_HORIZONTAL_OVERLAP = 0.15f
    private const val MIN_LOBE_CENTER_SHIFT_RATIO = 0.55f
    private const val SENTENCE_SPLIT_HORIZONTAL_OVERLAP = 0.35f
    private const val SENTENCE_SPLIT_CENTER_RATIO = 0.45f
    private const val TERMINAL_PUNCTUATION_GAP_MULTIPLIER = 1.4f
}
