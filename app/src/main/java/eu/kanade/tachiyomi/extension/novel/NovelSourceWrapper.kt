package eu.kanade.tachiyomi.extension.novel

import eu.kanade.tachiyomi.extension.novel.model.NovelPlugin
import eu.kanade.tachiyomi.extension.novel.runtime.NovelJsRuntime
import eu.kanade.tachiyomi.extension.novel.runtime.NovelNativeApi
import eu.kanade.tachiyomi.source.INovelSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import eu.kanade.tachiyomi.source.online.HttpSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

class NovelSourceWrapper(
    val plugin: NovelPlugin,
    val localPath: String,
) : HttpSource(), INovelSource {

    override val id: Long = generateId(plugin.id, plugin.lang)
    override val name: String = plugin.name
    override val lang: String = plugin.lang
    override val supportsLatest: Boolean = true
    override val baseUrl: String get() = plugin.site.ifBlank { "https://google.com" }

    override fun getHomeUrl(): String = plugin.site.ifBlank { baseUrl }

    override fun getMangaUrl(manga: SManga): String {
        if (manga.url.startsWith("http://") || manga.url.startsWith("https://")) {
            return manga.url
        }
        val cleanBase = baseUrl.trimEnd('/')
        val cleanPath = manga.url.trimStart('/')
        return "$cleanBase/$cleanPath"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        if (chapter.url.startsWith("http://") || chapter.url.startsWith("https://")) {
            return chapter.url
        }
        val cleanBase = baseUrl.trimEnd('/')
        val cleanPath = chapter.url.trimStart('/')
        return "$cleanBase/$cleanPath"
    }

    private fun escapeJsString(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")
    }

    private fun evaluateAsyncMethod(runtime: NovelJsRuntime, methodCall: String): String {
        val startScript = """
            var __asyncState = { result: null, error: null, done: false };
            try {
                var __promise = $methodCall;
                if (__promise && typeof __promise.then === 'function') {
                    __promise.then(function(res) {
                        __asyncState.result = res;
                        __asyncState.done = true;
                    }, function(err) {
                        var stack = (err && err.stack) ? String(err.stack) : "";
                        var msg = (err && err.message) ? String(err.message) : String(err);
                        __asyncState.error = stack ? (msg + "\n" + stack) : msg;
                        __asyncState.done = true;
                    });
                    var __drained = 0;
                    while (!__asyncState.done && typeof __hasJobs === 'function' && __hasJobs() && __drained < 50000) {
                        __drained += __drainJobs(500);
                    }
                } else {
                    __asyncState.result = __promise;
                    __asyncState.done = true;
                }
            } catch (e) {
                var stack = (e && e.stack) ? String(e.stack) : "";
                var msg = (e && e.message) ? String(e.message) : String(e);
                __asyncState.error = stack ? (msg + "\n" + stack) : msg;
                __asyncState.done = true;
            }
            JSON.stringify({
                done: __asyncState.done,
                drained: 0,
                result: __asyncState.done ? __asyncState.result : null,
                error: __asyncState.error
            });
        """.trimIndent()

        val stepScript = """
            (function() {
                var __drained = 0;
                if (!__asyncState.done && typeof __drainJobs === 'function') {
                    while (!__asyncState.done && typeof __hasJobs === 'function' && __hasJobs() && __drained < 50000) {
                        var r = __drainJobs(500);
                        if (r <= 0) break;
                        __drained += r;
                    }
                }
                return JSON.stringify({
                    done: __asyncState.done,
                    drained: __drained,
                    result: __asyncState.done ? __asyncState.result : null,
                    error: __asyncState.error
                });
            })();
        """.trimIndent()

        val jsonParser = Injekt.get<Json>()
        var rawJson = runtime.evaluate(startScript, "novel-evaluate.js", timeoutMs = JS_TIMEOUT_MS) as? String ?: "null"
        var idleTicks = 0
        var tick = 0

        while (tick < MAX_ASYNC_TICKS) {
            if (rawJson == "null" || rawJson.isBlank()) return "null"
            val stateObj = jsonParser.parseToJsonElement(rawJson).jsonObject
            val isDone = stateObj["done"]?.jsonPrimitive?.booleanOrNull == true
            if (isDone) {
                val errorMsg = stateObj.optCleanString("error")
                if (!errorMsg.isNullOrBlank()) {
                    android.util.Log.e("NovelSourceWrapper", "Novel JS execution error:\n$errorMsg")
                    throw Exception(errorMsg)
                }
                val resultElem = stateObj["result"]
                return if (resultElem == null || resultElem is JsonNull) "null" else resultElem.toString()
            }
            val drained = stateObj["drained"]?.jsonPrimitive?.intOrNull ?: 0
            if (tick > 0 && drained == 0) {
                idleTicks++
                if (idleTicks >= 3) break
            } else {
                idleTicks = 0
            }
            rawJson = runtime.evaluate(stepScript, "novel-evaluate-step.js", timeoutMs = JS_TIMEOUT_MS) as? String ?: "null"
            tick++
        }

        throw Exception("Promise timed out waiting for resolution")
    }

    private fun <T> runInJs(block: (NovelJsRuntime) -> T): T {
        val runtime = NovelJsRuntime(plugin.id, NovelNativeApi(pluginId = plugin.id))
        try {
            val jsCode = File(localPath).readText()
            val setupScript = """
                var module = { exports: {} };
                var exports = module.exports;
                function __safeFilters(p) {
                    var base = (p && p.filters) ? p.filters : {};
                    var res = {};
                    for (var k in base) {
                        if (Object.prototype.hasOwnProperty.call(base, k)) res[k] = base[k];
                    }
                    var common = ["genres", "genre", "genre[]", "categories", "category", "category[]", "type", "sort", "order", "status", "tags", "tag", "tag[]"];
                    for (var i = 0; i < common.length; i++) {
                        if (!res[common[i]]) res[common[i]] = { value: "", options: [] };
                    }
                    return res;
                }
            """.trimIndent()
            runtime.evaluate(setupScript + "\n" + jsCode, localPath, timeoutMs = JS_TIMEOUT_MS)
            return block(runtime)
        } finally {
            runtime.close()
        }
    }

    private fun isEndOfPageError(e: Exception): Boolean {
        val msg = e.message ?: return false
        return msg.contains("404") || msg.contains("not reach site", ignoreCase = true)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = runInJs { runtime ->
        val script = """
            (function() {
                var p = module.exports.default || module.exports;
                var filters = typeof __safeFilters === 'function' ? __safeFilters(p) : ((p && p.filters) ? p.filters : {});
                return p.popularNovels($page, { showLatestNovels: false, filters: filters });
            })()
        """.trimIndent()
        try {
            val jsonStr = evaluateAsyncMethod(runtime, script)
            parseNovelsJson(jsonStr)
        } catch (e: Exception) {
            if (page > 1 && isEndOfPageError(e)) {
                MangasPage(emptyList(), hasNextPage = false)
            } else {
                throw e
            }
        }
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = runInJs { runtime ->
        val script = """
            (function() {
                var p = module.exports.default || module.exports;
                var filters = typeof __safeFilters === 'function' ? __safeFilters(p) : ((p && p.filters) ? p.filters : {});
                return p.popularNovels($page, { showLatestNovels: true, filters: filters });
            })()
        """.trimIndent()
        try {
            val jsonStr = evaluateAsyncMethod(runtime, script)
            parseNovelsJson(jsonStr)
        } catch (e: Exception) {
            if (page > 1 && isEndOfPageError(e)) {
                MangasPage(emptyList(), hasNextPage = false)
            } else {
                throw e
            }
        }
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: eu.kanade.tachiyomi.source.model.FilterList): MangasPage = runInJs { runtime ->
        val escapedQuery = escapeJsString(query)
        val script = """
            (function() {
                var p = module.exports.default || module.exports;
                var safeFilters = typeof __safeFilters === 'function' ? __safeFilters(p) : ((p && p.filters) ? p.filters : {});
                return p.searchNovels('$escapedQuery', $page, { filters: safeFilters });
            })()
        """.trimIndent()
        try {
            val jsonStr = evaluateAsyncMethod(runtime, script)
            parseNovelsJson(jsonStr)
        } catch (e: Exception) {
            if (page > 1 && isEndOfPageError(e)) {
                MangasPage(emptyList(), hasNextPage = false)
            } else {
                throw e
            }
        }
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        var updatedManga = manga
        var updatedChapters = chapters

        if (fetchDetails || fetchChapters) {
            runInJs { runtime ->
                val escapedUrl = escapeJsString(manga.url)
                val script = """
                    (function() {
                        var p = module.exports.default || module.exports;
                        return p.parseNovel('$escapedUrl');
                    })()
                """.trimIndent()
                val jsonStr = evaluateAsyncMethod(runtime, script)
                val json = Injekt.get<Json>().parseToJsonElement(jsonStr).jsonObject

                if (fetchDetails) {
                    updatedManga = manga.apply {
                        title = json.optCleanString("name")?.takeIf { it.isNotBlank() } ?: title
                        thumbnail_url = json.optCleanString("cover")?.takeIf { it.isNotBlank() } ?: thumbnail_url
                        author = json.optCleanString("author") ?: author
                        artist = json.optCleanString("artist") ?: artist
                        description = json.optCleanString("summary") ?: description
                        genre = parseGenresField(json["genres"])
                        status = when (json.optCleanString("status")?.lowercase(Locale.ROOT)) {
                            "ongoing", "مستمرة", "publishing", "serializing" -> SManga.ONGOING
                            "completed", "منتهية", "finished" -> SManga.COMPLETED
                            "on hiatus", "hiatus" -> SManga.ON_HIATUS
                            "cancelled", "canceled", "dropped" -> SManga.CANCELLED
                            else -> SManga.UNKNOWN
                        }
                    }
                }

                if (fetchChapters) {
                    val chapList = (json["chapters"] as? JsonArray) ?: JsonArray(emptyList())
                    updatedChapters = chapList.mapNotNull { chapterObj ->
                        val obj = chapterObj as? JsonObject ?: return@mapNotNull null
                        val chapterPath = obj.optCleanString("path").orEmpty()
                        if (chapterPath.isBlank()) return@mapNotNull null
                        SChapter.create().apply {
                            name = obj.optCleanString("name")?.takeIf { it.isNotBlank() } ?: "Chapter"
                            url = chapterPath
                            date_upload = parseReleaseTime(obj["releaseTime"])
                            val chapNum = (obj["chapterNumber"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()
                            if (chapNum != null) {
                                chapter_number = chapNum
                            }
                        }
                    }
                }
            }
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        return emptyList()
    }

    suspend fun getChapterText(mangaUrl: String, chapterUrl: String): String = runInJs { runtime ->
        val escapedChapter = escapeJsString(chapterUrl)
        val escapedManga = escapeJsString(mangaUrl)
        val script = """
            (function() {
                var p = module.exports.default || module.exports;
                return p.parseChapter('$escapedChapter', '$escapedManga');
            })()
        """.trimIndent()
        val jsonStr = evaluateAsyncMethod(runtime, script)
        if (jsonStr == "null" || jsonStr.isBlank()) return@runInJs ""
        val json = Injekt.get<Json>().parseToJsonElement(jsonStr)
        (json as? JsonPrimitive)?.contentOrNull.orEmpty()
    }

    private fun parseNovelsJson(jsonStr: String): MangasPage {
        if (jsonStr == "null" || jsonStr.isEmpty()) return MangasPage(emptyList(), false)
        val json = Injekt.get<Json>().parseToJsonElement(jsonStr) as? JsonArray
            ?: return MangasPage(emptyList(), false)
        val mangas = json.mapNotNull {
            val obj = it as? JsonObject ?: return@mapNotNull null
            val path = obj.optCleanString("path").orEmpty()
            if (path.isBlank()) return@mapNotNull null
            SManga.create().apply {
                title = obj.optCleanString("name").orEmpty()
                url = path
                thumbnail_url = obj.optCleanString("cover").orEmpty()
            }
        }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    private fun JsonObject.optCleanString(key: String): String? {
        val elem = this[key] ?: return null
        if (elem is JsonNull) return null
        val primitive = elem as? JsonPrimitive ?: return null
        val content = primitive.contentOrNull ?: return null
        if (!primitive.isString && content == "null") return null
        return content
    }

    private fun parseGenresField(elem: JsonElement?): String? {
        return when (elem) {
            null, is JsonNull -> null
            is JsonPrimitive -> elem.contentOrNull
                ?.takeIf { it.isNotBlank() && (elem.isString || it != "null") }
            is JsonArray -> elem.mapNotNull { item ->
                if (item is JsonNull) null else (item as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            }.takeIf { it.isNotEmpty() }?.joinToString(", ")
            else -> null
        }
    }

    private fun parseReleaseTime(elem: JsonElement?): Long {
        if (elem == null || elem is JsonNull) return 0L
        val prim = elem as? JsonPrimitive ?: return 0L
        prim.longOrNull?.let { numeric ->
            if (numeric <= 0L) return 0L
            return if (numeric < 10_000_000_000L) numeric * 1000L else numeric
        }
        val str = prim.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return 0L
        runCatching { return Instant.parse(str).toEpochMilli() }
        runCatching { return LocalDateTime.parse(str).toInstant(ZoneOffset.UTC).toEpochMilli() }
        runCatching { return LocalDate.parse(str).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        for (pattern in RELEASE_DATE_PATTERNS) {
            runCatching {
                val parsed = SimpleDateFormat(pattern, Locale.US).parse(str)
                if (parsed != null) return parsed.time
            }
        }
        return 0L
    }

    private fun generateId(pluginId: String, lang: String): Long {
        val key = "novel:$pluginId:$lang"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xffL shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    companion object {
        private const val JS_TIMEOUT_MS = 30_000L
        private const val MAX_ASYNC_TICKS = 500
        private val RELEASE_DATE_PATTERNS = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd",
            "MMM dd, yyyy",
            "MMMM dd, yyyy",
            "dd MMM yyyy",
        )
    }
}
