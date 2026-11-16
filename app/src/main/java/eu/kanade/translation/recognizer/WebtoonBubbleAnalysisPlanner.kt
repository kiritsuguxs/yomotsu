package eu.kanade.translation.recognizer

import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.TranslationRegion
import eu.kanade.translation.model.clamped
import kotlin.math.max
import kotlin.math.min

/**
 * Plans full-resolution overlapping vertical slices for SpeechBubbleAnalyzer on tall
 * Webtoon / Manhwa / Manhua strips so wide-strip width is never downscaled into blurs.
 */
internal object WebtoonBubbleAnalysisPlanner {

    data class SliceWindow(
        val top: Int,
        val bottom: Int,
    ) {
        val height: Int get() = bottom - top
    }

    fun shouldUseTiledAnalysis(
        pageWidth: Int,
        pageHeight: Int,
        maxAnalysisPixels: Long = DEFAULT_MAX_ANALYSIS_PIXELS,
    ): Boolean {
        if (pageWidth <= 0 || pageHeight <= 0) return false
        return pageWidth.toLong() * pageHeight.toLong() > maxAnalysisPixels && pageHeight > pageWidth
    }

    fun planSlices(
        pageWidth: Int,
        pageHeight: Int,
        maxSlicePixels: Long = DEFAULT_MAX_ANALYSIS_PIXELS,
        overlapPx: Int = DEFAULT_SLICE_OVERLAP_PX,
    ): List<SliceWindow> {
        if (pageWidth <= 0 || pageHeight <= 0) return emptyList()
        val maxSliceHeight = (maxSlicePixels / pageWidth.coerceAtLeast(1))
            .toInt()
            .coerceAtLeast(MIN_SLICE_HEIGHT_PX)
            .coerceAtMost(pageHeight)

        if (pageHeight <= maxSliceHeight) {
            return listOf(SliceWindow(0, pageHeight))
        }

        val clampedOverlap = overlapPx
            .coerceAtLeast(0)
            .coerceAtMost(maxSliceHeight / 2)
        val step = (maxSliceHeight - clampedOverlap).coerceAtLeast(1)

        val slices = ArrayList<SliceWindow>()
        var top = 0
        while (top < pageHeight) {
            val remaining = pageHeight - top
            if (remaining <= maxSliceHeight) {
                val adjustedTop = if (slices.isNotEmpty() && remaining < clampedOverlap + MIN_TAIL_HEIGHT_PX) {
                    (pageHeight - maxSliceHeight).coerceAtLeast(0)
                } else {
                    top
                }
                val candidate = SliceWindow(adjustedTop, pageHeight)
                if (slices.lastOrNull() != candidate) {
                    slices += candidate
                }
                break
            }
            slices += SliceWindow(top, top + maxSliceHeight)
            top += step
        }
        return slices
    }

    fun bestSliceIndexForRegion(
        slices: List<SliceWindow>,
        region: TranslationRegion,
    ): Int {
        if (slices.isEmpty()) return -1
        val regionTop = region.y
        val regionBottom = region.y + region.height
        val regionCenterY = region.y + region.height / 2f

        var bestIndex = 0
        var bestScore = Float.NEGATIVE_INFINITY
        for (i in slices.indices) {
            val slice = slices[i]
            val overlap = (min(regionBottom, slice.bottom.toFloat()) - max(regionTop, slice.top.toFloat()))
                .coerceAtLeast(0f)
            val coverageRatio = overlap / region.height.coerceAtLeast(1f)
            val edgeClearance = min(regionTop - slice.top, slice.bottom - regionBottom)
            val centerBonus = if (regionCenterY in slice.top.toFloat()..slice.bottom.toFloat()) 10_000f else 0f
            val score = coverageRatio * 100_000f + centerBonus + edgeClearance
            if (score > bestScore) {
                bestScore = score
                bestIndex = i
            }
        }
        return bestIndex
    }

    fun assignIndicesToSlices(
        slices: List<SliceWindow>,
        regions: List<TranslationRegion>,
    ): Map<Int, List<Int>> {
        if (slices.isEmpty() || regions.isEmpty()) return emptyMap()
        val grouped = LinkedHashMap<Int, MutableList<Int>>()
        for (index in regions.indices) {
            val sliceIndex = bestSliceIndexForRegion(slices, regions[index])
            if (sliceIndex >= 0) {
                grouped.getOrPut(sliceIndex) { ArrayList() }.add(index)
            }
        }
        return grouped
    }

    fun toSliceLocalBlock(
        block: TranslationBlock,
        slice: SliceWindow,
    ): TranslationBlock {
        val localSourceRegions = block.sourceRegions.map { shiftRegionY(it, -slice.top.toFloat(), slice.height.toFloat()) }
        return block.copy(
            y = (block.y - slice.top).coerceIn(0f, (slice.height - 1).coerceAtLeast(0).toFloat()),
            height = block.height.coerceAtMost(slice.height.toFloat()),
            sourceRegions = localSourceRegions,
        )
    }

    fun toSliceLocalRegions(
        regions: List<TranslationRegion>,
        slice: SliceWindow,
    ): List<TranslationRegion> {
        val sliceTop = slice.top.toFloat()
        val sliceBottom = slice.bottom.toFloat()
        return regions.mapNotNull { region ->
            val top = region.y
            val bottom = region.y + region.height
            if (bottom <= sliceTop || top >= sliceBottom) {
                null
            } else {
                val clampedTop = max(top, sliceTop) - sliceTop
                val clampedBottom = min(bottom, sliceBottom) - sliceTop
                region.copy(
                    y = clampedTop,
                    height = (clampedBottom - clampedTop).coerceAtLeast(1f),
                )
            }
        }
    }

    fun shiftAnalysisToPage(
        analysis: SpeechBubbleAnalysis,
        slice: SliceWindow,
        pageWidth: Int,
        pageHeight: Int,
    ): SpeechBubbleAnalysis {
        val dy = slice.top.toFloat()
        val maxW = pageWidth.toFloat()
        val maxH = pageHeight.toFloat()
        return analysis.copy(
            cleanupRegion = analysis.cleanupRegion.copy(y = analysis.cleanupRegion.y + dy).clamped(maxW, maxH),
            layoutRegion = analysis.layoutRegion.copy(y = analysis.layoutRegion.y + dy).clamped(maxW, maxH),
        )
    }

    private fun shiftRegionY(region: TranslationRegion, dy: Float, maxHeight: Float): TranslationRegion {
        val shiftedY = (region.y + dy).coerceIn(0f, (maxHeight - 1f).coerceAtLeast(0f))
        val availableHeight = (maxHeight - shiftedY).coerceAtLeast(1f)
        return region.copy(
            y = shiftedY,
            height = region.height.coerceAtMost(availableHeight),
        )
    }

    const val DEFAULT_MAX_ANALYSIS_PIXELS = 6_000_000L
    const val DEFAULT_SLICE_OVERLAP_PX = 960
    private const val MIN_SLICE_HEIGHT_PX = 1024
    private const val MIN_TAIL_HEIGHT_PX = 128
}
