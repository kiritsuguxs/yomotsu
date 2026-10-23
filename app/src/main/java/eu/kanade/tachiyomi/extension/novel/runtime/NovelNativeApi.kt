package eu.kanade.tachiyomi.extension.novel.runtime

import android.util.Base64
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URI
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class NovelNativeApi(
    private val client: OkHttpClient = Injekt.get<NetworkHelper>().client,
) : NovelJsRuntime.NativeApi {

    private val domStore = NovelJsDomStore()

    private data class ParsedRequest(
        val request: Request,
        val textEncoding: String?,
    )

    private fun buildRequest(url: String, optionsJson: String?): ParsedRequest {
        val requestBuilder = Request.Builder().url(url)
        var bodyParams: RequestBody? = null
        var method = "GET"
        var hasUserAgent = false
        var hasReferer = false
        var hasOrigin = false
        var contentTypeHeader: String? = null
        var textEncoding: String? = null

        if (!optionsJson.isNullOrBlank()) {
            try {
                val json = Injekt.get<Json>().parseToJsonElement(optionsJson) as? JsonObject
                method = json?.get("method")?.jsonPrimitive?.contentOrNull?.uppercase() ?: "GET"
                textEncoding = json?.get("textEncoding")?.jsonPrimitive?.contentOrNull

                val headers = json?.get("headers") as? JsonObject
                headers?.entries?.forEach { (key, valueElem) ->
                    val valStr = (valueElem as? JsonPrimitive)?.contentOrNull ?: return@forEach
                    requestBuilder.addHeader(key, valStr)
                    when (key.lowercase()) {
                        "user-agent" -> hasUserAgent = true
                        "referer", "referrer" -> hasReferer = true
                        "origin" -> hasOrigin = true
                        "content-type" -> contentTypeHeader = valStr
                    }
                }

                val referrer = json?.get("referrer")?.jsonPrimitive?.contentOrNull
                if (!referrer.isNullOrBlank() && !hasReferer) {
                    requestBuilder.addHeader("Referer", referrer)
                }
                val origin = json?.get("origin")?.jsonPrimitive?.contentOrNull
                if (!origin.isNullOrBlank() && !hasOrigin) {
                    requestBuilder.addHeader("Origin", origin)
                }

                val bodyType = json?.get("bodyType")?.jsonPrimitive?.contentOrNull
                val formEntries = json?.get("formEntries") as? JsonArray
                val bodyStr = json?.get("body")?.jsonPrimitive?.contentOrNull

                if (bodyType == "form" && formEntries != null) {
                    val formBuilder = FormBody.Builder()
                    formEntries.forEach { entryElem ->
                        val obj = entryElem as? JsonObject ?: return@forEach
                        val k = obj["key"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                        val v = obj["value"]?.jsonPrimitive?.contentOrNull ?: ""
                        formBuilder.add(k, v)
                    }
                    bodyParams = formBuilder.build()
                } else if (bodyStr != null) {
                    val mediaType = (contentTypeHeader ?: "application/x-www-form-urlencoded").toMediaTypeOrNull()
                    bodyParams = bodyStr.toRequestBody(mediaType)
                }
            } catch (_: Exception) {
                // Ignore parse errors
            }
        }

        if (!hasUserAgent) {
            val defaultUa = runCatching { Injekt.get<NetworkHelper>().defaultUserAgentProvider() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            requestBuilder.header("User-Agent", defaultUa)
        }

        requestBuilder.method(
            method,
            if (method != "GET" && method != "HEAD") bodyParams ?: "".toRequestBody() else null,
        )

        return ParsedRequest(requestBuilder.build(), textEncoding)
    }

    override fun fetch(url: String, optionsJson: String?): String {
        return try {
            val parsed = buildRequest(url, optionsJson)
            client.newCall(parsed.request).execute().use { response ->
                val bytes = response.body?.bytes() ?: ByteArray(0)
                val charset = parsed.textEncoding?.let {
                    runCatching { Charset.forName(it) }.getOrNull()
                } ?: response.body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                val responseBody = String(bytes, charset)
                val finalUrl = response.request.url.toString()

                buildJsonObject {
                    put("status", response.code)
                    put("statusText", response.message)
                    put("url", finalUrl)
                    put("body", responseBody)
                    put(
                        "headers",
                        buildJsonObject {
                            response.headers.names().forEach { name ->
                                put(name, response.headers[name] ?: "")
                            }
                        },
                    )
                }.toString()
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("status", 0)
                put("statusText", e.message ?: "Network error")
                put("url", url)
                put("body", e.message ?: "")
                put("headers", buildJsonObject {})
            }.toString()
        }
    }

    override fun fetchBinary(url: String, optionsJson: String?): String {
        return try {
            val parsed = buildRequest(url, optionsJson)
            client.newCall(parsed.request).execute().use { response ->
                val bytes = response.body?.bytes() ?: ByteArray(0)
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (_: Exception) {
            ""
        }
    }

    override fun fetchProto(url: String, configJson: String, optionsJson: String?): String = fetch(url, optionsJson)

    override fun storageGet(key: String): String? = pluginStorage[key]
    override fun storageSet(key: String, value: String) { pluginStorage[key] = value }
    override fun storageRemove(key: String) { pluginStorage.remove(key) }
    override fun storageClear() { pluginStorage.clear() }
    override fun storageKeys(): String = buildJsonArray { pluginStorage.keys.forEach { add(JsonPrimitive(it)) } }.toString()

    override fun localStorageGet(key: String): String? = localStore[key]
    override fun localStorageSet(key: String, value: String) { localStore[key] = value }
    override fun localStorageRemove(key: String) { localStore.remove(key) }
    override fun localStorageClear() { localStore.clear() }
    override fun localStorageKeys(): String = buildJsonArray { localStore.keys.forEach { add(JsonPrimitive(it)) } }.toString()

    override fun sessionStorageGet(key: String): String? = sessionStore[key]
    override fun sessionStorageSet(key: String, value: String) { sessionStore[key] = value }
    override fun sessionStorageRemove(key: String) { sessionStore.remove(key) }
    override fun sessionStorageClear() { sessionStore.clear() }
    override fun sessionStorageKeys(): String = buildJsonArray { sessionStore.keys.forEach { add(JsonPrimitive(it)) } }.toString()

    override fun resolveUrl(url: String, base: String?): String =
        runCatching { if (base != null) URI(base).resolve(url).toString() else url }.getOrDefault(url)

    override fun getPathname(url: String): String =
        runCatching { URI(url).path ?: "" }.getOrDefault("")

    override fun select(html: String, selector: String): String =
        runCatching { Jsoup.parse(html).select(selector).outerHtml() }.getOrDefault("")

    override fun aesGcmDecrypt(keyB64: String, ivB64: String, cipherB64: String): String {
        return try {
            val keyBytes = Base64.decode(keyB64, Base64.DEFAULT)
            val ivBytes = Base64.decode(ivB64, Base64.DEFAULT)
            val cipherBytes = Base64.decode(cipherB64, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val gcmSpec = GCMParameterSpec(128, ivBytes)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            val plainBytes = cipher.doFinal(cipherBytes)
            Base64.encodeToString(plainBytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }

    override fun urlEncode(value: String, charsetName: String?): String =
        java.net.URLEncoder.encode(value, charsetName ?: "UTF-8")

    override fun urlDecode(value: String, charsetName: String?): String =
        java.net.URLDecoder.decode(value, charsetName ?: "UTF-8")

    override fun domLoad(html: String): Int = domStore.loadDocument(html)

    override fun domSelect(handle: Int, selector: String): String =
        domStore.select(handle, selector).joinToString(",", "[", "]")

    override fun domParent(handle: Int): Int = domStore.parent(handle)

    override fun domChildren(handle: Int, selector: String?): String =
        domStore.children(handle, selector).joinToString(",", "[", "]")

    override fun domNext(handle: Int, selector: String?): Int = domStore.next(handle, selector)

    override fun domPrev(handle: Int, selector: String?): Int = domStore.prev(handle, selector)

    override fun domNextAll(handle: Int, selector: String?): String =
        domStore.nextAll(handle, selector).joinToString(",", "[", "]")

    override fun domPrevAll(handle: Int, selector: String?): String =
        domStore.prevAll(handle, selector).joinToString(",", "[", "]")

    override fun domSiblings(handle: Int, selector: String?): String =
        domStore.siblings(handle, selector).joinToString(",", "[", "]")

    override fun domClosest(handle: Int, selector: String): Int = domStore.closest(handle, selector)

    override fun domContents(handle: Int): String =
        domStore.contents(handle).joinToString(",", "[", "]")

    override fun domIs(handle: Int, selector: String): Boolean = domStore.matches(handle, selector)
    override fun domHas(handle: Int, selector: String): Boolean = domStore.has(handle, selector)
    override fun domNot(handle: Int, selector: String): String =
        domStore.not(handle, selector).joinToString(",", "[", "]")
    override fun domHtml(handle: Int): String = domStore.getHtml(handle)
    override fun domOuterHtml(handle: Int): String = domStore.getOuterHtml(handle)
    override fun domXml(handle: Int): String = domStore.getXml(handle)
    override fun domText(handle: Int): String = domStore.getText(handle)
    override fun domAttr(handle: Int, name: String): String? = domStore.getAttr(handle, name)
    override fun domSetAttr(handle: Int, name: String, value: String) = domStore.setAttr(handle, name, value)
    override fun domRemoveAttr(handle: Int, name: String) = domStore.removeAttr(handle, name)
    override fun domAttrs(handle: Int): String = buildJsonObject {
        domStore.getAllAttrs(handle).forEach { (k, v) -> put(k, v) }
    }.toString()
    override fun domHasClass(handle: Int, className: String): Boolean = domStore.hasClass(handle, className)
    override fun domData(handle: Int, key: String): String? = domStore.getData(handle, key)
    override fun domVal(handle: Int): String? = domStore.getVal(handle)
    override fun domTagName(handle: Int): String = domStore.getTagName(handle)
    override fun domIsTextNode(handle: Int): Boolean = domStore.isTextNode(handle)
    override fun domReplaceWith(handle: Int, html: String) = domStore.replaceWith(handle, html)
    override fun domBefore(handle: Int, html: String) = domStore.before(handle, html)
    override fun domAfter(handle: Int, html: String) = domStore.after(handle, html)
    override fun domAppend(handle: Int, html: String) = domStore.append(handle, html)
    override fun domPrepend(handle: Int, html: String) = domStore.prepend(handle, html)
    override fun domEmpty(handle: Int) = domStore.empty(handle)
    override fun domRemove(handle: Int) = domStore.remove(handle)
    override fun domAddClass(handle: Int, className: String) = domStore.addClass(handle, className)
    override fun domRemoveClass(handle: Int, className: String) = domStore.removeClass(handle, className)
    override fun domRelease(handle: Int) = domStore.release(handle)
    override fun domReleaseAll() = domStore.releaseAll()

    override fun consoleLog(message: String) { println(message) }
    override fun consoleError(message: String) { System.err.println(message) }
    override fun consoleWarn(message: String) { System.err.println(message) }

    companion object {
        private val pluginStorage = ConcurrentHashMap<String, String>()
        private val localStore = ConcurrentHashMap<String, String>()
        private val sessionStore = ConcurrentHashMap<String, String>()
    }
}
