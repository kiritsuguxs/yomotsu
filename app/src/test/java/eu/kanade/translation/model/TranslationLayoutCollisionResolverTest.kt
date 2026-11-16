package eu.kanade.translation.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationLayoutCollisionResolverTest {

    @Test
    fun `overlapping dialogue bubbles are partitioned vertically to eliminate collision`() {
        val topBlock = TranslationBlock(
            text = "THAT'S WHAT",
            translation = "É O QUE",
            x = 100f,
            y = 100f,
            width = 120f,
            height = 30f,
            symWidth = 10f,
            symHeight = 18f,
            angle = 0f,
            layoutRegion = TranslationRegion(80f, 80f, 160f, 140f),
            balloonDetected = false,
        )
        val bottomBlock = TranslationBlock(
            text = "I THINK.",
            translation = "EU ACHO.",
            x = 105f,
            y = 150f,
            width = 110f,
            height = 30f,
            symWidth = 10f,
            symHeight = 18f,
            angle = 0f,
            layoutRegion = TranslationRegion(80f, 90f, 160f, 140f),
            balloonDetected = true,
        )

        val resolved = TranslationLayoutCollisionResolver.computeNonOverlappingLayouts(
            listOf(topBlock, bottomBlock),
            pageWidth = 400f,
            pageHeight = 600f,
        )

        assertEquals(2, resolved.size)
        val topReg = resolved[0]
        val botReg = resolved[1]

        // Top region bottom edge must not overlap bottom region top edge
        assertTrue(topReg.y + topReg.height <= botReg.y + 1f, "Top block must not collide with bottom block: $topReg vs $botReg")
    }

    @Test
    fun `translated block layout is bounded away from untranslated neighbor lines`() {
        val untranslated = TranslationBlock(
            text = "ORIGINAL ART LINE",
            translation = "",
            x = 100f,
            y = 80f,
            width = 120f,
            height = 25f,
            symWidth = 10f,
            symHeight = 18f,
            angle = 0f,
        )
        val translated = TranslationBlock(
            text = "LOWER DIALOGUE",
            translation = "DIÁLOGO INFERIOR",
            x = 100f,
            y = 140f,
            width = 120f,
            height = 25f,
            symWidth = 10f,
            symHeight = 18f,
            angle = 0f,
            layoutRegion = TranslationRegion(70f, 60f, 180f, 160f), // Expanded upward right over the untranslated line
        )

        val resolved = TranslationLayoutCollisionResolver.computeNonOverlappingLayouts(
            listOf(untranslated, translated),
            pageWidth = 400f,
            pageHeight = 600f,
        )

        val clampedTranslated = resolved[1]
        assertTrue(
            clampedTranslated.y >= untranslated.y + untranslated.height,
            "Translated layout must not cover untranslated text: $clampedTranslated vs untranslated Y=${untranslated.y + untranslated.height}",
        )
    }

    @Test
    fun `layoutCustomized block keeps user layout even when cleanupRegion stays anchored`() {
        val customized = TranslationBlock(
            text = "EDITED",
            translation = "EDITADO",
            x = 100f,
            y = 100f,
            width = 80f,
            height = 25f,
            symWidth = 10f,
            symHeight = 16f,
            angle = 0f,
            layoutRegion = TranslationRegion(60f, 70f, 200f, 140f),
            cleanupRegion = TranslationRegion(96f, 96f, 88f, 33f),
            layoutCustomized = true,
            geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION,
        )
        val neighbor = TranslationBlock(
            text = "NEIGHBOR",
            translation = "VIZINHO",
            x = 100f,
            y = 170f,
            width = 100f,
            height = 25f,
            symWidth = 10f,
            symHeight = 16f,
            angle = 0f,
            layoutRegion = TranslationRegion(80f, 150f, 140f, 70f),
            geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION,
        )

        val resolved = TranslationLayoutCollisionResolver.computeNonOverlappingLayouts(
            listOf(customized, neighbor),
            pageWidth = 400f,
            pageHeight = 600f,
        )

        assertEquals(customized.layoutRegion, resolved[0])
    }
}
