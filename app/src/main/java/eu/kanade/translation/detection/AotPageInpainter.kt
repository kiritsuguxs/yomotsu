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
        if (!inpainter.isModelLoaded || page.blocks.none(::needsAotInpaint)) return 0
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

        // Untranslated neighbor blocks must never be erased or smudged by an adjacent AOT patch.
        val untranslatedBoxes = page.blocks
            .filter { it.translation.isBlank() }
            .flatMap { block ->
                block.sourceRegions.ifEmpty { listOf(block.sourceRegion()) }.map { r ->
                    toRawRect(r, pad = 2, scaleX = scaleX, scaleY = scaleY, rawW = rawW, rawH = rawH)
                }
            }

        val candidateSpecs = ArrayList<BlockMaskSpec>()
        for ((index, block) in page.blocks.withIndex()) {
            if (!needsAotInpaint(block)) continue
            val patchRect = computePatchRect(block, pageW, pageH, scaleX, scaleY, rawW, rawH)
            candidateSpecs += BlockMaskSpec(
                index = index,
                block = block,
                patchRect = patchRect,
                isTarget = true,
            )
        }

        if (candidateSpecs.isEmpty()) return 0

        val aotTargets = ArrayList<PreparedBlock>()
        for (spec in candidateSpecs) {
            val patchPixels = reader.readPixels(spec.patchRect) ?: continue
            val mask = buildBlockPatchMask(
                block = spec.block,
                patchRect = spec.patchRect,
                untranslatedBoxes = untranslatedBoxes,
                scaleX = scaleX,
                scaleY = scaleY,
                rawW = rawW,
                rawH = rawH,
            )
            if (!mask.any { it }) continue
            if (isUniformFlatSurface(spec.block, patchPixels, mask)) {
                // Text floating on a pure uniform margin/gutter (#FFFFFF / #000000) needs no neural pass.
                continue
            }
            aotTargets += PreparedBlock(spec, mask)
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

            // Stamp all AOT target masks intersecting this context window so neighboring target text isn't copied.
            for (target in aotTargets) {
                if (!target.spec.patchRect.intersects(winRect)) continue
                stampMaskIntoWindow(
                    patchRect = target.spec.patchRect,
                    patchMask = target.mask,
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
                block.balloonDetected = false
                block.foregroundColor = if (stats.meanLum < DARK_BG_LUMINANCE_THRESHOLD) {
                    Color.WHITE
                } else {
                    Color.BLACK
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
        val pad = (symHRaw * 0.38f).roundToInt().coerceIn(8, 22)

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
        untranslatedBoxes: List<IntRect>,
        scaleX: Float,
        scaleY: Float,
        rawW: Int,
        rawH: Int,
    ): BooleanArray {
        val pw = patchRect.width
        val ph = patchRect.height
        val symHRaw = (block.symHeight * scaleY).coerceAtLeast(10f)
        val dilateRadius = (symHRaw * 0.28f).roundToInt().coerceIn(6, 12)

        // Reserve 2px outer ring on interior edges so patch boundaries match surrounding page pixels 100%.
        val minLocalX = if (patchRect.left > 0 && pw > 8) 2 else 0
        val maxLocalX = if (patchRect.right < rawW && pw > 8) pw - 2 else pw
        val minLocalY = if (patchRect.top > 0 && ph > 8) 2 else 0
        val maxLocalY = if (patchRect.bottom < rawH && ph > 8) ph - 2 else ph

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
                val dilated = dilateBooleanMask(
                    seed = seed,
                    w = pw,
                    h = ph,
                    radius = dilateRadius,
                    clipLeft = minLocalX,
                    clipTop = minLocalY,
                    clipRight = maxLocalX,
                    clipBottom = maxLocalY,
                )
                carveOutUntranslated(dilated, patchRect, untranslatedBoxes)
                return dilated
            }
        }

        // Complete rounded tight line-footprint masking for ML Kit / PaddleOCR over artwork:
        // guarantees 100% coverage of glyphs, outlines, and drop shadows even next to dark artwork lines.
        val combinedMask = BooleanArray(pw * ph)
        val boxPad = (symHRaw * 0.18f).roundToInt().coerceIn(4, 8)
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

            val cornerR = (minOf(fw, fh) / 5).coerceIn(0, 6)
            val rSq = cornerR * cornerR
            for (ly in fy0 until fy1) {
                val row = ly * pw
                val cy = when {
                    ly < fy0 + cornerR -> (fy0 + cornerR) - ly
                    ly >= fy1 - cornerR -> ly - (fy1 - cornerR - 1)
                    else -> 0
                }
                for (lx in fx0 until fx1) {
                    val cx = when {
                        lx < fx0 + cornerR -> (fx0 + cornerR) - lx
                        lx >= fx1 - cornerR -> lx - (fx1 - cornerR - 1)
                        else -> 0
                    }
                    if (cx == 0 || cy == 0 || cx * cx + cy * cy <= rSq) {
                        combinedMask[row + lx] = true
                    }
                }
            }
        }

        carveOutUntranslated(combinedMask, patchRect, untranslatedBoxes)
        return combinedMask
    }

    private fun carveOutUntranslated(
        mask: BooleanArray,
        patchRect: IntRect,
        untranslatedBoxes: List<IntRect>,
    ) {
        if (untranslatedBoxes.isEmpty()) return
        val pw = patchRect.width
        for (box in untranslatedBoxes) {
            if (!box.intersects(patchRect)) continue
            val x0 = (box.left - patchRect.left).coerceIn(0, pw)
            val x1 = (box.right - patchRect.left).coerceIn(x0, pw)
            val y0 = (box.top - patchRect.top).coerceIn(0, patchRect.height)
            val y1 = (box.bottom - patchRect.top).coerceIn(y0, patchRect.height)
            for (ly in y0 until y1) {
                val row = ly * pw
                for (lx in x0 until x1) {
                    mask[row + lx] = false
                }
            }
        }
    }

    internal fun isUniformFlatSurface(
        block: TranslationBlock,
        patchPixels: IntArray,
        mask: BooleanArray,
    ): Boolean {
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
            if (colorDistSq(px, bgColor) <= FLAT_SURFACE_DIST_SQ) {
                matching++
            }
            count++
        }
        if (count < 24) return false
        val mean = sumLum / count
        val std = sqrt((sumLum2 / count - mean * mean).coerceAtLeast(0.0))
        val matchRatio = matching.toFloat() / count.toFloat()
        return std < FLAT_SURFACE_MAX_STD && matchRatio >= FLAT_SURFACE_MIN_MATCH_RATIO
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
        private const val FLAT_SURFACE_DIST_SQ = 324
        private const val FLAT_SURFACE_MAX_STD = 5.5
        private const val FLAT_SURFACE_MIN_MATCH_RATIO = 0.90f
        private const val DARK_BG_LUMINANCE_THRESHOLD = 118f
        private const val MAX_FALLBACK_FULL_BITMAP_PIXELS = 25_000_000L

        /**
         * Returns true only when [block] actually needs neural background reconstruction
         * (e.g. text over artwork, gradients, system windows, SFX).
         * Closed solid speech bubbles ([TranslationBlock.balloonDetected]) already have
         * boundary-constrained uniform fills and skip AOT-GAN completely.
         */
        fun needsAotInpaint(block: TranslationBlock): Boolean {
            if (block.translation.isBlank()) return false
            if (block.layoutRegion != null && block.cleanupRegion == block.layoutRegion) return false
            val bgColor = block.backgroundColor
            if (block.balloonDetected && bgColor != null && ((bgColor ushr 24) and 0xFF) >= 240) {
                return false
            }
            return true
        }

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
