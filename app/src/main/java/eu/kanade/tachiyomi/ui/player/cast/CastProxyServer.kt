package eu.kanade.tachiyomi.ui.player.cast

import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.net.Uri
import androidx.core.net.toUri
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class CastProxyServer(
    baseClient: OkHttpClient,
    private val contentResolver: ContentResolver,
    private val ipAddress: String,
    private val port: Int,
    private val json: Json,
    private val sessionToken: String,
    private val allowedLocalMedia: ConcurrentHashMap<String, String>,
) : NanoHTTPD(ipAddress.takeIf { it.isNotBlank() && it != "0.0.0.0" }, port) {

    private val client = baseClient.newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun serve(session: IHTTPSession?): Response? {
        val uri = session?.uri ?: return super.serve(session)
        if (session.method == Method.OPTIONS) {
            val response = newFixedLengthResponse(Response.Status.OK, "text/plain", null, 0)
            addCorsHeaders(response)
            return response
        }

        val requestToken = session.parameters["token"]?.firstOrNull()
        if (sessionToken.isBlank() || requestToken != sessionToken) {
            return forbidden("Invalid cast session token")
        }

        return when {
            uri.startsWith("/local") || uri.startsWith("/file") -> localServe(session)
            uri.startsWith("/proxy") -> proxyServe(session)
            else -> notFound("Unknown route")
        }
    }

    private class ClosableAfdStream(
        private val afd: AssetFileDescriptor?,
        private val stream: InputStream,
    ) : InputStream() {
        override fun read(): Int = stream.read()
        override fun read(b: ByteArray): Int = stream.read(b)
        override fun read(b: ByteArray, off: Int, len: Int): Int = stream.read(b, off, len)
        override fun skip(n: Long): Long = stream.skip(n)
        override fun available(): Int = stream.available()

        override fun close() {
            try {
                stream.close()
            } finally {
                try {
                    afd?.close()
                } catch (_: Exception) {}
            }
        }
    }

    private fun parseByteRange(rangeHeader: String, fileLength: Long): Pair<Long, Long>? {
        val spec = rangeHeader.removePrefix("bytes=").trim()
        val parts = spec.split("-", limit = 2)
        if (parts.size != 2 || fileLength <= 0L) return null
        val startStr = parts[0].trim()
        val endStr = parts[1].trim()
        val (start, end) = if (startStr.isEmpty()) {
            val suffixLen = endStr.toLongOrNull()?.takeIf { it > 0L } ?: return null
            (fileLength - suffixLen).coerceAtLeast(0L) to (fileLength - 1L)
        } else {
            val s = startStr.toLongOrNull()?.takeIf { it >= 0L && it < fileLength } ?: return null
            val e = endStr.takeIf { it.isNotEmpty() }?.toLongOrNull()?.coerceAtMost(fileLength - 1L) ?: (fileLength - 1L)
            if (e < s) return null
            s to e
        }
        return start to end
    }

    private fun isForbiddenInternalPath(file: File): Boolean {
        val canonical = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        return canonical.contains("/databases/") ||
            canonical.contains("/shared_prefs/") ||
            canonical.contains("/files/exts/") ||
            canonical.contains("/files/tdlib")
    }

    private fun localServe(session: IHTTPSession): Response {
        val mediaId = session.parameters["id"]?.firstOrNull()
        val rawUrl = if (mediaId != null) {
            allowedLocalMedia[mediaId] ?: return forbidden("Media not registered")
        } else {
            val candidate = session.parameters["url"]?.firstOrNull()
                ?: session.parameters["uri"]?.firstOrNull()
                ?: return badRequest("No media id")
            if (!allowedLocalMedia.containsValue(candidate)) {
                return forbidden("Unregistered local path")
            }
            candidate
        }
        val cleanUrl = if (rawUrl.startsWith("file://")) rawUrl.removePrefix("file://") else rawUrl

        val directFile = when {
            File(cleanUrl).exists() && File(cleanUrl).canRead() -> File(cleanUrl)
            rawUrl.contains("primary%3A") || rawUrl.contains("primary:") -> {
                val decoded = Uri.decode(rawUrl)
                val relPath = decoded.substringAfterLast("primary:").substringBefore("?")
                File("/storage/emulated/0/$relPath").takeIf { it.exists() && it.canRead() }
            }
            else -> null
        }

        if (directFile != null && directFile.exists() && directFile.canRead()) {
            if (isForbiddenInternalPath(directFile)) {
                return forbidden("Access denied")
            }
            return serveDirectFile(directFile, session)
        }

        val uri = try {
            rawUrl.toUri()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to parse uri: $rawUrl" }
            return badRequest("Invalid uri")
        }

        val afd = try {
            contentResolver.openAssetFileDescriptor(uri, "r")
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to open asset file descriptor for $uri, trying direct file stream" }
            null
        }

        val contentResolverLength = try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }

        val rawMime = contentResolver.getType(uri) ?: guessMimeFromUri(uri, rawUrl)
        val mime = when (rawMime) {
            "video/x-matroska", "application/octet-stream" -> "video/mp4"
            else -> rawMime
        }
        val fileLength = (afd?.length?.takeIf { it > 0 } ?: afd?.parcelFileDescriptor?.statSize?.takeIf { it > 0 } ?: contentResolverLength) ?: -1L
        val rangeHeader = session.headers["range"]

        if (session.method == Method.HEAD) {
            val response = newFixedLengthResponse(Response.Status.OK, mime, null, 0)
            if (fileLength >= 0) response.addHeader("Content-Length", fileLength.toString())
            response.addHeader("Accept-Ranges", "bytes")
            addCorsHeaders(response)
            try { afd?.close() } catch (_: Exception) {}
            return response
        }

        val inputStream: InputStream = try {
            afd?.createInputStream() ?: contentResolver.openInputStream(uri) ?: run {
                try { afd?.close() } catch (_: Exception) {}
                return notFound("File not found")
            }
        } catch (e: Exception) {
            try { afd?.close() } catch (_: Exception) {}
            logcat(LogPriority.ERROR, e) { "Failed to open input stream: $rawUrl" }
            return notFound("File not found: ${e.message}")
        }

        val afdStream = ClosableAfdStream(afd, inputStream)

        if (rangeHeader != null && fileLength > 0) {
            val parsedRange = parseByteRange(rangeHeader, fileLength)
            if (parsedRange != null) {
                try {
                    val (start, end) = parsedRange
                    val length = (end - start + 1).coerceAtLeast(0L)

                    if (inputStream is FileInputStream) {
                        inputStream.channel.position(start)
                    } else {
                        var skipped = 0L
                        while (skipped < start) {
                            val s = afdStream.skip(start - skipped)
                            if (s <= 0) break
                            skipped += s
                        }
                    }

                    val response = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, afdStream, length)
                    response.addHeader("Content-Range", "bytes $start-$end/$fileLength")
                    response.addHeader("Content-Length", length.toString())
                    response.addHeader("Accept-Ranges", "bytes")
                    addCorsHeaders(response)
                    return response
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Error processing Range header" }
                }
            }
        }

        val response = if (fileLength >= 0) {
            newFixedLengthResponse(Response.Status.OK, mime, afdStream, fileLength)
        } else {
            newChunkedResponse(Response.Status.OK, mime, afdStream)
        }
        if (fileLength >= 0) response.addHeader("Content-Length", fileLength.toString())
        response.addHeader("Accept-Ranges", "bytes")
        addCorsHeaders(response)
        return response
    }

    private fun serveDirectFile(file: File, session: IHTTPSession): Response {
        val fileLength = file.length()
        val mime = guessMimeFromUri(file.toURI().toString().toUri(), file.name)
        val rangeHeader = session.headers["range"]

        if (session.method == Method.HEAD) {
            val response = newFixedLengthResponse(Response.Status.OK, mime, null, 0)
            response.addHeader("Content-Length", fileLength.toString())
            response.addHeader("Accept-Ranges", "bytes")
            addCorsHeaders(response)
            return response
        }

        if (rangeHeader != null && fileLength > 0) {
            val parsedRange = parseByteRange(rangeHeader, fileLength)
            if (parsedRange != null) {
                var inputStream: FileInputStream? = null
                try {
                    val (start, end) = parsedRange
                    val length = (end - start + 1).coerceAtLeast(0L)

                    inputStream = FileInputStream(file)
                    inputStream.channel.position(start)

                    val response = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, inputStream, length)
                    response.addHeader("Content-Range", "bytes $start-$end/$fileLength")
                    response.addHeader("Content-Length", length.toString())
                    response.addHeader("Accept-Ranges", "bytes")
                    addCorsHeaders(response)
                    return response
                } catch (e: Exception) {
                    try { inputStream?.close() } catch (_: Exception) {}
                    logcat(LogPriority.ERROR, e) { "Error processing direct file Range header" }
                }
            }
        }

        val inputStream = FileInputStream(file)
        val response = newFixedLengthResponse(Response.Status.OK, mime, inputStream, fileLength)
        response.addHeader("Content-Length", fileLength.toString())
        response.addHeader("Accept-Ranges", "bytes")
        addCorsHeaders(response)
        return response
    }

    private fun guessMimeFromUri(uri: Uri, rawUrl: String? = null): String {
        val target = rawUrl ?: uri.toString()
        val cleanTarget = Uri.decode(target).substringBefore("?").substringBefore("#")
        val ext = cleanTarget.substringAfterLast(".", "").lowercase()
        return when (ext) {
            "avi" -> "video/x-msvideo"
            "flv" -> "video/x-flv"
            "mkv" -> "video/mp4" // Chromecast HTML5 player compatibility
            "mov" -> "video/quicktime"
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "wmv" -> "video/x-ms-wmv"
            "ts" -> "video/mp2t"
            "m3u8" -> "application/x-mpegURL"
            "mpd" -> "application/dash+xml"
            else -> "video/mp4"
        }
    }

    private fun proxyServe(session: IHTTPSession): Response {
        val url = session.parameters["url"]?.firstOrNull() ?: return badRequest("No url param")
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return badRequest("Only http/https URLs are supported")
        }
        val headers = session.parameters["header"]?.firstOrNull()
            ?.let {
                try {
                    json.decodeFromString<Map<String, String>>(it)
                } catch (_: Exception) {
                    emptyMap()
                }
            } ?: emptyMap()

        val request = try {
            val requestBuilder = Request.Builder().url(url)
            if (session.method == Method.HEAD) {
                requestBuilder.head()
            }
            headers.forEach { (name, value) -> requestBuilder.addHeader(name, value) }
            session.headers["range"]?.let { requestBuilder.addHeader("Range", it) }
            requestBuilder.build()
        } catch (e: IllegalArgumentException) {
            logcat(LogPriority.WARN, e) { "Invalid proxy URL or header: $url" }
            return badRequest("Invalid proxy URL or header")
        }

        val resp = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to fetch proxied URL: $url" }
            return notFound("Failed to fetch stream: ${e.message}")
        }

        val contentType = resp.header("Content-Type") ?: ""
        val status = Response.Status.lookup(resp.code) ?: Response.Status.OK
        val effectiveUrl = resp.request.url.toString()

        if (session.method == Method.HEAD) {
            val headLength = resp.header("Content-Length")?.toLongOrNull() ?: resp.body.contentLength()
            resp.close()
            return newFixedLengthResponse(status, contentType.ifEmpty { "application/octet-stream" }, null, 0).apply {
                if (headLength >= 0) addHeader("Content-Length", headLength.toString())
                resp.header("Accept-Ranges")?.let { addHeader("Accept-Ranges", it) }
                addCorsHeaders(this)
            }
        }

        val response = if (resp.isM3U8()) {
            val body = resp.use { it.body.string() }
            val proxied = proxyM3U8(body, effectiveUrl, headers)
            newFixedLengthResponse(status, "application/vnd.apple.mpegurl", proxied)
        } else if (resp.isDash()) {
            val body = resp.use { it.body.string() }
            val proxied = proxyDash(body, effectiveUrl, headers)
            newFixedLengthResponse(status, "application/dash+xml", proxied)
        } else {
            val body = resp.body
            val length = body.contentLength()
            val stream = body.byteStream()
            val mime = contentType.ifEmpty { "application/octet-stream" }
            val returnResp = if (length >= 0) {
                newFixedLengthResponse(status, mime, stream, length)
            } else {
                newChunkedResponse(status, mime, stream)
            }
            resp.header("Content-Range")?.let { returnResp.addHeader("Content-Range", it) }
            resp.header("Accept-Ranges")?.let { returnResp.addHeader("Accept-Ranges", it) }
            returnResp
        }

        return response.apply { addCorsHeaders(this) }
    }

    private fun okhttp3.Response.isM3U8(): Boolean {
        val contentType = header("Content-Type")?.substringBefore(';')?.trim()?.lowercase() ?: ""
        if (contentType.endsWith("vnd.apple.mpegurl") || contentType.endsWith("x-mpegurl")) {
            return true
        }

        val path = request.url.pathSegments.lastOrNull()?.lowercase()
        return path?.endsWith(".m3u8") == true || path?.endsWith(".m3u") == true
    }

    private fun proxyM3U8(playlist: String, baseUrl: String, headers: Map<String, String>): String {
        return buildString {
            playlist.lines().forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.isEmpty() -> appendLine(line)
                    HLS_TAGS.any { trimmed.startsWith(it) } -> appendLine(
                        proxyAttrUris(line, baseUrl, headers),
                    )
                    trimmed.startsWith('#') -> appendLine(line)
                    else -> {
                        val full = resolveUrl(baseUrl, trimmed)
                        appendLine(getProxyUrl(full, headers))
                    }
                }
            }
        }
    }

    private fun proxyAttrUris(line: String, baseUrl: String, headers: Map<String, String>): String {
        return ATTRIBUTE_REGEX.replace(line) { m ->
            val uri = m.groupValues[1]
            val full = resolveUrl(baseUrl, uri)
            val proxied = getProxyUrl(full, headers)
            "URI=\"$proxied\""
        }
    }

    private fun resolveUrl(base: String, relative: String): String {
        if (relative.startsWith("http://") || relative.startsWith("https://")) return relative
        if (relative.startsWith("//")) return "https:$relative"
        return try {
            URI(base).resolve(relative).toString()
        } catch (_: Exception) {
            relative
        }
    }

    private fun okhttp3.Response.isDash(): Boolean {
        val contentType = header("Content-Type")?.substringBefore(';')?.trim()?.lowercase() ?: ""
        if (contentType.endsWith("dash+xml")) {
            return true
        }

        val path = request.url.pathSegments.lastOrNull()?.lowercase()
        return path?.endsWith(".mpd") == true
    }

    private fun proxyDash(manifest: String, baseUrl: String, headers: Map<String, String>): String {
        val document = Jsoup.parse(manifest, baseUrl, Parser.xmlParser()).apply {
            outputSettings().prettyPrint(false).syntax(Document.OutputSettings.Syntax.xml)
        }

        DASH_ATTRS.forEach { attr ->
            document.select("[$attr]").forEach { element ->
                val value = element.attr(attr)
                val full = resolveUrl(baseUrl, value)
                element.attr(attr, getDashProxyUrl(full, headers))
            }
        }

        return document.outerHtml()
    }

    private fun getDashProxyUrl(targetUrl: String, headers: Map<String, String>): String {
        val tokens = mutableListOf<String>()
        val prefix = "DASH_TOKEN_PREFIX"

        val placeholderUrl = DASH_TOKEN_REGEX.replace(targetUrl) { m ->
            val placeholder = "$prefix${tokens.size}END"
            tokens.add(m.value)
            placeholder
        }

        var proxied = getProxyUrl(placeholderUrl, headers)
        tokens.forEachIndexed { index, token ->
            proxied = proxied.replace("$prefix${index}END", token)
        }
        return proxied
    }

    private fun addCorsHeaders(response: Response) {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, OPTIONS, HEAD")
        response.addHeader("Access-Control-Allow-Headers", "*")
        response.addHeader("Access-Control-Expose-Headers", "content-range, content-length, accept-ranges")
        response.addHeader("Access-Control-Max-Age", "86400")
    }

    private fun getProxyUrl(targetUrl: String, headers: Map<String, String>): String {
        return "http://$ipAddress:$port".toHttpUrl().newBuilder().apply {
            addPathSegment("proxy")
            addQueryParameter("token", sessionToken)
            addQueryParameter("url", targetUrl)
            addQueryParameter("header", json.encodeToString(headers))
        }.build().toString()
    }

    private fun forbidden(message: String): Response {
        return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", message)
    }

    private fun notFound(message: String): Response {
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", message)
    }

    private fun badRequest(message: String): Response {
        return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", message)
    }

    companion object {
        private val ATTRIBUTE_REGEX = Regex("""URI="([^"]+)"""")
        private val HLS_TAGS = listOf("#EXT-X-KEY", "#EXT-X-MAP", "#EXT-X-MEDIA", "#EXT-X-I-FRAME-STREAM-INF")
        private val DASH_ATTRS = listOf("initialization", "media", "index")
        private val DASH_TOKEN_REGEX = Regex("""\$[A-Za-z0-9]*(?:%0\d+d)?\$""")
    }
}
