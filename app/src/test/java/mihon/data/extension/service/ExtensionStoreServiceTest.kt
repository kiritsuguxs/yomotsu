package mihon.data.extension.service

import eu.kanade.tachiyomi.network.NetworkHelper
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.data.extension.model.NetworkExtensionStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class ExtensionStoreServiceTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun createService(routes: Map<String, ByteArray>): ExtensionStoreService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val payload = routes[url]
                if (payload == null) {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body(ByteArray(0).toResponseBody("application/octet-stream".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(payload.toResponseBody("application/octet-stream".toMediaType()))
                        .build()
                }
            }
            .build()

        val network = mockk<NetworkHelper> {
            every { this@mockk.client } returns client
        }
        return ExtensionStoreService(network, json, ProtoBuf)
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    private class ProtoWriter {
        private val out = ByteArrayOutputStream()

        fun varint(fieldNumber: Int, value: Long) {
            writeVarint(((fieldNumber shl 3) or 0).toLong())
            writeVarint(value)
        }

        fun string(fieldNumber: Int, value: String) {
            bytes(fieldNumber, value.toByteArray(Charsets.UTF_8))
        }

        fun bytes(fieldNumber: Int, value: ByteArray) {
            writeVarint(((fieldNumber shl 3) or 2).toLong())
            writeVarint(value.size.toLong())
            out.write(value)
        }

        fun message(fieldNumber: Int, block: ProtoWriter.() -> Unit) {
            val nested = ProtoWriter().apply(block).toByteArray()
            bytes(fieldNumber, nested)
        }

        fun toByteArray(): ByteArray = out.toByteArray()

        private fun writeVarint(v: Long) {
            var value = v
            while (true) {
                if ((value and 0x7FL.inv()) == 0L) {
                    out.write(value.toInt())
                    return
                }
                out.write(((value.toInt() and 0x7F) or 0x80))
                value = value ushr 7
            }
        }
    }

    private fun buildAniyomiExtensionListBytes(
        name: String,
        packageName: String,
        apkUrl: String,
        iconUrl: String,
        extensionLib: String,
        versionCode: Long,
        versionName: String,
        contentWarning: Int,
        isTorrent: Boolean,
        sourceId: Long,
        sourceName: String,
        sourceLang: String,
        sourceHomeUrl: String,
        sourceMessage: String? = null,
    ): ByteArray {
        return ProtoWriter().apply {
            message(1) {
                string(1, name)
                string(2, packageName)
                message(3) {
                    string(1, apkUrl)
                    string(2, iconUrl)
                }
                string(4, extensionLib)
                varint(5, versionCode)
                string(6, versionName)
                varint(7, contentWarning.toLong())
                if (isTorrent) {
                    varint(8, 1L)
                }
                message(9) {
                    varint(1, sourceId)
                    string(2, sourceName)
                    string(3, sourceLang)
                    string(4, sourceHomeUrl)
                    if (sourceMessage != null) {
                        string(6, sourceMessage)
                    }
                }
            }
        }.toByteArray()
    }

    @Test
    fun `fetch and getExtensions decode gzipped Aniyomi index pb with torrent and sources`() = runTest {
        val url = "https://example.com/anime-extensoes/repo/index.pb"
        val listBytes = buildAniyomiExtensionListBytes(
            name = "Aniyomi: Nyaa (Torrent)",
            packageName = "eu.kanade.tachiyomi.animeextension.all.nyaatorrent",
            apkUrl = "https://example.com/apk/nyaa.apk",
            iconUrl = "https://example.com/icon/nyaa.png",
            extensionLib = "14",
            versionCode = 1400011,
            versionName = "14.11",
            contentWarning = 3,
            isTorrent = true,
            sourceId = 123456L,
            sourceName = "Nyaa",
            sourceLang = "all",
            sourceHomeUrl = "https://nyaa.si",
            sourceMessage = "Torrent source",
        )
        val payload = ProtoWriter().apply {
            string(1, "Project Nox Anime")
            string(2, "Nox Anime")
            string(3, "144f44e865cbfae6bde110489ef7ec142f0b1681f627a093a218650e21db5dad")
            message(4) {
                string(1, "https://github.com/ProjectNox-org/anime-extensoes")
            }
            bytes(101, listBytes)
        }.toByteArray()

        val service = createService(mapOf(url to gzip(payload)))

        val store = service.fetch(url).getOrThrow()
        assertEquals("Project Nox Anime", store.name)
        assertEquals("Nox Anime", store.badgeLabel)
        assertFalse(store.isLegacy)

        val extensions = service.getExtensions(store).getOrThrow()
        assertEquals(1, extensions.size)
        val ext = extensions.single()
        assertEquals("Nyaa (Torrent)", ext.name)
        assertTrue(ext.isAnime)
        assertTrue(ext.isNsfw)
        assertEquals("all", ext.lang)
        assertEquals(1, ext.sources.size)
        assertEquals("Nyaa", ext.sources.single().name)
        assertEquals("https://nyaa.si", ext.sources.single().baseUrl)
    }

    @Test
    fun `fetch and getExtensions preserve sources for Aniyomi index pb without torrent flags`() = runTest {
        val url = "https://example.com/anime/index.pb"
        val listBytes = buildAniyomiExtensionListBytes(
            name = "Aniyomi: AnimeOnsen",
            packageName = "eu.kanade.tachiyomi.animeextension.pt.animeonsen",
            apkUrl = "https://example.com/apk/onsen.apk",
            iconUrl = "https://example.com/icon/onsen.png",
            extensionLib = "14",
            versionCode = 1400011,
            versionName = "14.11",
            contentWarning = 1,
            isTorrent = false,
            sourceId = 999L,
            sourceName = "AnimeOnsen",
            sourceLang = "pt-BR",
            sourceHomeUrl = "https://www.animeonsen.xyz",
        )
        val payload = ProtoWriter().apply {
            string(1, "Aniyomi Repo")
            string(2, "Anime")
            string(3, "ANIME_REPO")
            message(4) {
                string(1, "https://example.com")
            }
            bytes(101, listBytes)
        }.toByteArray()

        val service = createService(mapOf(url to payload))

        val store = service.fetch(url).getOrThrow()
        val extensions = service.getExtensions(store).getOrThrow()
        assertEquals(1, extensions.size)
        val ext = extensions.single()
        assertEquals("pt-BR", ext.lang)
        assertEquals(1, ext.sources.size)
        assertEquals(999L, ext.sources.single().id)
        assertEquals("AnimeOnsen", ext.sources.single().name)
    }

    @Test
    fun `fetch and getExtensions decode Mihon index pb`() = runTest {
        val url = "https://example.com/manga/index.pb"
        val payload = ProtoBuf.encodeToByteArray(
            NetworkExtensionStore(
                name = "Mihon Repo",
                badgeLabel = "MNG",
                signingKey = "MANGA_KEY",
                contact = NetworkExtensionStore.Contact(website = "https://example.com"),
                extensionList = NetworkExtensionStore.ExtensionList(
                    extensions = listOf(
                        NetworkExtensionStore.Extension(
                            name = "Tachiyomi: ExampleManga",
                            packageName = "eu.kanade.tachiyomi.extension.en.example",
                            resources = NetworkExtensionStore.Resources(
                                apkUrl = "https://example.com/apk/example.apk",
                                iconUrl = "https://example.com/icon/example.png",
                            ),
                            extensionLib = "1.4",
                            versionCode = 1,
                            versionName = "1.4.1",
                            contentWarning = NetworkExtensionStore.ContentWarning.SAFE,
                            sources = listOf(
                                NetworkExtensionStore.Source(
                                    id = 42L,
                                    name = "Example",
                                    language = "en",
                                    homeUrl = "https://example.org",
                                    message = "Notice",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val service = createService(mapOf(url to gzip(payload)))

        val store = service.fetch(url).getOrThrow()
        val extensions = service.getExtensions(store).getOrThrow()
        assertEquals(1, extensions.size)
        val ext = extensions.single()
        assertEquals("ExampleManga", ext.name)
        assertFalse(ext.isAnime)
        assertFalse(ext.isNsfw)
        assertEquals("en", ext.lang)
        assertEquals(42L, ext.sources.single().id)
    }

    @Test
    fun `getExtensions decodes Aniyomi ExtensionList from extensionListUrl`() = runTest {
        val indexUrl = "https://example.com/anime/index.pb"
        val listUrl = "https://example.com/anime/extensions.pb"
        val storePayload = ProtoWriter().apply {
            string(1, "Split Aniyomi Repo")
            string(2, "Anime")
            string(3, "ANIME_REPO")
            message(4) {
                string(1, "https://example.com")
            }
            string(102, listUrl)
        }.toByteArray()
        val listPayload = buildAniyomiExtensionListBytes(
            name = "Aniyomi: SplitExt",
            packageName = "eu.kanade.tachiyomi.animeextension.en.split",
            apkUrl = "https://example.com/apk/split.apk",
            iconUrl = "https://example.com/icon/split.png",
            extensionLib = "15",
            versionCode = 1500001,
            versionName = "15.1",
            contentWarning = 1,
            isTorrent = true,
            sourceId = 777L,
            sourceName = "SplitSource",
            sourceLang = "en",
            sourceHomeUrl = "https://split.example.com",
        )

        val service = createService(
            mapOf(
                indexUrl to storePayload,
                listUrl to gzip(listPayload),
            ),
        )

        val store = service.fetch(indexUrl).getOrThrow()
        assertEquals(listUrl, store.extensionListUrl)
        val extensions = service.getExtensions(store).getOrThrow()
        assertEquals(1, extensions.size)
        assertEquals("SplitExt", extensions.single().name)
        assertEquals(777L, extensions.single().sources.single().id)
    }

    @Test
    fun `fetch fails on invalid protobuf payload`() = runTest {
        val url = "https://example.com/broken/index.pb"
        val service = createService(mapOf(url to byteArrayOf(0x01, 0x02, 0x03)))

        val result = service.fetch(url)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SerializationException)
    }
}
