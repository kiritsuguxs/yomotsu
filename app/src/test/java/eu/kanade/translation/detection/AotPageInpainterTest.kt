package eu.kanade.translation.detection

import eu.kanade.translation.model.AotPatchCleanup
import eu.kanade.translation.model.CURRENT_TRANSLATION_GEOMETRY_VERSION
import eu.kanade.translation.model.NoCleanup
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.TranslationRegion
import eu.kanade.translation.model.resolveCleanup
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AotPageInpainterTest {

    private class FakeAotBackend : DbnetBackend {
        var inpaintCalls = 0
        var lastTileSize = 0
        var lastMaskedCount = 0

        override fun create(param: String, bin: String): Long = 77L

        override fun infer(
            handle: Long,
            input: FloatArray,
            width: Int,
            height: Int,
            db: FloatArray,
            mask: FloatArray,
            dimensions: IntArray,
        ): Int = 0

        override fun inpaintAot(
            handle: Long,
            img: FloatArray,
            mask: FloatArray,
            s: Int,
            out: FloatArray,
        ): Int {
            inpaintCalls++
            lastTileSize = s
            val area = s * s
            var masked = 0
            for (i in 0 until area) {
                if (mask[i] > 0.5f) {
                    masked++
                    // Reconstruct dark blue artwork (-0.6, -0.5, 0.2) inside masked holes
                    out[i] = -0.6f
                    out[area + i] = -0.5f
                    out[2 * area + i] = 0.2f
                } else {
                    out[i] = img[i]
                    out[area + i] = img[area + i]
                    out[2 * area + i] = img[2 * area + i]
                }
            }
            lastMaskedCount = masked
            return 0
        }

        override fun release(handle: Long) = Unit
    }

    private class SyntheticReader(
        override val width: Int,
        override val height: Int,
        private val pixelAt: (x: Int, y: Int) -> Int,
    ) : AotPageInpainter.PixelWindowReader {
        override fun readPixels(rect: AotPageInpainter.IntRect): IntArray {
            val w = rect.width
            val h = rect.height
            val out = IntArray(w * h)
            for (ly in 0 until h) {
                val y = rect.top + ly
                val row = ly * w
                for (lx in 0 until w) {
                    out[row + lx] = pixelAt(rect.left + lx, y)
                }
            }
            return out
        }

        override fun close() = Unit
    }

    @Test
    fun `inpaints artwork block while skipping pure uniform solid speech bubble`() {
        val backend = FakeAotBackend()
        val inpainter = Inpainter(
            modelPath = "/models/mit_aot_fixed512.ncnn.param",
            cfg = InpainterConfig(method = InpainterConfig.METHOD_AOT, tileSize = 512),
            backend = backend,
        )
        val pageInpainter = AotPageInpainter(inpainter, tileSize = 512)

        // Block 0: Pure solid white speech bubble at top -> should keep zero-cost flat fill (skipped by AOT)
        val flatBubble = TranslationBlock(
            text = "HELLO",
            translation = "OLÁ",
            width = 80f,
            height = 24f,
            symWidth = 10f,
            symHeight = 16f,
            angle = 0f,
            x = 100f,
            y = 100f,
            backgroundColor = -0x1, // Color.WHITE
            foregroundColor = -0x1000000, // Color.BLACK
            balloonDetected = true,
            geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION,
        )

        // Block 1: SFX / system box over dark textured gradient artwork -> must be reconstructed by AOT-GAN
        val artworkSfx = TranslationBlock(
            text = "BOOM",
            translation = "KABUM",
            width = 90f,
            height = 28f,
            symWidth = 12f,
            symHeight = 20f,
            angle = 0f,
            x = 240f,
            y = 320f,
            backgroundColor = null,
            foregroundColor = -0x1,
            balloonDetected = false,
            geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION,
        )

        val page = PageTranslation(
            blocks = mutableListOf(flatBubble, artworkSfx),
            imgWidth = 600f,
            imgHeight = 800f,
        )

        val reader = SyntheticReader(600, 800) { x, y ->
            when {
                // Inside flat white speech bubble (with black glyph bars inside [110..170, 105..119])
                x in 70..210 && y in 75..150 -> {
                    if (x in 110..170 && y in 106..118 && x % 4 < 2) {
                        -0x1000000
                    } else {
                        -0x1
                    }
                }
                // Bright yellow SFX glyphs inside [250..320, 326..342] over dark gradient art
                x in 250..320 && y in 326..342 -> (0xFF shl 24) or (250 shl 16) or (240 shl 8) or 30
                // Dark gradient artwork elsewhere
                else -> {
                    val r = (20 + (x % 45)).coerceIn(0, 255)
                    val g = (25 + (y % 50)).coerceIn(0, 255)
                    val b = (60 + ((x + y) % 55)).coerceIn(0, 255)
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }

        val count = pageInpainter.inpaintPageWithReader(
            reader = reader,
            page = page,
            encodePatch = { px, w, h -> "patch:${w}x${h}:${px.size}" },
        )

        assertEquals(1, count, "Only the artwork block needs neural reconstruction")
        assertEquals(1, backend.inpaintCalls)
        assertEquals(512, backend.lastTileSize)
        assertTrue(backend.lastMaskedCount > 0)

        assertNull(flatBubble.aotPatchBase64, "Pure white uniform bubble keeps crisp flat fill")
        assertNotNull(artworkSfx.aotPatchBase64, "Artwork SFX receives reconstructed AOT patch")
        assertNotNull(artworkSfx.aotPatchRegion)
        assertNull(artworkSfx.backgroundColor)
        assertEquals(-0x1, artworkSfx.foregroundColor, "Dark reconstructed art selects white text")
    }

    @Test
    fun `webtoon tall strip clusters nearby blocks and separates distant ones at 512px scale`() {
        val backend = FakeAotBackend()
        val inpainter = Inpainter(
            modelPath = "/models/mit_aot_fixed512.ncnn.param",
            cfg = InpainterConfig(method = InpainterConfig.METHOD_AOT, tileSize = 512),
            backend = backend,
        )
        val pageInpainter = AotPageInpainter(inpainter, tileSize = 512)

        fun artBlock(x: Float, y: Float) = TranslationBlock(
            text = "SFX",
            translation = "ESTRondo",
            width = 70f,
            height = 24f,
            symWidth = 12f,
            symHeight = 18f,
            angle = 0f,
            x = x,
            y = y,
            balloonDetected = false,
            geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION,
        )

        val topA = artBlock(180f, 400f)
        val topB = artBlock(220f, 510f)
        val farBottom = artBlock(200f, 11_200f)

        val page = PageTranslation(
            blocks = mutableListOf(topA, topB, farBottom),
            imgWidth = 800f,
            imgHeight = 12_000f,
        )

        val reader = SyntheticReader(800, 12_000) { x, y ->
            val inGlyph = (x in 185..245 && y in 404..420) ||
                (x in 225..285 && y in 514..530) ||
                (x in 205..265 && y in 11_204..11_220)
            if (inGlyph) {
                -0x1
            } else {
                val c = 35 + ((x * 3 + y * 5) % 60)
                (0xFF shl 24) or (c shl 16) or (c shl 8) or (c + 20)
            }
        }

        val count = pageInpainter.inpaintPageWithReader(
            reader = reader,
            page = page,
            encodePatch = { _, w, h -> "webp:${w}x${h}" },
        )

        assertEquals(3, count)
        assertEquals(2, backend.inpaintCalls, "Two nearby top blocks share one 512x512 pass; bottom block uses a second")
    }

    @Test
    fun `compositeFeathered preserves outer unmasked pixels and blends mask border smoothly`() {
        val inpainter = Inpainter(modelPath = null, cfg = InpainterConfig(method = InpainterConfig.METHOD_BOXFILL))
        val pageInpainter = AotPageInpainter(inpainter, tileSize = 512)

        val w = 9
        val h = 9
        val orig = IntArray(w * h) { (0xFF shl 24) or 0 } // Pure black (0)
        val aot = IntArray(w * h) { (0xFF shl 24) or (200 shl 16) or (200 shl 8) or 200 } // Gray 200
        val mask = BooleanArray(w * h)
        for (y in 2..6) {
            for (x in 2..6) {
                mask[y * w + x] = true
            }
        }

        val out = pageInpainter.compositeFeathered(orig, aot, mask, w, h)

        // Outside mask (0, 0) -> untouched 0
        assertEquals(0, out[0] and 0xFF)
        // Outer 1px border of mask (2, 4) -> feathered (200 * 96 / 256 = 75)
        assertEquals(75, out[4 * w + 2] and 0xFF)
        // Second 2px ring of mask (3, 4) -> feathered (200 * 184 / 256 = 143)
        assertEquals(143, out[4 * w + 3] and 0xFF)
        // Core center of mask (4, 4) -> 100% AOT output (200)
        assertEquals(200, out[4 * w + 4] and 0xFF)
    }

    @Test
    fun `resolveCleanup prioritizes serialized AOT patch and survives layout resize`() {
        val block = TranslationBlock(
            text = "RAW",
            translation = "TRADUZIDO",
            width = 100f,
            height = 30f,
            symWidth = 10f,
            symHeight = 16f,
            angle = 0f,
            x = 50f,
            y = 60f,
            layoutRegion = TranslationRegion(30f, 40f, 160f, 80f),
            cleanupRegion = TranslationRegion(45f, 55f, 110f, 40f),
            aotPatchBase64 = "UklGRg==",
            aotPatchRegion = TranslationRegion(40f, 50f, 120f, 50f),
            geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION,
        )

        val json = Json { ignoreUnknownKeys = true }
        val restored = json.decodeFromString<TranslationBlock>(json.encodeToString(block))
        val resolved = restored.resolveCleanup(500f, 800f)

        assertTrue(resolved is AotPatchCleanup)
        val aotCleanup = resolved as AotPatchCleanup
        assertEquals("UklGRg==", aotCleanup.patchBase64)
        assertEquals(TranslationRegion(40f, 50f, 120f, 50f), aotCleanup.region)

        // Blank translation must never erase original page artwork
        val blank = restored.copy(translation = "   ")
        assertFalse(blank.resolveCleanup(500f, 800f) is AotPatchCleanup)
        assertEquals(NoCleanup, blank.resolveCleanup(500f, 800f))
    }

    @Test
    fun `shadowed closed speech bubble is skipped completely while untranslated neighbor stays intact`() {
        val backend = FakeAotBackend()
        val inpainter = Inpainter(
            modelPath = "/models/mit_aot_fixed512.ncnn.param",
            cfg = InpainterConfig(method = InpainterConfig.METHOD_AOT, tileSize = 512),
            backend = backend,
        )
        val pageInpainter = AotPageInpainter(inpainter, tileSize = 512)

        // Closed speech bubble with dark outline/drop-shadow right next to text -> must skip AOT
        val shadowedBubble = TranslationBlock(
            text = "NORMALLY ONE OF YOUR PARENTS WOULD HAVE GUIDED YOU",
            translation = "NORMALMENTE, UM DOS SEUS PAIS GUIARIA VOCÊ.",
            width = 180f,
            height = 60f,
            symWidth = 10f,
            symHeight = 16f,
            angle = 0f,
            x = 60f,
            y = 80f,
            backgroundColor = -0x1,
            foregroundColor = -0x1000000,
            balloonDetected = true,
            geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION,
        )
        assertFalse(AotPageInpainter.needsAotInpaint(shadowedBubble))

        // Untranslated neighbor line just above an artwork block -> must never be smudged
        val untranslatedNeighbor = TranslationBlock(
            text = "RHYTHM FOR EVERY",
            translation = "",
            width = 120f,
            height = 18f,
            symWidth = 10f,
            symHeight = 16f,
            angle = 0f,
            x = 200f,
            y = 300f,
            balloonDetected = false,
            geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION,
        )
        val translatedArt = TranslationBlock(
            text = "INDIVIDUAL.",
            translation = "É INDIVIDUAL.",
            width = 100f,
            height = 18f,
            symWidth = 10f,
            symHeight = 16f,
            angle = 0f,
            x = 210f,
            y = 324f,
            balloonDetected = false,
            geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION,
        )

        val sentinelColor = (0xFF shl 24) or (250 shl 16) or (10 shl 8) or 10
        val reader = SyntheticReader(600, 600) { x, y ->
            if (x in 200..319 && y in 300..317) {
                sentinelColor
            } else {
                val c = 40 + ((x + y) % 50)
                (0xFF shl 24) or (c shl 16) or (c shl 8) or (c + 15)
            }
        }

        var capturedPatch: IntArray? = null
        var capturedW = 0
        val page = PageTranslation(
            blocks = mutableListOf(shadowedBubble, untranslatedNeighbor, translatedArt),
            imgWidth = 600f,
            imgHeight = 600f,
        )

        val count = pageInpainter.inpaintPageWithReader(
            reader = reader,
            page = page,
            encodePatch = { px, w, _ ->
                capturedPatch = px
                capturedW = w
                "patch"
            },
        )

        assertEquals(1, count)
        assertNull(shadowedBubble.aotPatchBase64)
        assertNotNull(translatedArt.aotPatchBase64)

        // Any pixel inside translatedArt's patch that overlaps untranslatedNeighbor [300..317] must remain sentinelColor
        val region = requireNotNull(translatedArt.aotPatchRegion)
        val patch = requireNotNull(capturedPatch)
        val pLeft = region.x.toInt()
        val pTop = region.y.toInt()
        val pH = patch.size / capturedW
        for (ly in 0 until pH) {
            val rawY = pTop + ly
            for (lx in 0 until capturedW) {
                val rawX = pLeft + lx
                if (rawX in 202..317 && rawY in 302..315) {
                    assertEquals(sentinelColor, patch[ly * capturedW + lx])
                }
            }
        }
    }
}
