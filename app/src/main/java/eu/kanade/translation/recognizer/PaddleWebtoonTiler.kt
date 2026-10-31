package eu.kanade.translation.recognizer

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal object PaddleWebtoonTiler {

    data class TileWindow(
        val top: Int,
        val bottom: Int,
    ) {
        val height: Int get() = bottom - top
    }

    /**
     * Plans vertical tiles for tall Webtoon/Manhwa strips so PaddleOCR's 960px longest-side
     * resize never squashes page width into unreadable sub-100px blurs.
     * Ordinary manga pages return a single full-page window with zero tiling overhead.
     */
    fun planTiles(
        imageWidth: Int,
        imageHeight: Int,
        detLimitSideLen: Int = DEFAULT_DET_LIMIT_SIDE_LEN,
        targetMinDetWidth: Int = TARGET_MIN_DET_WIDTH,
    ): List<TileWindow> {
        if (imageWidth <= 0 || imageHeight <= 0) return emptyList()
        val effectiveMinWidth = min(imageWidth, targetMinDetWidth).coerceAtLeast(1)
        val maxUntiledHeight = ((detLimitSideLen.toLong() * imageWidth) / effectiveMinWidth)
            .toInt()
            .coerceAtLeast(detLimitSideLen)

        if (imageHeight <= maxUntiledHeight) {
            return listOf(TileWindow(0, imageHeight))
        }

        val tileHeight = maxUntiledHeight.coerceAtMost(imageHeight)
        val overlap = (imageWidth * OVERLAP_WIDTH_RATIO).toInt()
            .coerceIn(MIN_OVERLAP_PX, MAX_OVERLAP_PX)
            .coerceAtMost(tileHeight / 3)
        val step = (tileHeight - overlap).coerceAtLeast(1)

        val windows = ArrayList<TileWindow>()
        var top = 0
        while (top < imageHeight) {
            val remaining = imageHeight - top
            if (remaining <= tileHeight) {
                val adjustedTop = if (windows.isNotEmpty() && remaining < overlap + MIN_TAIL_HEIGHT_PX) {
                    (imageHeight - tileHeight).coerceAtLeast(0)
                } else {
                    top
                }
                val candidate = TileWindow(adjustedTop, imageHeight)
                if (windows.lastOrNull() != candidate) {
                    windows += candidate
                }
                break
            }
            windows += TileWindow(top, top + tileHeight)
            top += step
        }
        return windows
    }

    /**
     * Drops boxes truncated right against an internal tile cut edge when the adjacent
     * overlapping tile covers the same line cleanly inside its interior.
     */
    fun isClippedAtTileSeam(
        points: List<OcrPoint>,
        tile: TileWindow,
        prevTile: TileWindow?,
        nextTile: TileWindow?,
        edgeMargin: Float = SEAM_EDGE_MARGIN_PX,
    ): Boolean {
        if (points.isEmpty()) return true
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (p in points) {
            if (!p.y.isFinite()) return true
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        val pageMinY = tile.top + minY
        val pageMaxY = tile.top + maxY

        if (prevTile != null && minY <= edgeMargin && pageMaxY <= prevTile.bottom - edgeMargin) {
            return true
        }
        if (nextTile != null && maxY >= tile.height - edgeMargin && pageMinY >= nextTile.top + edgeMargin) {
            return true
        }
        return false
    }

    /**
     * Suppresses duplicate recognitions of the same line produced inside overlapping tile seams,
     * keeping the fuller, higher-confidence block.
     */
    fun deduplicateBlocks(blocks: List<OcrTextBlock>): List<OcrTextBlock> {
        if (blocks.size <= 1) return blocks
        val ranked = blocks.sortedByDescending { block ->
            val conf = (block.confidence ?: 0.5f).coerceAtLeast(0.05f)
            val area = (block.width * block.height).coerceAtLeast(1f)
            conf * sqrt(area)
        }
        val kept = ArrayList<OcrTextBlock>(blocks.size)
        for (candidate in ranked) {
            val duplicate = kept.any { existing -> areDuplicateSeamBlocks(existing, candidate) }
            if (!duplicate) {
                kept += candidate
            }
        }
        return kept.sortedWith(compareBy<OcrTextBlock> { it.y }.thenBy { it.x })
    }

    private fun areDuplicateSeamBlocks(a: OcrTextBlock, b: OcrTextBlock): Boolean {
        val overlapW = (min(a.x + a.width, b.x + b.width) - max(a.x, b.x)).coerceAtLeast(0f)
        if (overlapW <= 0f) return false
        val overlapH = (min(a.y + a.height, b.y + b.height) - max(a.y, b.y)).coerceAtLeast(0f)
        if (overlapH <= 0f) return false

        val minH = min(a.height, b.height).coerceAtLeast(1f)
        val minW = min(a.width, b.width).coerceAtLeast(1f)
        val verticalRatio = overlapH / minH
        val horizontalRatio = overlapW / minW
        if (verticalRatio >= MIN_VERTICAL_DUPLICATE_OVERLAP && horizontalRatio >= MIN_HORIZONTAL_DUPLICATE_OVERLAP) {
            return true
        }
        val minArea = min(a.width * a.height, b.width * b.height).coerceAtLeast(1f)
        return (overlapW * overlapH) / minArea >= MIN_AREA_DUPLICATE_OVERLAP
    }

    private const val DEFAULT_DET_LIMIT_SIDE_LEN = 960
    private const val TARGET_MIN_DET_WIDTH = 416
    private const val OVERLAP_WIDTH_RATIO = 0.22f
    private const val MIN_OVERLAP_PX = 128
    private const val MAX_OVERLAP_PX = 256
    private const val MIN_TAIL_HEIGHT_PX = 64
    private const val SEAM_EDGE_MARGIN_PX = 5f
    private const val MIN_VERTICAL_DUPLICATE_OVERLAP = 0.55f
    private const val MIN_HORIZONTAL_DUPLICATE_OVERLAP = 0.50f
    private const val MIN_AREA_DUPLICATE_OVERLAP = 0.50f
}
