package eu.kanade.translation.model

import eu.kanade.translation.detection.DbnetCleanupMask
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationGeometryTest {
    @Test
    fun `IoU and directional coverage distinguish overlap from containment`() {
        val first = TranslationRegion(0f, 0f, 10f, 10f)
        val overlap = TranslationRegion(5f, 0f, 10f, 10f)
        val inner = TranslationRegion(2f, 2f, 2f, 2f)
        assertEquals(1f, first.intersectionOverUnion(first))
        assertEquals(1f / 3f, first.intersectionOverUnion(overlap), 0.00001f)
        assertEquals(first.intersectionOverUnion(overlap), overlap.intersectionOverUnion(first))
        assertEquals(0.5f, first.overlapFraction(overlap))
        assertEquals(1f, inner.overlapFraction(first))
        assertEquals(0.04f, first.overlapFraction(inner), 0.00001f)
    }

    @Test
    fun `disjoint touching and invalid geometry have zero overlap`() {
        val first = TranslationRegion(0f, 0f, 10f, 10f)
        listOf(
            TranslationRegion(10f, 0f, 10f, 10f),
            TranslationRegion(20f, 0f, 10f, 10f),
            first.copy(width = 0f), first.copy(height = -1f),
            first.copy(x = Float.NaN), first.copy(width = Float.POSITIVE_INFINITY),
        ).forEach {
            assertEquals(0f, first.intersectionOverUnion(it))
            assertEquals(0f, it.overlapFraction(first))
        }
    }

    @Test
    fun `cleanup region expands OCR bounds and stays inside page`() {
        val block = translationBlock(x = 4f, y = 6f)

        val region = block.defaultCleanupRegion(pageWidth = 200f, pageHeight = 120f)

        assertEquals(0f, region.x)
        assertEquals(0f, region.y)
        assertTrue(region.width > block.width)
        assertTrue(region.height > block.height)
        assertTrue(region.x + region.width <= 200f)
        assertTrue(region.y + region.height <= 120f)
    }

    @Test
    fun `layout region gives translation more room than cleanup region`() {
        val block = translationBlock()

        val cleanup = block.defaultCleanupRegion(500f, 500f)
        val layout = block.defaultLayoutRegion(500f, 500f)

        assertTrue(layout.width > cleanup.width)
        assertTrue(layout.height > cleanup.height)
        assertTrue(layout.x < cleanup.x)
        assertTrue(layout.y < cleanup.y)
    }

    @Test
    fun `inset keeps text away from rounded balloon edges`() {
        val region = TranslationRegion(x = 20f, y = 30f, width = 100f, height = 80f)

        val inset = region.inset(horizontalInset = 10f, verticalInset = 8f)

        assertEquals(30f, inset.x)
        assertEquals(38f, inset.y)
        assertEquals(80f, inset.width)
        assertEquals(64f, inset.height)
    }

    @Test
    fun `clamped region never crosses right or bottom page edge`() {
        val region = TranslationRegion(x = 470f, y = 780f, width = 80f, height = 60f)

        val clamped = region.clamped(pageWidth = 500f, pageHeight = 800f)

        assertEquals(470f, clamped.x)
        assertEquals(780f, clamped.y)
        assertEquals(30f, clamped.width)
        assertEquals(20f, clamped.height)
        assertTrue(clamped.x + clamped.width <= 500f)
        assertTrue(clamped.y + clamped.height <= 800f)
    }

    @Test
    fun `clamped region that starts beyond page edge is moved fully inside`() {
        val region = TranslationRegion(x = 540f, y = 830f, width = 50f, height = 40f)

        val clamped = region.clamped(pageWidth = 500f, pageHeight = 800f)

        assertEquals(499f, clamped.x)
        assertEquals(799f, clamped.y)
        assertEquals(1f, clamped.width)
        assertEquals(1f, clamped.height)
        assertTrue(clamped.x + clamped.width <= 500f)
        assertTrue(clamped.y + clamped.height <= 800f)
    }

    @Test
    fun `clamped sanitizes non finite geometry before rendering`() {
        val region = TranslationRegion(
            x = Float.NaN,
            y = Float.POSITIVE_INFINITY,
            width = Float.NaN,
            height = Float.NEGATIVE_INFINITY,
        )

        val clamped = region.clamped(pageWidth = 500f, pageHeight = 800f)

        assertTrue(clamped.x.isFinite())
        assertTrue(clamped.y.isFinite())
        assertTrue(clamped.width.isFinite() && clamped.width >= 1f)
        assertTrue(clamped.height.isFinite() && clamped.height >= 1f)
        assertTrue(clamped.x >= 0f && clamped.x + clamped.width <= 500f)
        assertTrue(clamped.y >= 0f && clamped.y + clamped.height <= 800f)
    }

    @Test
    fun `clamped keeps an already valid region unchanged`() {
        val region = TranslationRegion(x = 70f, y = 80f, width = 140f, height = 100f)

        val clamped = region.clamped(pageWidth = 500f, pageHeight = 800f)

        assertEquals(region, clamped)
    }

    @Test
    fun `layout saved by first Y9 falls back to safer geometry`() {
        val staleRegion = TranslationRegion(x = 10f, y = 10f, width = 300f, height = 240f)
        val block = translationBlock().copy(layoutRegion = staleRegion)

        val resolved = block.resolvedLayoutRegion(pageWidth = 500f, pageHeight = 500f)

        assertEquals(block.defaultLayoutRegion(500f, 500f), resolved)
    }

    @Test
    fun `current layout geometry keeps detected balloon region`() {
        val detectedRegion = TranslationRegion(x = 70f, y = 80f, width = 140f, height = 100f)
        val block = translationBlock().copy(
            layoutRegion = detectedRegion,
            geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION,
        )

        val resolved = block.resolvedLayoutRegion(pageWidth = 500f, pageHeight = 500f)

        assertEquals(detectedRegion, resolved)
    }

    @Test
    fun `Y8 translation json remains compatible with Y9 geometry`() {
        val block = Json.decodeFromString<TranslationBlock>(
            """{"text":"Hello","translation":"Olá","width":80.0,"height":30.0,"x":20.0,"y":40.0,"symHeight":15.0,"symWidth":10.0,"angle":0.0}""",
        )

        assertEquals("Olá", block.translation)
        assertNull(block.cleanupRegion)
        assertNull(block.layoutRegion)
        assertNull(block.backgroundColor)
        assertNull(block.foregroundColor)
        assertEquals(false, block.balloonDetected)
        assertEquals(false, block.layoutCustomized)
        assertEquals(1, block.geometryVersion)
    }

    @Test
    fun `applyCustomizationGeometry updates layout without expanding cleanup regions`() {
        val originalLayout = TranslationRegion(80f, 80f, 120f, 70f)
        val originalCleanup = TranslationRegion(96f, 96f, 88f, 38f)
        val sourcePatch = TranslationRegion(98f, 98f, 84f, 34f)
        val aotRegion = TranslationRegion(95f, 95f, 90f, 40f)
        val mask = DbnetCleanupMask.fromRuns(500, 500, intArrayOf(100, 100, 20))
        val block = translationBlock(x = 100f, y = 100f).copy(
            layoutRegion = originalLayout,
            cleanupRegion = originalCleanup,
            sourceCleanupRegions = listOf(sourcePatch),
            dbnetCleanupMask = mask,
            aotPatchRegion = aotRegion,
            balloonDetected = true,
            geometryVersion = 1,
        )

        block.applyCustomizationGeometry(
            widthScale = 1.5f,
            heightScale = 1.2f,
            offsetX = 0.25f,
            offsetY = -0.10f,
        )

        val updatedLayout = requireNotNull(block.layoutRegion)
        assertEquals(180f, updatedLayout.width, 0.001f)
        assertEquals(84f, updatedLayout.height, 0.001f)
        assertEquals(80f, updatedLayout.x, 0.001f) // - (180-120)/2 + 120*0.25 = -30 + 30 = 0 delta
        assertEquals(66f, updatedLayout.y, 0.001f) // - (84-70)/2 - 70*0.10 = -7 - 7 = -14 delta
        assertTrue(block.layoutCustomized)
        assertEquals(CURRENT_TRANSLATION_GEOMETRY_VERSION, block.geometryVersion)
        assertEquals(100f, block.x)
        assertEquals(100f, block.y)
        assertEquals(originalCleanup, block.cleanupRegion)
        assertEquals(listOf(sourcePatch), block.sourceCleanupRegions)
        assertEquals(mask, block.dbnetCleanupMask)
        assertEquals(aotRegion, block.aotPatchRegion)
    }

    @Test
    fun `applyCustomizationGeometry with neutral scale and zero offset is a no-op`() {
        val originalLayout = TranslationRegion(80f, 80f, 120f, 70f)
        val block = translationBlock().copy(layoutRegion = originalLayout)

        block.applyCustomizationGeometry(1.0f, 1.0f, 0f, 0f)

        assertFalse(block.layoutCustomized)
        assertEquals(originalLayout, block.layoutRegion)
    }

    @Test
    fun `resolveCleanup keeps DbnetMaskCleanup when layoutCustomized is true`() {
        val region = TranslationRegion(80f, 80f, 120f, 70f)
        val mask = DbnetCleanupMask.fromRuns(500, 500, intArrayOf(100, 100, 20))
        val uncustomized = translationBlock().copy(
            layoutRegion = region,
            cleanupRegion = region,
            dbnetCleanupMask = mask,
            layoutCustomized = false,
        )
        val customized = uncustomized.copy(layoutCustomized = true)

        assertTrue(uncustomized.resolveCleanup(500f, 500f) is LegacyCleanup)
        assertTrue(customized.resolveCleanup(500f, 500f) is DbnetMaskCleanup)
    }

    @Test
    fun `resolvedLayoutRegion does not expand downward when balloon background color exists`() {
        val baseRegion = TranslationRegion(80f, 80f, 120f, 60f)
        val longTranslation = "Esta tradução é bem maior do que o texto original para testar a expansão"
        val freeTextBlock = translationBlock().copy(
            text = "Hello",
            translation = longTranslation,
            layoutRegion = baseRegion,
            geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION,
            balloonDetected = false,
            backgroundColor = null,
        )
        val balloonBackgroundBlock = freeTextBlock.copy(backgroundColor = 0xffffffff.toInt())

        val freeResolved = freeTextBlock.resolvedLayoutRegion(500f, 500f)
        val balloonResolved = balloonBackgroundBlock.resolvedLayoutRegion(500f, 500f)

        assertTrue(freeResolved.height > baseRegion.height)
        assertEquals(baseRegion, balloonResolved)
    }

    private fun translationBlock(
        x: Float = 100f,
        y: Float = 100f,
    ) = TranslationBlock(
        text = "Hello world",
        translation = "Olá, mundo",
        width = 80f,
        height = 30f,
        x = x,
        y = y,
        symHeight = 15f,
        symWidth = 10f,
        angle = 0f,
    )
}
