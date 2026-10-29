package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ToonLivre : HttpSource(), ConfigurableSource {

    override val id: Long = 2834885536325274328L
    override val name = "ToonLivre"
    override val baseUrl = "https://toonlivre.net"
    override val lang = "pt-BR"
    override val supportsLatest = true

    companion object {
        const val PREFIX_PATH_SEARCH = "path:"
        private const val ALTERNATIVE_TITLE_PREF = "alternativeTitlePref"
        private const val CDN_HOST = "cdn.toonlivre.net"
        private const val PROXY_HOST = "slightly-free-mayfly.edgecompute.app"
        private val PAGE_NUMBER_REGEX = Regex("""[-_](\d+)\.[^.]+$""")
        private val pageListCache = Collections.synchronizedMap(LinkedHashMap<String, List<Page>>(20, 0.75f, true))
        private val parallelExecutor = Executors.newFixedThreadPool(8)
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedAppContext: Any? = null

    private val preferences: SharedPreferences?
        get() = runCatching {
            val ctx = cachedAppContext ?: Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null)
                ?.also { cachedAppContext = it }
                ?: return null
            ctx.javaClass
                .getMethod("getSharedPreferences", String::class.java, Int::class.javaPrimitiveType)
                .invoke(ctx, "source_$id", 0) as? SharedPreferences
        }.getOrNull()

    private val useAlternativeTitle: Boolean
        get() = preferences?.getBoolean(ALTERNATIVE_TITLE_PREF, false) ?: false

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("Accept", "application/json, text/plain, */*")
        .set("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)
        .set("Sec-Fetch-Dest", "empty")
        .set("Sec-Fetch-Mode", "cors")
        .set("Sec-Fetch-Site", "same-origin")

    // ============================== Popular Manga ==============================
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/api/mangas/search?page=$page&limit=24&sortBy=popular&sortOrder=desc", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangasJson(response)

    // ============================== Latest Updates =============================
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/api/mangas/search?page=$page&limit=24&sortBy=updated&sortOrder=desc", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangasJson(response)

    // ============================== Search Manga ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val trimmed = query.trim()
        if (trimmed.startsWith(PREFIX_PATH_SEARCH)) {
            val path = trimmed.removePrefix(PREFIX_PATH_SEARCH).removePrefix("/")
            return GET("$baseUrl/api/manga-by-slug/$path", headers)
        }

        val urlBuilder = StringBuilder("$baseUrl/api/mangas/search?page=$page&limit=24")
        if (trimmed.isNotEmpty()) {
            urlBuilder.append("&q=").append(URLEncoder.encode(trimmed, "UTF-8"))
        }

        var sortBy = "popular"
        var sortOrder = "desc"

        filters.forEach { filter ->
            when (filter) {
                is OrderByFilter -> sortBy = filter.toSlug()
                is OrderDirectionFilter -> sortOrder = filter.toSlug()
                else -> {}
            }
        }

        urlBuilder.append("&sortBy=").append(sortBy).append("&sortOrder=").append(sortOrder)
        return GET(urlBuilder.toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val bodyStr = response.body?.string().orEmpty()
        if (bodyStr.startsWith("{") && !bodyStr.contains("\"mangas\":")) {
            // Direct single manga JSON from slug lookup
            return runCatching {
                val json = JSONObject(bodyStr)
                val manga = parseSingleMangaJson(json)
                MangasPage(listOf(manga), false)
            }.getOrDefault(MangasPage(emptyList(), false))
        }
        return parseMangasJsonFromString(bodyStr)
    }

    private fun parseMangasJson(response: Response): MangasPage =
        parseMangasJsonFromString(response.body?.string().orEmpty())

    private fun parseMangasJsonFromString(bodyStr: String): MangasPage {
        if (bodyStr.isBlank() || !bodyStr.startsWith("{")) {
            return MangasPage(emptyList(), false)
        }
        val json = JSONObject(bodyStr)
        val mangasArray = json.optJSONArray("mangas") ?: JSONArray()
        val mangas = ArrayList<SManga>(mangasArray.length())
        for (i in 0 until mangasArray.length()) {
            val item = mangasArray.getJSONObject(i)
            val manga = parseSingleMangaJson(item)
            mangas.add(manga)
        }
        val hasNextPage = json.optJSONObject("pagination")?.optBoolean("hasNextPage", false) ?: false
        return MangasPage(mangas, hasNextPage)
    }

    private fun parseSingleMangaJson(json: JSONObject): SManga = SManga.create().apply {
        val titleStr = json.optString("title")
        val altTitle = json.optString("alternativeTitle")
        title = if (useAlternativeTitle && altTitle.isNotBlank()) altTitle else titleStr
        thumbnail_url = json.optString("coverUrl").takeIf { it.isNotBlank() }
        url = json.optString("id")
    }

    // ============================== Manga Details ==============================
    override fun mangaDetailsRequest(manga: SManga): Request =
        GET("$baseUrl/api/manga-by-slug/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val bodyStr = response.body?.string().orEmpty()
        val json = JSONObject(bodyStr)
        return SManga.create().apply {
            val titleStr = json.optString("title")
            val altTitle = json.optString("alternativeTitle")
            title = if (useAlternativeTitle && altTitle.isNotBlank()) altTitle else titleStr
            thumbnail_url = json.optString("coverUrl").takeIf { it.isNotBlank() }

            val desc = json.optString("description")
            description = listOfNotNull(
                desc.takeIf { it.isNotBlank() },
                altTitle.takeIf { it.isNotBlank() }?.let { "Nome alternativo: $it" },
            ).joinToString("\n\n")

            author = json.optJSONArray("authors")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }.joinToString(", ")
            }
            artist = json.optJSONArray("artists")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }.joinToString(", ")
            }
            genre = json.optJSONArray("genres")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }.joinToString(", ")
            }

            status = when (json.optString("status").lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            url = json.optString("id", manga.url)
        }
    }

    // ============================== Chapter List ===============================
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val bodyStr = response.body?.string().orEmpty()
        val json = JSONObject(bodyStr)
        val mangaId = json.optString("id")
        val titleStr = json.optString("title")
        val slug = getSlug(titleStr)
        val chaptersArray = json.optJSONArray("chapters") ?: return emptyList()
        val list = ArrayList<SChapter>(chaptersArray.length())
        for (i in 0 until chaptersArray.length()) {
            val ch = chaptersArray.getJSONObject(i)
            val chId = ch.optString("id")
            val chNum = ch.optString("number")
            val chTime = ch.optLong("timestamp", 0L)
            val pageCount = ch.optInt("pageCount", -1)
            list.add(
                SChapter.create().apply {
                    name = "Capítulo $chNum"
                    chapter_number = chNum.toFloatOrNull() ?: -1f
                    date_upload = chTime
                    url = "/$slug/$chNum?mangaId=$mangaId&chapterId=$chId&pageCount=$pageCount"
                },
            )
        }
        return list.sortedByDescending { it.chapter_number }
    }

    // ============================== Page List (ULTRA FAST) ====================
    override fun pageListRequest(chapter: SChapter): Request =
        GET("$baseUrl${chapter.url.substringBefore('?')}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val originalReqUrl = response.request.url
        val rawUrl = originalReqUrl.toString()
        val httpUrl = rawUrl.toHttpUrlOrNull() ?: return emptyList()

        val mangaId = httpUrl.queryParameter("mangaId").orEmpty()
        val chapterId = httpUrl.queryParameter("chapterId").orEmpty()
        val expectedCount = httpUrl.queryParameter("pageCount")?.toIntOrNull()?.takeIf { it > 0 }
        val chapterNumber = httpUrl.pathSegments.lastOrNull { it.isNotEmpty() && !it.contains("?") }.orEmpty()

        val cacheKey = "$mangaId-$chapterNumber"
        pageListCache[cacheKey]?.takeIf { it.isNotEmpty() }?.let { return it }

        // 1. FAST Direct API access (Bypasses WebView if session/cookies exist)
        if (mangaId.isNotEmpty() && chapterId.isNotEmpty()) {
            val directPages = tryDirectApiAccess(mangaId, chapterId, chapterNumber, expectedCount)
            if (!directPages.isNullOrEmpty()) {
                pageListCache[cacheKey] = directPages
                return directPages
            }
        }

        // 2. High-performance Headless WebView with Parallel Fetching & Adblocking
        val readerUrl = "$baseUrl/${httpUrl.pathSegments.firstOrNull().orEmpty()}/$chapterNumber"
        val webViewPages = getPagesWithFastWebView(readerUrl, mangaId, chapterNumber, expectedCount)
        if (webViewPages.isNotEmpty()) {
            pageListCache[cacheKey] = webViewPages
        }
        return webViewPages
    }

    private fun tryDirectApiAccess(
        mangaId: String,
        chapterId: String,
        chapterNumber: String,
        expectedCount: Int?,
    ): List<Page>? = runCatching {
        val cookieStr = CookieManager.getInstance().getCookie(baseUrl).orEmpty()
        val csrf = cookieStr.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("csrf_token=") }
            ?.substringAfter("csrf_token=")

        val reqHeaders = headers.newBuilder()
            .set("Accept", "application/json, text/plain, */*")
            .set("Content-Type", "application/json")
            .set("Origin", baseUrl)
            .set("Referer", "$baseUrl/")
            .apply {
                if (cookieStr.isNotBlank()) set("Cookie", cookieStr)
                if (!csrf.isNullOrEmpty()) set("x-csrf-token", csrf)
            }
            .build()

        val body = JSONObject().apply {
            put("mangaId", mangaId)
            put("chapterId", chapterId)
        }.toString().toRequestBody("application/json".toMediaType())

        val postReq = POST("$baseUrl/api/reader/chapter/access", reqHeaders, body)
        client.newCall(postReq).execute().use { res ->
            if (!res.isSuccessful) return null
            val resStr = res.body?.string().orEmpty()
            if (!resStr.startsWith("{")) return null
            val json = JSONObject(resStr)

            // Direct pages array
            val pagesArr = json.optJSONObject("chapter")?.optJSONArray("pages")
            if (pagesArr != null && pagesArr.length() > 0) {
                val urls = (0 until pagesArr.length()).map { pagesArr.getString(it) }
                return urls.toPageList(mangaId, chapterNumber)
            }

            // Grant token with pageCount -> Fast parallel fetching via OkHttp!
            val grantObj = json.optJSONObject("grant")
            val grantToken = grantObj?.optString("token")
            val pageCount = json.optJSONObject("chapter")?.optInt("pageCount") ?: expectedCount ?: -1
            if (!grantToken.isNullOrEmpty() && pageCount > 0) {
                val tasks = (1..pageCount).map { p ->
                    Callable {
                        val pageReq = GET(
                            "$baseUrl/api/reader/chapter/$mangaId/$chapterId/page/$p",
                            headers.newBuilder().set("x-tl-reader-grant", grantToken).build(),
                        )
                        runCatching {
                            client.newCall(pageReq).execute().use { pRes ->
                                if (pRes.isSuccessful) {
                                    val pJson = JSONObject(pRes.body?.string().orEmpty())
                                    pJson.optString("imageUrl").takeIf { it.isNotBlank() }
                                } else null
                            }
                        }.getOrNull()
                    }
                }
                val futures = parallelExecutor.invokeAll(tasks, 5, TimeUnit.SECONDS)
                val imageUrls = futures.mapNotNull { runCatching { it.get() }.getOrNull() }
                if (imageUrls.isNotEmpty()) {
                    return imageUrls.toPageList(mangaId, chapterNumber)
                }
            }
        }
        null
    }.getOrNull()

    @SuppressLint("SetJavaScriptEnabled")
    private fun getPagesWithFastWebView(
        readerUrl: String,
        mangaId: String,
        chapterNumber: String,
        expectedCount: Int?,
    ): List<Page> {
        val result = CompletableDeferred<List<String>>()
        val bridgeName = "bridge_" + UUID.randomUUID().toString().replace("-", "")
        val mainHandler = Handler(Looper.getMainLooper())
        val collectedUrls = Collections.synchronizedSet(LinkedHashSet<String>())
        var webViewRef: WebView? = null

        val deliverRunnable = Runnable {
            if (!result.isCompleted && collectedUrls.isNotEmpty()) {
                result.complete(ArrayList(collectedUrls))
            }
        }

        mainHandler.post {
            try {
                val context = cachedAppContext as? Context
                    ?: (Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication")
                        .invoke(null) as? Context)
                    ?: run {
                        result.completeExceptionally(Exception("Contexto Android indisponível"))
                        return@post
                    }

                val wv = WebView(context)
                webViewRef = wv

                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    blockNetworkImage = true // Accelerate: no rendering images inside headless WebView!
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }

                wv.addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun post(msg: String) {
                            try {
                                val json = JSONObject(msg)
                                val type = json.optString("type")
                                if (type == "PAGES") {
                                    val arr = json.optJSONArray("pages")
                                    if (arr != null && arr.length() > 0) {
                                        val list = (0 until arr.length()).map { arr.getString(it) }
                                        mainHandler.post {
                                            runCatching { CookieManager.getInstance().flush() }
                                            if (!result.isCompleted) {
                                                result.complete(list)
                                            }
                                        }
                                        return
                                    }
                                }
                            } catch (_: Exception) {
                                runCatching {
                                    val arr = JSONArray(msg)
                                    for (i in 0 until arr.length()) {
                                        val u = arr.getString(i)
                                        val cdn = toValidImageUrl(u)
                                        if (cdn != null && isChapterImage(cdn, mangaId, chapterNumber)) {
                                            if (collectedUrls.add(cdn)) {
                                                mainHandler.removeCallbacks(deliverRunnable)
                                                if (expectedCount != null && collectedUrls.size >= expectedCount) {
                                                    mainHandler.post(deliverRunnable)
                                                } else {
                                                    mainHandler.postDelayed(deliverRunnable, 250L) // Ultra fast 250ms settle
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    bridgeName,
                )

                wv.webChromeClient = WebChromeClient()
                wv.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val urlStr = request.url.toString()
                        val host = request.url.host.orEmpty()

                        // Block all ads, analytics, video and fonts to drastically speed up loading
                        if (urlStr.endsWith(".woff") || urlStr.endsWith(".woff2") || urlStr.endsWith(".ttf") ||
                            urlStr.endsWith(".mp4") || urlStr.endsWith(".m3u8") ||
                            urlStr.contains("googletagmanager") || urlStr.contains("cloudflareinsights") ||
                            urlStr.contains("whos.amung.us") || urlStr.contains("ptag.js") ||
                            urlStr.contains("pubadx") || urlStr.contains("monetag") ||
                            urlStr.contains("al5sm") || urlStr.contains("5gvci") ||
                            urlStr.contains("popads") || urlStr.contains("adsterra") ||
                            urlStr.contains("doubleclick") || urlStr.contains("google-analytics")
                        ) {
                            return WebResourceResponse("text/plain", "UTF-8", 200, "OK", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                        }

                        // Intercept image candidates directly from network requests
                        val cdnUrl = toValidImageUrl(urlStr)
                        if (cdnUrl != null && isChapterImage(cdnUrl, mangaId, chapterNumber)) {
                            if (collectedUrls.add(cdnUrl)) {
                                mainHandler.removeCallbacks(deliverRunnable)
                                if (expectedCount != null && collectedUrls.size >= expectedCount) {
                                    mainHandler.post(deliverRunnable)
                                } else {
                                    mainHandler.postDelayed(deliverRunnable, 250L)
                                }
                            }
                        }
                        return null
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        val parallelHookScript = buildFastParallelHookScript(bridgeName)
                        view.evaluateJavascript(parallelHookScript, null)
                    }
                }

                wv.loadUrl(readerUrl)
            } catch (e: Exception) {
                result.completeExceptionally(e)
            }
        }

        val pages = runBlocking {
            withTimeoutOrNull(15000L) {
                result.await()
            }
        } ?: ArrayList(collectedUrls)

        mainHandler.post {
            try {
                webViewRef?.stopLoading()
                webViewRef?.destroy()
                webViewRef = null
            } catch (_: Exception) {}
        }

        return pages.toPageList(mangaId, chapterNumber)
    }

    private fun buildFastParallelHookScript(bridgeName: String): String =
        """
        (() => {
            if (window.__tl_hooked) return;
            window.__tl_hooked = true;

            const postMessage = (msg) => {
                try {
                    if (window['$bridgeName'] && window['$bridgeName'].post) {
                        window['$bridgeName'].post(JSON.stringify(msg));
                    }
                } catch(e) {}
            };

            const origFetch = window.fetch;
            if (origFetch) {
                window.fetch = async function(...args) {
                    const u = typeof args[0] === 'string' ? args[0] : (args[0] && args[0].url);
                    const urlStr = String(u || '');
                    const isAccess = urlStr.indexOf('/api/reader/chapter/access') !== -1;
                    const res = await origFetch.apply(this, args);

                    if (isAccess) {
                        try {
                            const clone = res.clone();
                            clone.json().then(async data => {
                                if (!data) return;

                                const directPages = data.chapter && data.chapter.pages;
                                if (Array.isArray(directPages) && directPages.length > 0) {
                                    postMessage({ type: 'PAGES', pages: directPages });
                                    return;
                                }

                                // PARALLEL PROMISE.ALL FETCH (Super Fast!)
                                if (data.grant && data.grant.token && data.chapter && data.chapter.pageCount) {
                                    const mId = data.chapter.mangaId || '';
                                    const cId = data.chapter.id || '';
                                    const count = data.chapter.pageCount;
                                    const grantToken = data.grant.token;
                                    
                                    const promises = [];
                                    for (let p = 1; p <= count; p++) {
                                        promises.push(
                                            origFetch('/api/reader/chapter/' + encodeURIComponent(mId) + '/' + encodeURIComponent(cId) + '/page/' + p, {
                                                headers: { 'x-tl-reader-grant': grantToken }
                                            }).then(r => r.ok ? r.json() : null).catch(() => null)
                                        );
                                    }
                                    const results = await Promise.all(promises);
                                    const urls = results.filter(d => d && d.imageUrl).map(d => d.imageUrl);
                                    if (urls.length > 0) {
                                        postMessage({ type: 'PAGES', pages: urls });
                                        return;
                                    }
                                }

                                // Linked token chain fallback
                                if (data.protectedPages && data.firstHandle && data.firstHandle.token) {
                                    let cur = data.firstHandle;
                                    const count = data.pageCount || (data.chapter && data.chapter.pageCount) || 200;
                                    const urls = [];
                                    while (cur && cur.token && urls.length < count) {
                                        const pRes = await origFetch('/api/reader/p/' + encodeURIComponent(cur.token), { cache: 'no-store' });
                                        if (!pRes.ok) break;
                                        const pData = await pRes.json();
                                        if (pData && pData.imageUrl) {
                                            urls.push(pData.imageUrl);
                                            cur = pData.nextHandle;
                                        } else break;
                                    }
                                    if (urls.length > 0) {
                                        postMessage({ type: 'PAGES', pages: urls });
                                        return;
                                    }
                                }
                            }).catch(() => {});
                        } catch(e) {}
                    }
                    return res;
                };
            }

            // Quick DOM scan fallback
            setInterval(() => {
                const urls = new Set();
                document.querySelectorAll('img').forEach((img) => {
                    [img.currentSrc, img.src, img.dataset.src].forEach((u) => { if (u) urls.add(u); });
                });
                if (window.performance && performance.getEntriesByType) {
                    performance.getEntriesByType('resource').forEach((e) => urls.add(e.name));
                }
                if (urls.size > 0) {
                    try { window['$bridgeName'].post(JSON.stringify(Array.from(urls))); } catch(e) {}
                }
            }, 200);
        })();
        """.trimIndent()

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val builder = headers.newBuilder()
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .set("Sec-Fetch-Dest", "image")
            .set("Sec-Fetch-Mode", "no-cors")

        val imgUrl = page.imageUrl ?: page.url
        val httpUrl = imgUrl.toHttpUrlOrNull()
        if (httpUrl != null && httpUrl.host.endsWith("edgecompute.app")) {
            builder.removeAll("Referer")
        } else {
            builder.set("Referer", "$baseUrl/")
        }
        return GET(imgUrl, builder.build())
    }

    override fun getFilterList(): FilterList = FilterList(
        OrderByFilter(),
        OrderDirectionFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = ALTERNATIVE_TITLE_PREF
            title = "Titulo alternativo"
            summary = "Use titulos alternativos como principal quando disponivel."
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    // ============================== Helper Functions ==========================
    private fun getSlug(title: String): String {
        val noDiacritics = Normalizer.normalize(title, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return noDiacritics.lowercase(Locale.ROOT)
            .replace("[^a-z0-9]+".toRegex(), "-")
            .trim('-')
    }

    private fun toValidImageUrl(rawUrl: String): String? {
        val url = rawUrl.toHttpUrlOrNull() ?: return null
        if (!url.isHttps) return null
        val host = url.host
        if (host == CDN_HOST || host.endsWith("edgecompute.app")) return rawUrl
        val queryUrl = url.queryParameter("url")?.toHttpUrlOrNull()
        if (queryUrl != null && queryUrl.isHttps && (queryUrl.host == CDN_HOST || queryUrl.host.endsWith("edgecompute.app"))) {
            return queryUrl.toString()
        }
        return null
    }

    private fun isChapterImage(imageUrl: String, mangaId: String, chapterNumber: String): Boolean {
        val url = imageUrl.toHttpUrlOrNull() ?: return false
        val host = url.host
        if (host.endsWith("edgecompute.app")) {
            return url.pathSegments.size >= 2 && url.pathSegments[0] == "reader" && url.pathSegments[1] == "image"
        }
        val pathSegments = url.pathSegments
        return pathSegments.size >= 4 &&
            pathSegments[0] == "obras" &&
            (mangaId.isEmpty() || pathSegments[1] == mangaId) &&
            pathSegments[2] == chapterNumber &&
            pathSegments[3].isNotEmpty()
    }

    private fun Collection<String>.toPageList(mangaId: String, chapterNumber: String): List<Page> = synchronized(this) {
        val validUrls = asSequence()
            .mapNotNull { toValidImageUrl(it) }
            .filter { isChapterImage(it, mangaId, chapterNumber) }
            .distinct()
            .toList()

        val hasNumericPages = validUrls.any { pageNumber(it) != null }
        val sortedUrls = if (hasNumericPages) {
            validUrls.sortedWith(
                compareBy<String>({ pageNumber(it) ?: Int.MAX_VALUE }, { it }),
            )
        } else {
            validUrls
        }
        sortedUrls.mapIndexed { index, imageUrl -> Page(index, url = imageUrl, imageUrl = imageUrl) }
    }

    private fun pageNumber(urlStr: String): Int? = urlStr.toHttpUrlOrNull()?.pathSegments?.lastOrNull()
        ?.let(PAGE_NUMBER_REGEX::find)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()
}
