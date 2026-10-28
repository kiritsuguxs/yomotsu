package eu.kanade.tachiyomi.animeextension.pt.animeshentai.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class BloggerExtractor(private val client: OkHttpClient) {

    fun videosFromUrl(url: String, headers: Headers, qualityPrefix: String = ""): List<Video> {
        return try {
            val request = Request.Builder().url(url).headers(headers).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val streamVideos = getStreamVideos(body, headers, qualityPrefix)
            if (streamVideos.isNotEmpty()) {
                streamVideos
            } else {
                getRpcVideos(url, body, headers, qualityPrefix)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getStreamVideos(body: String, headers: Headers, qualityPrefix: String): List<Video> {
        if (body.contains("errorContainer")) return emptyList()

        return try {
            body.substringAfter("\"streams\":[", "")
                .substringBefore("]")
                .split("},")
                .mapNotNull { chunk ->
                    val videoUrl = chunk.substringAfter("\"play_url\":\"", "")
                        .substringBefore("\"")
                        .takeIf(String::isNotBlank) ?: return@mapNotNull null

                    val format = chunk.substringAfter("\"format_id\":", "").substringBefore("}")
                    val quality = qualityFromFormat(format.trim())
                    val label = if (qualityPrefix.isNotBlank()) "$qualityPrefix - $quality" else "Blogger - $quality"
                    Video(videoUrl, label, videoUrl, headers)
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getRpcVideos(
        url: String,
        body: String,
        headers: Headers,
        qualityPrefix: String,
    ): List<Video> {
        return try {
            val token = url.toHttpUrl().queryParameter("token")?.takeIf(String::isNotBlank) ?: return emptyList()
            val formSessionId = body.substringAfter("FdrFJe\":\"", "").substringBefore("\"")
            val blogId = body.substringAfter("cfb2h\":\"", "").substringBefore("\"")
            val requestId = ((System.currentTimeMillis() / 1000L) % 86400L).toString()

            val rpcUrl = BLOGGER_BASE.toHttpUrl().newBuilder()
                .addPathSegments("_/BloggerVideoPlayerUi/data/batchexecute")
                .addQueryParameter("rpcids", "WcwnYd")
                .addQueryParameter("source-path", "/video.g")
                .addQueryParameter("f.sid", formSessionId)
                .addQueryParameter("bl", blogId)
                .addQueryParameter("hl", "en-US")
                .addQueryParameter("_reqid", requestId)
                .addQueryParameter("rt", "c")
                .build()
                .toString()

            val rpcBody =
                "f.req=%5B%5B%5B%22WcwnYd%22%2C%22%5B%5C%22$token%5C%22%2C%5C%22%5C%22%2C0%5D%22%2Cnull%2C%22generic%22%5D%5D%5D&".toRequestBody()

            val rpcHeaders = Headers.headersOf(
                "accept", "*/*",
                "accept-language", "en-US,en;q=0.9",
                "content-type", "application/x-www-form-urlencoded;charset=UTF-8",
                "sec-fetch-dest", "empty",
                "sec-fetch-mode", "cors",
                "sec-fetch-site", "same-origin",
                "User-Agent", headers["User-Agent"] ?: "Mozilla/5.0",
                "x-same-domain", "1",
                "Referer", BLOGGER_BASE,
            )

            val rpcRequest = Request.Builder()
                .url(rpcUrl)
                .headers(rpcHeaders)
                .post(rpcBody)
                .build()

            val rpcResponse = client.newCall(rpcRequest).execute()
            val rpcString = rpcResponse.body?.string() ?: return emptyList()

            if (!rpcString.contains("https://")) return emptyList()

            rpcString
                .substringAfter("[[\\\"", "")
                .substringBefore("]]]")
                .let { "\\\"$it]" }
                .split("],[")
                .mapNotNull { chunk ->
                    val rawUrl = chunk.substringAfter("\\\"", "").substringBefore("\\\"")
                        .takeIf(String::isNotBlank) ?: return@mapNotNull null

                    val decodedUrl = decodeDoubleEscapedJson(rawUrl) ?: return@mapNotNull null
                    val format = chunk.substringAfter("[").substringBefore("]")
                    val quality = qualityFromFormat(format.trim())
                    val label = if (qualityPrefix.isNotBlank()) "$qualityPrefix - $quality" else "Blogger - $quality"
                    Video(decodedUrl, label, decodedUrl, headers)
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun decodeDoubleEscapedJson(value: String): String? = runCatching {
        val first = Json.parseToJsonElement("\"$value\"").jsonPrimitive.content
        Json.parseToJsonElement("\"$first\"").jsonPrimitive.content
    }.getOrNull()

    private fun qualityFromFormat(format: String): String = when (format) {
        "7" -> "240p"
        "18" -> "360p"
        "22" -> "720p"
        "37" -> "1080p"
        else -> "SD"
    }

    companion object {
        private const val BLOGGER_BASE = "https://www.blogger.com/"
    }
}
