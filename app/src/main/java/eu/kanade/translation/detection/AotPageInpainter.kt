package eu.kanade.translation.detection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.TranslationRegion
import eu.kanade.translation.model.resolvedCleanupPatches
import eu.kanade.translation.model.sourceRegion
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.FileInputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal class AotPageInpainter(
    private val inpainter: Inpainter,
    private val tileSize: Int = AOT_MODEL_TILE_SIZE,
) {
    internal data class IntRect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
        val centerX: Int get() = (left + right) ushr 1
        val centerY: Int get() = (top + bottom) ushr 1

        fun intersects(other: IntRect): Boolean =
            left < other.right && right > other.left && top < other.bottom && bottom > other.top

        fun union(other: IntRect): IntRect = IntRect(
            left = minOf(left, other.left),
            top = minOf(top, other.top),
            right = maxOf(right, other.right),
            bottom = maxOf(bottom, other.bottom),
        )
    }

    internal interface PixelWindowReader : Closeable {
        val width: Int
        val height: Int
        fun readPixels(rect: IntRect): IntArray?
    }

    fun inpaintPage(fd: ParcelFileDescriptor, page: PageTranslation): Int {
        if (!inpainter.isModelLoaded || page.blocks.isEmpty()) return 0
        if (!isNormalExifOrientation(fd)) return 0

        openPixelWindowReader(fd)?.use { reader ->
            return inpaintPageWithReader(reader, page)
        }
        return 0
    }

    internal fun inpaintPageWithReader(
        reader: PixelWindowReader,
        page: PageTranslation,
        encodePatch: (IntArray, Int, Int) -> String? = ::encodeWebpBase64,
    ): Int {
        val rawW = reader.width
        val rawH = reader.height
        if (rawW < 8 || rawH < 8) return 0

        val pageW = page.imgWidth.takeIf { it > 0f } ?: rawW.toFloat()
        val pageH = page.imgHeight.takeIf { it > 0f } ?: rawH.toFloat()
        val scaleX = rawW / pageW
        val scaleY = rawH / pageH

        val allSourceBoxes = page.blocks.flatMap { block ->
            block.sourceRegions.ifEmpty { listOf(block.sourceRegion()) }.map { r ->
                toRawRect(r, pad = 1, scaleX = scaleX, scaleY = scaleY, rawW = rawW, rawH = rawH)
            }
        }

        val blockSpecs = ArrayList<BlockMaskSpec>(page.blocks.size)
        for ((index, block) in page.blocks.withIndex()) {
            val isManualSized = block.layoutRegion != null && block.cleanupRegion == block.layoutRegion
            val isTarget = block.translation.isNotBlank() && !isManualSized
            val patchRect = computePatchRect(block, pageW, pageH, scaleX, scaleY, rawW, rawH)
            blockSpecs += BlockMaskSpec(
                index = index,
                block = block,
                patchRect = patchRect,
                isTarget = isTarget,
            )
        }

        if (blockSpecs.none { it.isTarget }) return 0

        // Prepare local glyph+stroke masks and filter out provably 100%-uniform digital solid speech bubbles.
        val aotTargets = ArrayList<PreparedBlock>()
        val neighborMasks = ArrayList<PreparedBlock>()

        for (spec in blockSpecs) {
            val patchPixels = reader.readPixels(spec.patchRect) ?: continue
            val mask = buildBlockPatchMask(
                block = spec.block,
                patchRect = spec.patchRect,
                patchPixels = patchPixels,
                allSourceBoxes = allSourceBoxes,
                scaleX = scaleX,
                scaleY = scaleY,
                rawW = rawW,
                rawH = rawH,
            )
            val prepared = PreparedBlock(spec, mask)
            neighborMasks += prepared

            if (!spec.isTarget) continue
            if (isUniformFlatBalloon(spec.block, patchPixels, mask)) {
                // Keep crisp zero-cost flat fill for pure solid digital bubbles (#FFFFFF / #000000).
                continue
            }
            aotTargets += prepared
        }

        if (aotTargets.isEmpty()) return 0

        val clusters = clusterTargets(aotTargets, rawW, rawH)
        var inpaintedCount = 0

        for (cluster in clusters) {
            val winRect = cluster.windowRect
            val winW = winRect.width
            val winH = winRect.height
            val origWin = reader.readPixels(winRect) ?: continue
            val winMask = BooleanArray(winW * winH)

            // Mask ALL text blocks intersecting this context window so neighboring text is never copied as texture.
            for (neighbor in neighborMasks) {
                if (!neighbor.spec.patchRect.intersects(winRect)) continue
                stampMaskIntoWindow(
                    patchRect = neighbor.spec.patchRect,
                    patchMask = neighbor.mask,
                    winRect = winRect,
                    winMask = winMask,
                )
            }

            if (!winMask.any { it }) continue

            val aotWin = inpainter.inpaintWindow(
                windowPixels = origWin,
                aotMask = winMask,
                winW = winW,
                winH = winH,
                tileSize = tileSize,
            ) ?: continue

            val compositedWin = compositeFeathered(origWin, aotWin, winMask, winW, winH)

            for (target in cluster.targets) {
                val rect = target.spec.patchRect
                val pw = rect.width
                val ph = rect.height
                val patchOut = extractSubRect(compositedWin, winRect, rect)
                val base64 = encodePatch(patchOut, pw, ph) ?: continue

                val block = target.spec.block
                block.aotPatchBase64 = base64
                block.aotPatchRegion = TranslationRegion(
                    x = rect.left / scaleX,
                    y = rect.top / scaleY,
                    width = pw / scaleX,
                    height = ph / scaleY,
                )

                val stats = computePatchLuminanceStats(patchOut)
                block.backgroundColor = null
                if (!block.balloonDetected || block.foregroundColor == null) {
                    block.foregroundColor = if (stats.meanLum < DARK_BG_LUMINANCE_THRESHOLD) {
                        Color.WHITE
                    } else {
                        Color.BLACK
                    }
                }
                if (block.balloonDetected && stats.stdLum > BUSY_ART_STD_THRESHOLD) {
                    // Busy artwork or strong texture behind text: enable outlined comic text for legibility.
                    block.balloonDetected = false
                    block.foregroundColor = if (stats.meanLum < DARK_BG_LUMINANCE_THRESHOLD) {
                        Color.WHITE
                    } else {
                        Color.BLACK
                    }
                }
                inpaintedCount++
            }
        }

        return inpaintedCount
    }

    private fun computePatchRect(
        block: TranslationBlock,
        pageW: Float,
        pageH: Float,
        scaleX: Float,
        scaleY: Float,
        rawW: Int,
        rawH: Int,
    ): IntRect {
        val symHRaw = (block.symHeight * scaleY).coerceAtLeast(10f)
        val pad = (symHRaw * 0.42f).roundToInt().coerceIn(8, 26)

        val sources = block.sourceRegions.ifEmpty { listOf(block.sourceRegion()) }
        val cleanupPatches = block.resolvedCleanupPatches(pageW, pageH).map { it.region }
        var unionRect = toRawRect(sources.first(), pad, scaleX, scaleY, rawW, rawH)
        for (region in sources.drop(1) + cleanupPatches) {
            unionRect = unionRect.union(toRawRect(region, pad, scaleX, scaleY, rawW, rawH))
        }

        val dbnetMask = block.dbnetCleanupMask
        if (dbnetMask != null && !dbnetMask.isEmpty &&
            rawW == pageW.roundToInt() && rawH == pageH.roundToInt()
        ) {
            var minX = rawW
            var minY = rawH
            var maxX = 0
            var maxY = 0
            val valid = dbnetMask.forEachRun(rawW, rawH) { y, x, length ->
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x + length > maxX) maxX = x + length
                if (y + 1 > maxY) maxY = y + 1
            }
            if (valid && maxX > minX && maxY > minY) {
                val maskRect = IntRect(
                    left = (minX - pad).coerceIn(0, rawW - 1),
                    top = (minY - pad).coerceIn(0, rawH - 1),
                    right = (maxX + pad).coerceIn(1, rawW),
                    bottom = (maxY + pad).coerceIn(1, rawH),
                )
                unionRect = unionRect.union(maskRect)
            }
        }

        return unionRect
    }

    internal fun buildBlockPatchMask(
        block: TranslationBlock,
        patchRect: IntRect,
        patchPixels: IntArray,
        allSourceBoxes: List<IntRect>,
        scaleX: Float,
        scaleY: Float,
        rawW: Int,
        rawH: Int,
    ): BooleanArray {
        val pw = patchRect.width
        val ph = patchRect.height
        val symHRaw = (block.symHeight * scaleY).coerceAtLeast(10f)
        val dilateRadius = (symHRaw * 0.24f).roundToInt().coerceIn(5, 11)

        // Reserve 1px outer ring on interior edges so patch boundaries match surrounding page pixels 100%.
        val minLocalX = if (patchRect.left > 0 && pw > 6) 1 else 0
        val maxLocalX = if (patchRect.right < rawW && pw > 6) pw - 1 else pw
        val minLocalY = if (patchRect.top > 0 && ph > 6) 1 else 0
        val maxLocalY = if (patchRect.bottom < rawH && ph > 6) ph - 1 else ph

        val dbnetMask = block.dbnetCleanupMask
        if (dbnetMask != null && !dbnetMask.isEmpty &&
            rawW == (rawW / scaleX).roundToInt() && rawH == (rawH / scaleY).roundToInt()
        ) {
            val seed = BooleanArray(pw * ph)
            var count = 0
            val valid = dbnetMask.forEachRun(rawW, rawH) { y, x, length ->
                val ly = y - patchRect.top
                if (ly in 0 until ph) {
                    val startX = (x - patchRect.left).coerceAtLeast(0)
                    val endX = (x + length - patchRect.left).coerceAtMost(pw)
                    val row = ly * pw
                    for (lx in startX until endX) {
                        seed[row + lx] = true
                        count++
                    }
                }
            }
            if (valid && count > 0) {
                return dilateBooleanMask(
                    seed = seed,
                    w = pw,
                    h = ph,
                    radius = dilateRadius,
                    clipLeft = minLocalX,
                    clipTop = minLocalY,
                    clipRight = maxLocalX,
                    clipBottom = maxLocalY,
                )
            }
        }

        // Adaptive glyph + outline detection for ML Kit / PaddleOCR footprints.
        val combinedMask = BooleanArray(pw * ph)
        val boxPad = (symHRaw * 0.16f).roundToInt().coerceIn(3, 8)
        val footprints = block.sourceRegions.ifEmpty { listOf(block.sourceRegion()) }

        for (src in footprints) {
            val rawSrc = toRawRect(src, pad = boxPad, scaleX = scaleX, scaleY = scaleY, rawW = rawW, rawH = rawH)
            val fx0 = (rawSrc.left - patchRect.left).coerceIn(minLocalX, maxLocalX)
            val fy0 = (rawSrc.top - patchRect.top).coerceIn(minLocalY, maxLocalY)
            val fx1 = (rawSrc.right - patchRect.left).coerceIn(fx0, maxLocalX)
            val fy1 = (rawSrc.bottom - patchRect.top).coerceIn(fy0, maxLocalY)
            val fw = fx1 - fx0
            val fh = fy1 - fy0
            if (fw <= 0 || fh <= 0) continue

            val seed = BooleanArray(pw * ph)
            val topColors = IntArray(fw) { Int.MIN_VALUE }
            val botColors = IntArray(fw) { Int.MIN_VALUE }
            val leftColors = IntArray(fh) { Int.MIN_VALUE }
            val rightColors = IntArray(fh) { Int.MIN_VALUE }

            for (ix in 0 until fw) {
                val lx = fx0 + ix
                topColors[ix] = sampleCleanBorderPixel(patchPixels, pw, ph, patchRect, lx, fy0 - 1, 0, -1, allSourceBoxes)
                botColors[ix] = sampleCleanBorderPixel(patchPixels, pw, ph, patchRect, lx, fy1, 0, 1, allSourceBoxes)
            }
            for (iy in 0 until fh) {
                val ly = fy0 + iy
                leftColors[iy] = sampleCleanBorderPixel(patchPixels, pw, ph, patchRect, fx0 - 1, ly, -1, 0, allSourceBoxes)
                rightColors[iy] = sampleCleanBorderPixel(patchPixels, pw, ph, patchRect, fx1, ly, 1, 0, allSourceBoxes)
            }

            for (iy in 0 until fh) {
                val ly = fy0 + iy
                val row = ly * pw
                val ty = if (fh > 1) iy.toFloat() / (fh - 1).toFloat() else 0.5f
                val hPred = interpolateColors(leftColors[iy], rightColors[iy], 0.5f)
                for (ix in 0 until fw) {
                    val lx = fx0 + ix
                    val tx = if (fw > 1) ix.toFloat() / (fw - 1).toFloat() else 0.5f
                    val vPred = interpolateColors(topColors[ix], botColors[ix], ty)
                    val hPredLocal = interpolateColors(leftColors[iy], rightColors[iy], tx) ?: hPred
                    val px = patchPixels[row + lx]

                    val matchesV = vPred != null && colorDistSq(px, vPred) <= BG_MATCH_DIST_SQ
                    val matchesH = hPredLocal != null && colorDistSq(px, hPredLocal) <= BG_MATCH_DIST_SQ
                    if (!matchesV && !matchesH) {
                        seed[row + lx] = true
                    } else if (vPred == null && hPredLocal == null) {
                        seed[row + lx] = true
                    }
                }
            }

            val dilated = dilateBooleanMask(
                seed = seed,
                w = pw,
                h = ph,
                radius = dilateRadius,
                clipLeft = (fx0 - dilateRadius).coerceAtLeast(minLocalX),
                clipTop = (fy0 - dilateRadius).coerceAtLeast(minLocalY),
                clipRight = (fx1 + dilateRadius).coerceAtMost(maxLocalX),
                clipBottom = (fy1 + dilateRadius).coerceAtMost(maxLocalY),
            )

            var coveredInCore = 0
            for (ly in fy0 until fy1) {
                val row = ly * pw
                for (lx in fx0 until fx1) {
                    if (dilated[row + lx]) coveredInCore++
                }
            }
            val coreArea = fw * fh
            if (coveredInCore * 100 < coreArea * MIN_MASK_COVERAGE_PERCENT) {
                // Low-contrast safety fallback: mask full tight line footprint so no faint glyph survives.
                for (ly in fy0 until fy1) {
                    val row = ly * pw
                    for (lx in fx0 until fx1) {
                        combinedMask[row + lx] = true
                    }
                }
            } else {
                for (i in combinedMask.indices) {
                    if (dilated[i]) combinedMask[i] = true
                }
            }
        }

        return combinedMask
    }

    internal fun isUniformFlatBalloon(
        block: TranslationBlock,
        patchPixels: IntArray,
        mask: BooleanArray,
    ): Boolean {
        if (!block.balloonDetected) return false
        val bgColor = block.backgroundColor ?: return false
        if (((bgColor ushr 24) and 0xFF) < 240) return false

        var count = 0
        var matching = 0
        var sumLum = 0.0
        var sumLum2 = 0.0
        for (i in patchPixels.indices) {
            if (mask[i]) continue
            val px = patchPixels[i]
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            val lum = 0.299 * r + 0.587 * g + 0.114 * b
            sumLum += lum
            sumLum2 += lum * lum
            if (colorDistSq(px, bgColor) <= FLAT_BUBBLE_DIST_SQ) {
                matching++
            }
            count++
        }
        if (count < 24) return false
        val mean = sumLum / count
        val std = sqrt((sumLum2 / count - mean * mean).coerceAtLeast(0.0))
        val matchRatio = matching.toFloat() / count.toFloat()
        return std < FLAT_BUBBLE_MAX_STD && matchRatio >= FLAT_BUBBLE_MIN_MATCH_RATIO
    }

    internal data class WindowCluster(
        val windowRect: IntRect,
        val targets: List<PreparedBlock>,
    )

    internal data class BlockMaskSpec(
        val index: Int,
        val block: TranslationBlock,
        val patchRect: IntRect,
        val isTarget: Boolean,
    )

    internal class PreparedBlock(
        val spec: BlockMaskSpec,
        val mask: BooleanArray,
    )

    internal fun clusterTargets(
        targets: List<PreparedBlock>,
        rawW: Int,
        rawH: Int,
    ): List<WindowCluster> {
        if (targets.isEmpty()) return emptyList()
        val remaining = targets.sortedWith(
            compareBy<PreparedBlock> { it.spec.patchRect.centerY }.thenBy { it.spec.patchRect.centerX },
        ).toMutableList()

        val clusters = ArrayList<WindowCluster>()
        while (remaining.isNotEmpty()) {
            val seed = remaining.removeAt(0)
            val group = mutableListOf(seed)
            var bounds = seed.spec.patchRect

            var changed: Boolean
            do {
                changed = false
                val iter = remaining.iterator()
                while (iter.hasNext()) {
                    val candidate = iter.next()
                    val merged = bounds.union(candidate.spec.patchRect)
                    if (merged.width <= MAX_CLUSTER_SPAN && merged.height <= MAX_CLUSTER_SPAN) {
                        bounds = merged
                        group += candidate
                        iter.remove()
                        changed = true
                    }
                }
            } while (changed)

            val contextPad = 48
            val side = maxOf(bounds.width + contextPad * 2, bounds.height + contextPad * 2, tileSize)
            val winW = minOf(side, rawW)
            val winH = minOf(side, rawH)
            val winLeft = (bounds.centerX - winW / 2).coerceIn(0, (rawW - winW).coerceAtLeast(0))
            val winTop = (bounds.centerY - winH / 2).coerceIn(0, (rawH - winH).coerceAtLeast(0))
            val windowRect = IntRect(winLeft, winTop, winLeft + winW, winTop + winH)
            clusters += WindowCluster(windowRect, group)
        }
        return clusters
    }

    private fun stampMaskIntoWindow(
        patchRect: IntRect,
        patchMask: BooleanArray,
        winRect: IntRect,
        winMask: BooleanArray,
    ) {
        val pw = patchRect.width
        val winW = winRect.width
        val xStart = maxOf(patchRect.left, winRect.left)
        val xEnd = minOf(patchRect.right, winRect.right)
        val yStart = maxOf(patchRect.top, winRect.top)
        val yEnd = minOf(patchRect.bottom, winRect.bottom)
        if (xEnd <= xStart || yEnd <= yStart) return

        for (y in yStart until yEnd) {
            val pRow = (y - patchRect.top) * pw
            val wRow = (y - winRect.top) * winW
            for (x in xStart until xEnd) {
                if (patchMask[pRow + (x - patchRect.left)]) {
                    winMask[wRow + (x - winRect.left)] = true
                }
            }
        }
    }

    internal fun compositeFeathered(
        origWin: IntArray,
        aotWin: IntArray,
        mask: BooleanArray,
        winW: Int,
        winH: Int,
    ): IntArray {
        val out = origWin.copyOf()
        for (y in 0 until winH) {
            val row = y * winW
            for (x in 0 until winW) {
                val idx = row + x
                if (!mask[idx]) continue

                val edge1 = (x > 0 && !mask[idx - 1]) ||
                    (x + 1 < winW && !mask[idx + 1]) ||
                    (y > 0 && !mask[idx - winW]) ||
                    (y + 1 < winH && !mask[idx + winW])

                val alpha = if (edge1) {
                    96
                } else {
                    val edge2 = (x > 1 && !mask[idx - 2]) ||
                        (x + 2 < winW && !mask[idx + 2]) ||
                        (y > 1 && !mask[idx - 2 * winW]) ||
                        (y + 2 < winH && !mask[idx + 2 * winW]) ||
                        (x > 0 && y > 0 && !mask[idx - winW - 1]) ||
                        (x + 1 < winW && y > 0 && !mask[idx - winW + 1]) ||
                        (x > 0 && y + 1 < winH && !mask[idx + winW - 1]) ||
                        (x + 1 < winW && y + 1 < winH && !mask[idx + winW + 1])
                    if (edge2) 184 else 256
                }

                if (alpha >= 256) {
                    out[idx] = aotWin[idx]
                } else {
                    val o = origWin[idx]
                    val a = aotWin[idx]
                    val inv = 256 - alpha
                    val r = (((a ushr 16) and 0xFF) * alpha + ((o ushr 16) and 0xFF) * inv) ushr 8
                    val g = (((a ushr 8) and 0xFF) * alpha + ((o ushr 8) and 0xFF) * inv) ushr 8
                    val b = ((a and 0xFF) * alpha + (o and 0xFF) * inv) ushr 8
                    out[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }
        return out
    }

    private fun extractSubRect(
        winPixels: IntArray,
        winRect: IntRect,
        subRect: IntRect,
    ): IntArray {
        val sw = subRect.width
        val sh = subRect.height
        val winW = winRect.width
        val out = IntArray(sw * sh)
        val dx = subRect.left - winRect.left
        val dy = subRect.top - winRect.top
        for (y in 0 until sh) {
            val srcOffset = (dy + y) * winW + dx
            winPixels.copyInto(out, destinationOffset = y * sw, startIndex = srcOffset, endIndex = srcOffset + sw)
        }
        return out
    }

    private class PatchStats(val meanLum: Float, val stdLum: Float)

    private fun computePatchLuminanceStats(pixels: IntArray): PatchStats {
        if (pixels.isEmpty()) return PatchStats(255f, 0f)
        var sum = 0.0
        var sum2 = 0.0
        for (px in pixels) {
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            val lum = 0.299 * r + 0.587 * g + 0.114 * b
            sum += lum
            sum2 += lum * lum
        }
        val mean = sum / pixels.size
        val std = sqrt((sum2 / pixels.size - mean * mean).coerceAtLeast(0.0))
        return PatchStats(mean.toFloat(), std.toFloat())
    }

    private fun sampleCleanBorderPixel(
        patchPixels: IntArray,
        pw: Int,
        ph: Int,
        patchRect: IntRect,
        startX: Int,
        startY: Int,
        stepX: Int,
        stepY: Int,
        allSourceBoxes: List<IntRect>,
    ): Int {
        var sr = 0
        var sg = 0
        var sb = 0
        var count = 0
        var lx = startX
        var ly = startY
        for (step in 0 until 6) {
            if (lx !in 0 until pw || ly !in 0 until ph) break
            val rawX = patchRect.left + lx
            val rawY = patchRect.top + ly
            val insideText = allSourceBoxes.any { box ->
                rawX in box.left until box.right && rawY in box.top until box.bottom
            }
            if (!insideText) {
                val px = patchPixels[ly * pw + lx]
                sr += (px ushr 16) and 0xFF
                sg += (px ushr 8) and 0xFF
                sb += px and 0xFF
                count++
                if (count >= 3) break
            }
            lx += stepX
            ly += stepY
        }
        return if (count == 0) Int.MIN_VALUE else packRgb(sr / count, sg / count, sb / count)
    }

    private fun interpolateColors(c0: Int, c1: Int, t: Float): Int? {
        if (c0 == Int.MIN_VALUE && c1 == Int.MIN_VALUE) return null
        if (c0 == Int.MIN_VALUE) return c1
        if (c1 == Int.MIN_VALUE) return c0
        val clamped = t.coerceIn(0f, 1f)
        val r = (((c0 ushr 16) and 0xFF) * (1f - clamped) + ((c1 ushr 16) and 0xFF) * clamped).roundToInt()
        val g = (((c0 ushr 8) and 0xFF) * (1f - clamped) + ((c1 ushr 8) and 0xFF) * clamped).roundToInt()
        val b = ((c0 and 0xFF) * (1f - clamped) + (c1 and 0xFF) * clamped).roundToInt()
        return packRgb(r, g, b)
    }

    private fun packRgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    private fun dilateBooleanMask(
        seed: BooleanArray,
        w: Int,
        h: Int,
        radius: Int,
        clipLeft: Int,
        clipTop: Int,
        clipRight: Int,
        clipBottom: Int,
    ): BooleanArray {
        if (radius <= 0) return seed.copyOf()
        val horiz = BooleanArray(w * h)
        for (y in clipTop until clipBottom) {
            val row = y * w
            var windowCount = 0
            val rStart = clipLeft
            val rInitEnd = (clipLeft + radius).coerceAtMost(clipRight - 1)
            for (xx in rStart..rInitEnd) {
                if (seed[row + xx]) windowCount++
            }
            for (x in clipLeft until clipRight) {
                if (windowCount > 0) horiz[row + x] = true
                val removeX = x - radius
                if (removeX >= clipLeft && seed[row + removeX]) windowCount--
                val addX = x + radius + 1
                if (addX < clipRight && seed[row + addX]) windowCount++
            }
        }
        val out = BooleanArray(w * h)
        for (x in clipLeft until clipRight) {
            var windowCount = 0
            val rInitEnd = (clipTop + radius).coerceAtMost(clipBottom - 1)
            for (yy in clipTop..rInitEnd) {
                if (horiz[yy * w + x]) windowCount++
            }
            for (y in clipTop until clipBottom) {
                if (windowCount > 0) out[y * w + x] = true
                val removeY = y - radius
                if (removeY >= clipTop && horiz[removeY * w + x]) windowCount--
                val addY = y + radius + 1
                if (addY < clipBottom && horiz[addY * w + x]) windowCount++
            }
        }
        return out
    }

    private fun toRawRect(
        region: TranslationRegion,
        pad: Int,
        scaleX: Float,
        scaleY: Float,
        rawW: Int,
        rawH: Int,
    ): IntRect {
        val left = (floor(region.x * scaleX).toInt() - pad).coerceIn(0, rawW - 1)
        val top = (floor(region.y * scaleY).toInt() - pad).coerceIn(0, rawH - 1)
        val right = (ceil((region.x + region.width) * scaleX).toInt() + pad).coerceIn(left + 1, rawW)
        val bottom = (ceil((region.y + region.height) * scaleY).toInt() + pad).coerceIn(top + 1, rawH)
        return IntRect(left, top, right, bottom)
    }

    private fun colorDistSq(a: Int, b: Int): Int {
        val dr = ((a ushr 16) and 0xFF) - ((b ushr 16) and 0xFF)
        val dg = ((a ushr 8) and 0xFF) - ((b ushr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr + dg * dg + db * db
    }

    private fun rewindFd(fd: ParcelFileDescriptor) {
        runCatching { Os.lseek(fd.fileDescriptor, 0L, OsConstants.SEEK_SET) }
    }

    private fun isNormalExifOrientation(fd: ParcelFileDescriptor): Boolean = runCatching {
        rewindFd(fd)
        ParcelFileDescriptor.dup(fd.fileDescriptor).use { dupFd ->
            FileInputStream(dupFd.fileDescriptor).use { input ->
                val orientation = ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED,
                )
                orientation == ExifInterface.ORIENTATION_UNDEFINED || orientation == ExifInterface.ORIENTATION_NORMAL
            }
        }
    }.getOrDefault(true)

    private fun openPixelWindowReader(fd: ParcelFileDescriptor): PixelWindowReader? {
        rewindFd(fd)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFileDescriptor(fd.fileDescriptor, null, bounds)
        val rawW = bounds.outWidth
        val rawH = bounds.outHeight
        if (rawW <= 0 || rawH <= 0 || rawW > 100_000 || rawH > 100_000) return null

        val decoder = runCatching {
            rewindFd(fd)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(fd)
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(fd.fileDescriptor, false)
            }
        }.getOrNull()

        if (decoder != null) {
            return object : PixelWindowReader {
                override val width: Int = decoder.width
                override val height: Int = decoder.height

                override fun readPixels(rect: IntRect): IntArray? = runCatching {
                    val opts = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val androidRect = Rect(rect.left, rect.top, rect.right, rect.bottom)
                    val decoded = decoder.decodeRegion(androidRect, opts) ?: return@runCatching null
                    val argb = if (decoded.config == Bitmap.Config.ARGB_8888) {
                        decoded
                    } else {
                        decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() }
                    }
                    try {
                        val w = rect.width
                        val h = rect.height
                        if (argb.width != w || argb.height != h) return@runCatching null
                        IntArray(w * h).also { argb.getPixels(it, 0, w, 0, 0, w, h) }
                    } finally {
                        argb.recycle()
                    }
                }.getOrNull()

                override fun close() {
                    decoder.recycle()
                }
            }
        }

        if (rawW.toLong() * rawH > MAX_FALLBACK_FULL_BITMAP_PIXELS) return null
        val fullBitmap = runCatching {
            rewindFd(fd)
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFileDescriptor(fd.fileDescriptor, null, opts)
        }.getOrNull() ?: return null

        return object : PixelWindowReader {
            override val width: Int = fullBitmap.width
            override val height: Int = fullBitmap.height

            override fun readPixels(rect: IntRect): IntArray? = runCatching {
                val w = rect.width
                val h = rect.height
                IntArray(w * h).also {
                    fullBitmap.getPixels(it, 0, w, rect.left, rect.top, w, h)
                }
            }.getOrNull()

            override fun close() {
                fullBitmap.recycle()
            }
        }
    }

    companion object {
        const val AOT_MODEL_TILE_SIZE = 512
        private const val MAX_CLUSTER_SPAN = 416
        private const val BG_MATCH_DIST_SQ = 720
        private const val MIN_MASK_COVERAGE_PERCENT = 18
        private const val FLAT_BUBBLE_DIST_SQ = 196
        private const val FLAT_BUBBLE_MAX_STD = 3.2
        private const val FLAT_BUBBLE_MIN_MATCH_RATIO = 0.95f
        private const val DARK_BG_LUMINANCE_THRESHOLD = 118f
        private const val BUSY_ART_STD_THRESHOLD = 26f
        private const val MAX_FALLBACK_FULL_BITMAP_PIXELS = 25_000_000L

        internal fun encodeWebpBase64(pixels: IntArray, width: Int, height: Int): String? = runCatching {
            val bmp = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
            try {
                val out = ByteArrayOutputStream(4096)
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                if (!bmp.compress(format, 92, out)) return@runCatching null
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            } finally {
                bmp.recycle()
            }
        }.getOrNull()
    }
}
