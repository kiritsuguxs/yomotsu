package eu.kanade.tachiyomi.extension.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtensionLoaderTest {

    @Test
    fun `isContentWarningNsfw treats unspecified and safe content warnings as non-nsfw`() {
        assertFalse(ExtensionLoader.isContentWarningNsfw(contentWarning = 0, legacyNsfw = 0))
        assertFalse(ExtensionLoader.isContentWarningNsfw(contentWarning = 1, legacyNsfw = 0))
    }

    @Test
    fun `isContentWarningNsfw treats mixed, nsfw, and legacy nsfw flags as nsfw`() {
        assertTrue(ExtensionLoader.isContentWarningNsfw(contentWarning = 2, legacyNsfw = 0))
        assertTrue(ExtensionLoader.isContentWarningNsfw(contentWarning = 3, legacyNsfw = 0))
        assertTrue(ExtensionLoader.isContentWarningNsfw(contentWarning = 0, legacyNsfw = 1))
        assertTrue(ExtensionLoader.isContentWarningNsfw(contentWarning = 1, legacyNsfw = 1))
    }
}
