package eu.kanade.tachiyomi.extension

import eu.kanade.tachiyomi.extension.model.Extension
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtensionVersionCheckTest {

    private val dummyStore = ExtensionStore(
        indexUrl = "https://example.com/index.pb",
        name = "Test Store",
        badgeLabel = "Anime",
        signingKey = "TEST_KEY",
        contact = ExtensionStore.Contact(website = "https://example.com", discord = null),
        isLegacy = false,
        extensionListUrl = null,
    )

    private fun installed(
        versionCode: Long,
        versionName: String,
        libVersion: Double,
        isAnime: Boolean = true,
    ) = Extension.Installed(
        name = "Test",
        pkgName = "eu.kanade.tachiyomi.animeextension.pt.test",
        versionName = versionName,
        versionCode = versionCode,
        libVersion = libVersion,
        lang = "pt-BR",
        isNsfw = false,
        isAnime = isAnime,
        pkgFactory = null,
        sources = emptyList(),
        icon = null,
        isShared = false,
    )

    private fun available(
        versionCode: Long,
        versionName: String,
        libVersion: Double,
        isAnime: Boolean = true,
    ) = Extension.Available(
        name = "Test",
        pkgName = "eu.kanade.tachiyomi.animeextension.pt.test",
        versionName = versionName,
        versionCode = versionCode,
        libVersion = libVersion,
        lang = "pt-BR",
        isNsfw = false,
        isAnime = isAnime,
        sources = emptyList(),
        apkUrl = "https://example.com/test.apk",
        iconUrl = "https://example.com/test.png",
        store = dummyStore,
    )

    @Test
    fun `hasExtensionUpdate ignores composite repo versionCode when versionName matches installed APK`() {
        assertFalse(
            hasExtensionUpdate(
                installed = installed(versionCode = 6, versionName = "14.6", libVersion = 14.0),
                available = available(versionCode = 1400006, versionName = "14.6", libVersion = 14.0),
                isAnime = true,
            ),
        )
        assertFalse(
            hasExtensionUpdate(
                installed = installed(versionCode = 16, versionName = "16.16", libVersion = 16.0),
                available = available(versionCode = 1600016, versionName = "16.16", libVersion = 16.0),
                isAnime = true,
            ),
        )
    }

    @Test
    fun `hasExtensionUpdate detects real updates even with composite repo versionCode`() {
        assertTrue(
            hasExtensionUpdate(
                installed = installed(versionCode = 6, versionName = "14.6", libVersion = 14.0),
                available = available(versionCode = 1400007, versionName = "14.7", libVersion = 14.0),
                isAnime = true,
            ),
        )
        assertTrue(
            hasExtensionUpdate(
                installed = installed(versionCode = 16, versionName = "16.16", libVersion = 16.0),
                available = available(versionCode = 1700001, versionName = "17.1", libVersion = 17.0),
                isAnime = true,
            ),
        )
    }

    @Test
    fun `hasExtensionUpdate handles standard matching versionCodes for anime and manga`() {
        assertFalse(
            hasExtensionUpdate(
                installed = installed(versionCode = 11, versionName = "14.11", libVersion = 14.0),
                available = available(versionCode = 11, versionName = "14.11", libVersion = 14.0),
                isAnime = true,
            ),
        )
        assertTrue(
            hasExtensionUpdate(
                installed = installed(versionCode = 11, versionName = "14.11", libVersion = 14.0),
                available = available(versionCode = 12, versionName = "14.12", libVersion = 14.0),
                isAnime = true,
            ),
        )
        assertFalse(
            hasExtensionUpdate(
                installed = installed(versionCode = 106001, versionName = "1.6.1", libVersion = 1.6, isAnime = false),
                available = available(versionCode = 106001, versionName = "1.6.1", libVersion = 1.6, isAnime = false),
                isAnime = false,
            ),
        )
        assertTrue(
            hasExtensionUpdate(
                installed = installed(versionCode = 106001, versionName = "1.6.1", libVersion = 1.6, isAnime = false),
                available = available(versionCode = 106002, versionName = "1.6.2", libVersion = 1.6, isAnime = false),
                isAnime = false,
            ),
        )
    }

    @Test
    fun `hasExtensionUpdate handles composite installed APK against simple repo versionCode`() {
        assertFalse(
            hasExtensionUpdate(
                installed = installed(versionCode = 1400006, versionName = "14.6", libVersion = 14.0),
                available = available(versionCode = 6, versionName = "14.6", libVersion = 14.0),
                isAnime = true,
            ),
        )
        assertTrue(
            hasExtensionUpdate(
                installed = installed(versionCode = 1400006, versionName = "14.6", libVersion = 14.0),
                available = available(versionCode = 7, versionName = "14.7", libVersion = 14.0),
                isAnime = true,
            ),
        )
    }

    @Test
    fun `deduplicateAvailableExtensions keeps highest version across stores`() {
        val older = available(versionCode = 6, versionName = "14.6", libVersion = 14.0)
        val newer = available(versionCode = 1400008, versionName = "14.8", libVersion = 14.0)
        val result = deduplicateAvailableExtensions(listOf(newer, older))
        assertTrue(result.size == 1 && result.single().versionName == "14.8")
    }
}
