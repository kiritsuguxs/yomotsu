package eu.kanade.translation.recognizer

import android.graphics.Bitmap
import android.graphics.Color
import eu.kanade.translation.model.TranslationBlock
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SpeechBubbleOvalLayoutRegressionTest {

    @BeforeEach
    fun setUpAndroidColors() {
        mockkStatic(Color::class)
        every { Color.alpha(any()) } answers { firstArg<Int>() ushr 24 and 0xff }
        every { Color.red(any()) } answers { firstArg<Int>() ushr 16 and 0xff }
        every { Color.green(any()) } answers { firstArg<Int>() ushr 8 and 0xff }
        every { Color.blue(any()) } answers { firstArg<Int>() and 0xff }
        every { Color.rgb(any<Int>(), any<Int>(), any<Int>()) } answers {
            0xff000000.toInt() or (firstArg<Int>() shl 16) or (secondArg<Int>() shl 8) or thirdArg<Int>()
        }
    }

    @AfterEach
    fun tearDownAndroidColors() = unmockkStatic(Color::class)

    @Test
    fun `narrow oval balloon keeps detected layout inside curved shoulders`() {
        val samplePixel: (Int, Int) -> Int = { x, y ->
            val dx = (x - CENTER_X) / RADIUS_X.toFloat()
            val dy = (y - CENTER_Y) / RADIUS_Y.toFloat()
            if (dx * dx + dy * dy <= 1f) 0xffffffff.toInt() else 0xff000000.toInt()
        }
        val bitmap: Bitmap = mockk {
            every { width } returns PAGE_SIZE
            every { height } returns PAGE_SIZE
            every { getPixel(any(), any()) } answers { samplePixel(firstArg(), secondArg()) }
            every { getPixels(any(), any(), any(), any(), any(), any(), any()) } answers {
                val out = firstArg<IntArray>()
                val offset = secondArg<Int>()
                val stride = thirdArg<Int>()
                val x0 = arg<Int>(3)
                val y0 = arg<Int>(4)
                val w = arg<Int>(5)
                val h = arg<Int>(6)
                for (row in 0 until h) {
                    for (col in 0 until w) {
                        out[offset + row * stride + col] = samplePixel(x0 + col, y0 + row)
                    }
                }
            }
        }
        val block = TranslationBlock(
            text = "EXCUSE ME",
            translation = "...COM LICENÇA",
            width = 54f,
            height = 18f,
            x = 73f,
            y = 91f,
            symHeight = 9f,
            symWidth = 6f,
            angle = 0f,
        )

        val analysis = SpeechBubbleAnalyzer(bitmap, PAGE_SIZE, PAGE_SIZE).analyze(block)

        assertTrue(analysis.balloonDetected)
        val layout = analysis.layoutRegion
        val topShoulderHalfWidth = ellipseHalfWidthAtY(layout.y)
        val bottomShoulderHalfWidth = ellipseHalfWidthAtY(layout.y + layout.height)
        val layoutHalfWidth = layout.width / 2f
        assertTrue(
            layoutHalfWidth <= minOf(topShoulderHalfWidth, bottomShoulderHalfWidth),
            "The rectangular text layout must fit inside the oval at its top and bottom edges",
        )
    }

    @Test
    fun `wide black-bordered oval on white page background stays strictly inside curved borders`() {
        val pageWidth = 300
        val pageHeight = 200
        val cx = 150f
        val cy = 100f
        val rx = 120f
        val ry = 46f
        val samplePixel: (Int, Int) -> Int = { x, y ->
            val outerDx = (x - cx) / rx
            val outerDy = (y - cy) / ry
            val innerDx = (x - cx) / (rx - 4f)
            val innerDy = (y - cy) / (ry - 4f)
            val onStroke = outerDx * outerDx + outerDy * outerDy <= 1f &&
                innerDx * innerDx + innerDy * innerDy > 1f
            // White interior AND white page background outside, separated only by the 4px black border.
            if (onStroke) 0xff000000.toInt() else 0xffffffff.toInt()
        }
        val bitmap: Bitmap = mockk {
            every { width } returns pageWidth
            every { height } returns pageHeight
            every { getPixel(any(), any()) } answers { samplePixel(firstArg(), secondArg()) }
            every { getPixels(any(), any(), any(), any(), any(), any(), any()) } answers {
                val out = firstArg<IntArray>()
                val offset = secondArg<Int>()
                val stride = thirdArg<Int>()
                val x0 = arg<Int>(3)
                val y0 = arg<Int>(4)
                val w = arg<Int>(5)
                val h = arg<Int>(6)
                for (row in 0 until h) {
                    for (col in 0 until w) {
                        out[offset + row * stride + col] = samplePixel(x0 + col, y0 + row)
                    }
                }
            }
        }
        val block = TranslationBlock(
            text = "WHO CHANGED WAS MOM.",
            translation = "QUEM MUDOU FOI A MAMÃE.",
            width = 120f,
            height = 24f,
            x = 90f,
            y = 88f,
            symHeight = 10f,
            symWidth = 6f,
            angle = 0f,
        )

        val analysis = SpeechBubbleAnalyzer(bitmap, pageWidth, pageHeight).analyze(block)

        assertTrue(analysis.balloonDetected)
        val r = analysis.layoutRegion
        for (px in listOf(r.x, r.x + r.width)) {
            for (py in listOf(r.y, r.y + r.height)) {
                val norm = ((px - cx) / rx) * ((px - cx) / rx) + ((py - cy) / ry) * ((py - cy) / ry)
                assertTrue(norm <= 1.02f, "Wide oval layout corner ($px, $py) spilled outside white-on-white balloon border: $r")
            }
        }
    }

    @Test
    fun `tall narrow manhwa oval keeps layout corners inside curved border`() {
        val pageWidth = 240
        val pageHeight = 320
        val cx = 120f
        val cy = 160f
        val rx = 50f
        val ry = 100f
        val samplePixel: (Int, Int) -> Int = { x, y ->
            val dx = (x - cx) / rx
            val dy = (y - cy) / ry
            if (dx * dx + dy * dy <= 1f) 0xffffffff.toInt() else 0xff000000.toInt()
        }
        val bitmap: Bitmap = mockk {
            every { width } returns pageWidth
            every { height } returns pageHeight
            every { getPixel(any(), any()) } answers { samplePixel(firstArg(), secondArg()) }
            every { getPixels(any(), any(), any(), any(), any(), any(), any()) } answers {
                val out = firstArg<IntArray>()
                val offset = secondArg<Int>()
                val stride = thirdArg<Int>()
                val x0 = arg<Int>(3)
                val y0 = arg<Int>(4)
                val w = arg<Int>(5)
                val h = arg<Int>(6)
                for (row in 0 until h) {
                    for (col in 0 until w) {
                        out[offset + row * stride + col] = samplePixel(x0 + col, y0 + row)
                    }
                }
            }
        }
        val block = TranslationBlock(
            text = "WHY ARE YOU HERE",
            translation = "POR QUE VOCÊ ESTÁ AQUI",
            width = 48f,
            height = 48f,
            x = 96f,
            y = 136f,
            symHeight = 10f,
            symWidth = 6f,
            angle = 0f,
        )

        val analysis = SpeechBubbleAnalyzer(bitmap, pageWidth, pageHeight).analyze(block)

        assertTrue(analysis.balloonDetected)
        val r = analysis.layoutRegion
        for (px in listOf(r.x, r.x + r.width)) {
            for (py in listOf(r.y, r.y + r.height)) {
                val norm = ((px - cx) / rx) * ((px - cx) / rx) + ((py - cy) / ry) * ((py - cy) / ry)
                assertTrue(norm <= 1.02f, "Tall narrow oval corner ($px, $py) spilled outside balloon border: $r")
            }
        }
    }

    private fun ellipseHalfWidthAtY(y: Float): Float {
        val dy = ((y - CENTER_Y) / RADIUS_Y).coerceIn(-1f, 1f)
        return RADIUS_X * kotlin.math.sqrt(1f - dy * dy)
    }

    private companion object {
        const val PAGE_SIZE = 200
        const val CENTER_X = 100
        const val CENTER_Y = 100
        const val RADIUS_X = 58
        const val RADIUS_Y = 38
    }
}
