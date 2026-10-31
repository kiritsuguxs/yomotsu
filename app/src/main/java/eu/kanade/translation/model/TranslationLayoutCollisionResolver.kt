package eu.kanade.translation.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object TranslationLayoutCollisionResolver {

    fun resolveInPlace(
        blocks: List<TranslationBlock>,
        pageWidth: Float,
        pageHeight: Float,
    ) {
        if (blocks.size <= 1 || pageWidth <= 0f || pageHeight <= 0f) return
        val resolved = computeNonOverlappingLayouts(blocks, pageWidth, pageHeight)
        blocks.forEachIndexed { index, block ->
            if (block.translation.isNotBlank() && !isCustomSized(block)) {
                val target = resolved[index]
                val current = block.resolvedLayoutRegion(pageWidth, pageHeight)
                if (target != current) {
                    block.layoutRegion = target
                    block.geometryVersion = max(block.geometryVersion, CURRENT_TRANSLATION_GEOMETRY_VERSION)
                }
            }
        }
    }

    fun computeNonOverlappingLayouts(
        blocks: List<TranslationBlock>,
        pageWidth: Float,
        pageHeight: Float,
    ): List<TranslationRegion> {
        val regions = blocks.map { it.resolvedLayoutRegion(pageWidth, pageHeight) }.toMutableList()
        if (blocks.size <= 1 || pageWidth <= 0f || pageHeight <= 0f) return regions

        // 1. Prevent a translated block's expanded layout from covering an untranslated neighbor's source text.
        for (i in blocks.indices) {
            val block = blocks[i]
            if (block.translation.isBlank() || isCustomSized(block)) continue
            val src = block.sourceRegion()
            val srcCy = src.y + src.height / 2f
            for (j in blocks.indices) {
                if (i == j || blocks[j].translation.isNotBlank()) continue
                val u = blocks[j].sourceRegion()
                var r = regions[i]
                if (!intersects(r, u)) continue
                val minSrcArea = min(src.width * src.height, u.width * u.height).coerceAtLeast(1f)
                if (intersectionArea(src, u) / minSrcArea >= 0.40f) continue

                val uCy = u.y + u.height / 2f
                val minH = minReadableHeight(block, r)
                if (srcCy > uCy) {
                    val clippedTop = u.y + u.height + BLOCK_SEPARATION_GAP
                    if (clippedTop < r.y + r.height - minH) {
                        val nextTop = max(r.y, clippedTop)
                        r = TranslationRegion(r.x, nextTop, r.width, (r.y + r.height - nextTop).coerceAtLeast(1f))
                            .clamped(pageWidth, pageHeight)
                        regions[i] = r
                    }
                } else {
                    val clippedBottom = u.y - BLOCK_SEPARATION_GAP
                    if (clippedBottom > r.y + minH) {
                        val nextBottom = min(r.y + r.height, clippedBottom)
                        r = TranslationRegion(r.x, r.y, r.width, (nextBottom - r.y).coerceAtLeast(1f))
                            .clamped(pageWidth, pageHeight)
                        regions[i] = r
                    }
                }
            }
        }

        // 2. Partition overlapping layout regions between distinct translated blocks.
        repeat(2) {
            for (i in 0 until blocks.size) {
                if (blocks[i].translation.isBlank()) continue
                for (j in i + 1 until blocks.size) {
                    if (blocks[j].translation.isBlank()) continue
                    val ra = regions[i]
                    val rb = regions[j]
                    val overlapW = min(ra.x + ra.width, rb.x + rb.width) - max(ra.x, rb.x)
                    val overlapH = min(ra.y + ra.height, rb.y + rb.height) - max(ra.y, rb.y)
                    if (overlapW <= 1f || overlapH <= 1f) continue

                    val customA = isCustomSized(blocks[i])
                    val customB = isCustomSized(blocks[j])
                    if (customA && customB) continue

                    val sa = blocks[i].sourceRegion()
                    val sb = blocks[j].sourceRegion()
                    val cax = sa.x + sa.width / 2f
                    val cay = sa.y + sa.height / 2f
                    val cbx = sb.x + sb.width / 2f
                    val cby = sb.y + sb.height / 2f

                    if (abs(cby - cay) >= abs(cbx - cax) * 0.45f) {
                        val (topIdx, botIdx) = if (cay <= cby) i to j else j to i
                        val topBlock = blocks[topIdx]
                        val botBlock = blocks[botIdx]
                        val topReg = regions[topIdx]
                        val botReg = regions[botIdx]
                        val topSrc = topBlock.sourceRegion()
                        val botSrc = botBlock.sourceRegion()

                        val rawSplit = ((topSrc.y + topSrc.height) + botSrc.y) / 2f
                        val minSplit = topReg.y + minReadableHeight(topBlock, topReg)
                        val maxSplit = (botReg.y + botReg.height) - minReadableHeight(botBlock, botReg)
                        if (maxSplit >= minSplit) {
                            val splitY = when {
                                isCustomSized(topBlock) -> topReg.y + topReg.height + HALF_GAP
                                isCustomSized(botBlock) -> botReg.y - HALF_GAP
                                else -> rawSplit.coerceIn(minSplit, maxSplit)
                            }
                            if (!isCustomSized(topBlock)) {
                                val nextBottom = min(topReg.y + topReg.height, splitY - HALF_GAP)
                                val nextH = (nextBottom - topReg.y).coerceAtLeast(1f)
                                regions[topIdx] = topReg.copy(height = nextH).clamped(pageWidth, pageHeight)
                            }
                            if (!isCustomSized(botBlock)) {
                                val nextTop = max(botReg.y, splitY + HALF_GAP)
                                val nextH = ((botReg.y + botReg.height) - nextTop).coerceAtLeast(1f)
                                regions[botIdx] = TranslationRegion(botReg.x, nextTop, botReg.width, nextH)
                                    .clamped(pageWidth, pageHeight)
                            }
                        }
                    } else {
                        val (leftIdx, rightIdx) = if (cax <= cbx) i to j else j to i
                        val leftBlock = blocks[leftIdx]
                        val rightBlock = blocks[rightIdx]
                        val leftReg = regions[leftIdx]
                        val rightReg = regions[rightIdx]
                        val leftSrc = leftBlock.sourceRegion()
                        val rightSrc = rightBlock.sourceRegion()

                        val rawSplit = ((leftSrc.x + leftSrc.width) + rightSrc.x) / 2f
                        val minSplit = leftReg.x + minReadableWidth(leftBlock, leftReg)
                        val maxSplit = (rightReg.x + rightReg.width) - minReadableWidth(rightBlock, rightReg)
                        if (maxSplit >= minSplit) {
                            val splitX = when {
                                isCustomSized(leftBlock) -> leftReg.x + leftReg.width + HALF_GAP
                                isCustomSized(rightBlock) -> rightReg.x - HALF_GAP
                                else -> rawSplit.coerceIn(minSplit, maxSplit)
                            }
                            if (!isCustomSized(leftBlock)) {
                                val nextRight = min(leftReg.x + leftReg.width, splitX - HALF_GAP)
                                val nextW = (nextRight - leftReg.x).coerceAtLeast(1f)
                                regions[leftIdx] = leftReg.copy(width = nextW).clamped(pageWidth, pageHeight)
                            }
                            if (!isCustomSized(rightBlock)) {
                                val nextLeft = max(rightReg.x, splitX + HALF_GAP)
                                val nextW = ((rightReg.x + rightReg.width) - nextLeft).coerceAtLeast(1f)
                                regions[rightIdx] = TranslationRegion(nextLeft, rightReg.y, nextW, rightReg.height)
                                    .clamped(pageWidth, pageHeight)
                            }
                        }
                    }
                }
            }
        }

        return regions
    }

    private fun isCustomSized(block: TranslationBlock): Boolean =
        block.layoutRegion != null && block.cleanupRegion == block.layoutRegion

    private fun minReadableHeight(block: TranslationBlock, region: TranslationRegion): Float =
        min(region.height * 0.45f, max(block.symHeight * 1.25f, 14f)).coerceAtLeast(1f)

    private fun minReadableWidth(block: TranslationBlock, region: TranslationRegion): Float =
        min(region.width * 0.45f, max(block.symWidth * 2.5f, 24f)).coerceAtLeast(1f)

    private fun intersects(a: TranslationRegion, b: TranslationRegion): Boolean =
        a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y

    private fun intersectionArea(a: TranslationRegion, b: TranslationRegion): Float {
        val w = (min(a.x + a.width, b.x + b.width) - max(a.x, b.x)).coerceAtLeast(0f)
        val h = (min(a.y + a.height, b.y + b.height) - max(a.y, b.y)).coerceAtLeast(0f)
        return w * h
    }

    private const val BLOCK_SEPARATION_GAP = 3f
    private const val HALF_GAP = 1.5f
}
