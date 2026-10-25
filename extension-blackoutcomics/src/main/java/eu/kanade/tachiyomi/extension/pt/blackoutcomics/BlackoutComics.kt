package eu.kanade.tachiyomi.extension.pt.blackoutcomics

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.text.InputType
import android.util.Base64
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Locale

class BlackoutComics : HttpSource(), ConfigurableSource {

    override val name = "Blackout Comics"

    override val baseUrl = "https://blackoutcomics.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    private val baseHttpUrl by lazy { baseUrl.toHttpUrl() }

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

    @Volatile
    private var loginVerifiedInMemory = false

    @Volatile
    private var cachedCsrfToken: String? = null

    override val client: OkHttpClient by lazy {
        network.client.newBuilder()
            .addInterceptor(RateLimitInterceptor(permits = 1, periodMs = 1100L))
            .addInterceptor(::ageGateAndCookiesInterceptor)
            .build()
    }

    // Herda o User-Agent oficial do app (que sincroniza com o WebView para Cloudflare)
    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
        .set("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
        .set("Referer", "$baseUrl/")
        .set("Sec-Fetch-Dest", "document")
        .set("Sec-Fetch-Mode", "navigate")
        .set("Sec-Fetch-Site", "same-origin")
        .set("Sec-Fetch-User", "?1")
        .set("Upgrade-Insecure-Requests", "1")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ranking", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = response.asJsoupDoc().captureCsrf()
        val mangas = parseRankingCards(doc)
        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/atualizados-recente?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val doc = response.asJsoupDoc().captureCsrf()
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val mangas = parseCatalogCards(doc, sortByViews = false)
        val hasNext = doc.selectFirst(
            ".pagerx__link[rel=next], a.pagerx__link[href*=\"page=${currentPage + 1}\"]",
        ) != null
        return MangasPage(mangas, hasNext)
    }

    // =============================== Search ===============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val trimmed = query.trim()

        val directPath = when {
            trimmed.startsWith(PREFIX_PATH_SEARCH) -> trimmed.removePrefix(PREFIX_PATH_SEARCH)
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
                val parsed = trimmed.toHttpUrlOrNull()
                if (parsed != null && parsed.host == baseHttpUrl.host && parsed.encodedPath.startsWith("/comics/")) {
                    parsed.encodedPath
                } else {
                    null
                }
            }
            else -> null
        }

        if (!directPath.isNullOrEmpty()) {
            return Observable.fromCallable {
                val comicPath = normalizeComicPath(directPath)
                val req = GET("$baseUrl$comicPath", headers)
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@fromCallable MangasPage(emptyList(), false)
                    val manga = mangaDetailsParse(res).apply { url = comicPath }
                    MangasPage(listOf(manga), false)
                }
            }
        }

        return Observable.fromCallable {
            executeSearch(query = trimmed, filters = filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sortMode = filters.filterIsInstance<SortFilter>().firstOrNull()?.toMode() ?: "popular"
        if (query.isBlank() && sortMode == "ranking") {
            return GET("$baseUrl/ranking", headers)
        }

        val urlBuilder = "$baseUrl/comics".toHttpUrl().newBuilder()
        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("src", query.trim())
        }

        val status = filters.filterIsInstance<StatusFilter>().firstOrNull()?.toUriPart()
        val letter = filters.filterIsInstance<LetterFilter>().firstOrNull()?.toUriPart()
        val genre = filters.filterIsInstance<GenreFilter>().firstOrNull()?.toUriPart()

        if (!status.isNullOrEmpty()) urlBuilder.addQueryParameter("status", status)
        if (!letter.isNullOrEmpty()) urlBuilder.addQueryParameter("order", letter)
        if (!genre.isNullOrEmpty()) urlBuilder.addQueryParameter("gen", genre)

        return GET(urlBuilder.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val doc = response.asJsoupDoc().captureCsrf()
        if (response.request.url.encodedPath == "/ranking") {
            return MangasPage(parseRankingCards(doc), false)
        }
        return MangasPage(parseCatalogCards(doc, sortByViews = false), false)
    }

    private fun executeSearch(query: String, filters: FilterList): MangasPage {
        val sortMode = filters.filterIsInstance<SortFilter>().firstOrNull()?.toMode() ?: "popular"
        val req = searchMangaRequest(1, query, filters)

        val primaryResult = client.newCall(req).execute().use { response ->
            val doc = response.asJsoupDoc().captureCsrf()
            if (response.request.url.encodedPath == "/ranking") {
                return MangasPage(parseRankingCards(doc), false)
            }
            parseCatalogCards(doc, sortByViews = (sortMode == "popular"))
        }

        if (primaryResult.isNotEmpty() || query.isBlank()) {
            return MangasPage(primaryResult, false)
        }

        val previewItems = runCatching { fetchSearchPreviewAjax(query) }.getOrDefault(emptyList())
        return MangasPage(previewItems, false)
    }

    private fun fetchSearchPreviewAjax(query: String): List<SManga> {
        val csrf = ensureCsrfToken()
        if (csrf.isEmpty()) return emptyList()

        val ajaxHeaders = headersBuilder()
            .set("Accept", "application/json, text/javascript, */*; q=0.01")
            .set("Content-Type", "application/json")
            .set("Origin", baseUrl)
            .set("Referer", "$baseUrl/")
            .set("X-CSRF-TOKEN", csrf)
            .set("X-Requested-With", "XMLHttpRequest")
            .removeAll("Upgrade-Insecure-Requests")
            .set("Sec-Fetch-Dest", "empty")
            .set("Sec-Fetch-Mode", "cors")
            .set("Sec-Fetch-Site", "same-origin")
            .build()

        val payload = JSONObject().put("src", query).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        client.newCall(POST("$baseUrl/comics/search-preview", ajaxHeaders, payload)).execute().use { res ->
            if (!res.isSuccessful) {
                cachedCsrfToken = null
                return emptyList()
            }
            val root = JSONObject(res.body?.string().orEmpty())
            val arr = root.optJSONArray("items") ?: return emptyList()
            val list = ArrayList<SManga>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val pjtId = obj.optInt("PJT_ID", 0)
                val pjtName = obj.optString("PJT_NAME", "").trim()
                if (pjtId <= 0 || pjtName.isEmpty()) continue
                val imgUrl = obj.optString("PJT_IMG_PR_URL", "").ifBlank {
                    obj.optString("PJT_IMG_PR", "")
                }
                list.add(
                    SManga.create().apply {
                        title = pjtName
                        url = "/comics/$pjtId"
                        thumbnail_url = resolveAbsoluteUrl(imgUrl)
                    },
                )
            }
            return list
        }
    }

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoupDoc().captureCsrf()
        return SManga.create().apply {
            setUrlWithoutDomain(doc.location().ifBlank { response.request.url.toString() })
            title = doc.selectFirst("h1.project-title, .project-title")?.text()?.trim().orEmpty()
                .ifEmpty { doc.selectFirst("h1:not(#ageGateTitle)")?.text()?.trim().orEmpty() }
                .ifEmpty { doc.title().substringBefore(" - Blackout Comics").substringBefore(" | Blackout Comics").trim() }
            thumbnail_url = extractImageUrl(doc.selectFirst(".project-cover"))
            author = doc.selectFirst(".quick-info-item:has(.fa-pen-nib) strong")?.text()?.trim()
            artist = doc.selectFirst(".quick-info-item:has(.fa-palette) strong")?.text()?.trim()
            description = doc.selectFirst(".project-description")?.text()?.trim()
            genre = doc.select(".project-genres .genre-tag")
                .map { it.text().trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString()

            val statusText = doc.selectFirst(".status-pill")?.text()?.lowercase(Locale.ROOT).orEmpty()
            status = when {
                statusText.contains("lançamento") || statusText.contains("andamento") -> SManga.ONGOING
                statusText.contains("completo") || statusText.contains("finalizado") -> SManga.COMPLETED
                statusText.contains("hiato") || statusText.contains("pausado") -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
    }

    // ============================== Chapters ==============================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        Observable.fromCallable {
            ensureLoginSilently()
            client.newCall(chapterListRequest(manga)).execute().use { response ->
                chapterListParse(response)
            }
        }

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoupDoc().captureCsrf()
        val mangaPath = doc.location().toHttpUrlOrNull()?.encodedPath
            ?: response.request.url.encodedPath

        return doc.select("#tab-capitulos-list .normal_ep, #tab-capitulos-list li:has(.num)").map { el ->
            SChapter.create().apply {
                val linkElement = el.selectFirst("a[href]")
                val rawNum = el.selectFirst(".num")?.text()?.trim().orEmpty()

                if (linkElement != null) {
                    val href = linkElement.attr("abs:href").ifBlank { linkElement.attr("href") }
                    setUrlWithoutDomain(resolveAbsoluteUrl(href))
                } else {
                    url = "${mangaPath.trimEnd('/')}/ler/capitulo-$rawNum"
                }

                val epTitle = el.selectFirst(".cell-title strong.line-3")?.text()?.trim().orEmpty()
                name = buildString {
                    append(if (rawNum.isNotEmpty()) "Capítulo $rawNum" else "Capítulo")
                    if (epTitle.isNotEmpty()) {
                        append(" - ")
                        append(epTitle)
                    }
                }

                chapter_number = rawNum.replace(",", ".").toFloatOrNull() ?: -1f
                date_upload = parseChapterDate(el)
            }
        }
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterFullUrl = resolveAbsoluteUrl(chapter.url)
        val mangaPath = chapter.url.substringBefore("/ler")
        val mangaUrl = if (mangaPath.startsWith("http")) mangaPath else "$baseUrl$mangaPath"
        val reqHeaders = headersBuilder()
            .set("Referer", mangaUrl)
            .build()
        return GET(chapterFullUrl, reqHeaders)
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> =
        Observable.fromCallable {
            loadChapterPagesWithAuth(chapter)
        }

    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoupDoc().captureCsrf()
        val chapterUrl = response.request.url.toString()
        val pages = extractPagesFromDocument(doc, chapterUrl)
        if (pages.isNotEmpty()) {
            return pages
        }

        throw Exception("Nenhuma página encontrada neste capítulo.")
    }

    private fun loadChapterPagesWithAuth(chapter: SChapter): List<Page> {
        ensureLoginSilently()

        val mangaPath = chapter.url.substringBefore("/ler")
        val mangaUrl = if (mangaPath.startsWith("http")) mangaPath else "$baseUrl$mangaPath"
        val chapterUrl = resolveAbsoluteUrl(chapter.url)

        val chapterHeaders = headersBuilder()
            .set("Referer", mangaUrl)
            .build()

        var pages = fetchPagesFromUrl(chapterUrl, chapterHeaders)
        if (pages.isNotEmpty()) return pages

        // Se o #reader-payload não veio e há credenciais salvas, autentica e tenta novamente
        val email = getSavedEmail()
        val password = getSavedPassword()
        if (email.isNotEmpty() && password.isNotEmpty()) {
            val logged = runCatching { performLogin(email, password) }.isSuccess
            if (logged) {
                pages = fetchPagesFromUrl(chapterUrl, chapterHeaders)
                if (pages.isNotEmpty()) return pages
            }
        }

        // Fallback caso o capítulo tenha zero à esquerda (ex.: capitulo-01 -> capitulo-1)
        val altPath = stripLeadingZeroInChapterPath(chapter.url)
        if (altPath != null && altPath != chapter.url) {
            val altUrl = resolveAbsoluteUrl(altPath)
            pages = fetchPagesFromUrl(altUrl, chapterHeaders)
            if (pages.isNotEmpty()) return pages
        }

        if (email.isEmpty()) {
            throw Exception("Capítulo protegido: configure e-mail e senha nas opções da extensão ou faça login na WebView (🌐).")
        }

        throw Exception("Nenhuma página encontrada. Abra na WebView (🌐) para verificar o acesso.")
    }

    private fun fetchPagesFromUrl(url: String, chapterHeaders: Headers): List<Page> {
        return client.newCall(GET(url, chapterHeaders)).execute().use { response ->
            if (response.code == 429) {
                throw Exception("Muitas requisições (HTTP 429): aguarde 1 minuto para o site liberar e tente novamente.")
            }
            if (response.code == 403) {
                throw Exception("HTTP 403: Acesso bloqueado. Abra a WebView (ícone 🌐 no topo) e conclua a verificação.")
            }
            if (!response.isSuccessful) {
                return emptyList()
            }
            val doc = response.asJsoupDoc().captureCsrf()
            extractPagesFromDocument(doc, url)
        }
    }

    private fun toPages(urls: List<String>, chapterUrl: String): List<Page> =
        urls.distinct().mapIndexed { index, imgUrl -> Page(index, chapterUrl, imgUrl) }

    private fun extractPagesFromDocument(doc: Document, chapterUrl: String): List<Page> {
        // Estratégia 1 (Padrão Blackout Comics): #reader-payload em Base64
        val payload = doc.selectFirst("#reader-payload")?.attr("data-payload")?.trim().orEmpty()
        if (payload.isNotEmpty()) {
            val decodedJson = runCatching {
                Base64.decode(payload, Base64.DEFAULT).toString(Charsets.UTF_8)
            }.getOrNull()
            if (!decodedJson.isNullOrEmpty()) {
                val urls = extractUrlsFromDecodedJson(decodedJson)
                if (urls.isNotEmpty()) {
                    return toPages(urls, chapterUrl)
                }
            }
        }

        val scriptElements = doc.select("script:not([src])")
        val scripts = scriptElements.flatMap { listOf(it.data(), it.html()) }.filter { it.isNotBlank() }

        // Estratégia 2: Array JS atribuído a variável (var S = [...]; etc.)
        for (content in scripts) {
            for (match in JS_ARRAY_ASSIGNMENT_REGEX.findAll(content)) {
                val arrayContent = match.groupValues[1]
                val urls = extractUrlsFromJsBlock(arrayContent)
                if (urls.isNotEmpty() && urls.any { isLikelyChapterImage(it) }) {
                    return toPages(urls, chapterUrl)
                }
            }
        }

        // Estratégia 3: qualquer array de URLs em scripts
        for (content in scripts) {
            for (match in GENERIC_BRACKETED_ARRAY_REGEX.findAll(content)) {
                val arrayContent = match.groupValues[1]
                val urls = extractUrlsFromJsBlock(arrayContent)
                if (urls.isNotEmpty() && urls.any { isLikelyChapterImage(it) }) {
                    return toPages(urls, chapterUrl)
                }
            }
        }

        // Estratégia 4: URLs de imagens entre aspas em scripts
        for (content in scripts) {
            val urls = extractAllQuotedImageUrls(content)
            if (urls.isNotEmpty()) {
                return toPages(urls, chapterUrl)
            }
        }

        // Estratégia 5: tags <img> já renderizadas
        val domImages = doc.select("#pages img, img.slice, .viewer-body img.slice, #viewer-img img, #viewer-img-container img")
            .mapNotNull { extractReaderImage(it) }
            .filter { isLikelyChapterImage(it) }
        if (domImages.isNotEmpty()) {
            return toPages(domImages, chapterUrl)
        }

        return emptyList()
    }

    private fun extractUrlsFromDecodedJson(jsonString: String): List<String> {
        val urls = ArrayList<String>()
        val arr = runCatching { JSONArray(jsonString) }.getOrNull()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val item = arr.optString(i, "").trim()
                if (item.isNotEmpty()) {
                    urls.add(resolveAbsoluteUrl(item))
                }
            }
        }
        if (urls.isEmpty()) {
            for (m in QUOTED_STRING_REGEX.findAll(jsonString)) {
                val item = unescapeJsString(m.groupValues[1])
                if (item.isNotEmpty() && (item.startsWith("http") || item.startsWith("/") || IMAGE_EXT_REGEX.containsMatchIn(item))) {
                    urls.add(resolveAbsoluteUrl(item))
                }
            }
        }
        return urls.distinct()
    }

    private fun extractUrlsFromJsBlock(rawBlock: String): List<String> {
        val urls = ArrayList<String>()
        for (m in QUOTED_STRING_REGEX.findAll(rawBlock)) {
            val unescaped = unescapeJsString(m.groupValues[1])
            if (unescaped.isBlank() || isNonContentImage(unescaped)) continue
            if (unescaped.startsWith("http://") || unescaped.startsWith("https://") ||
                unescaped.startsWith("//") || unescaped.startsWith("/") ||
                IMAGE_EXT_REGEX.containsMatchIn(unescaped)
            ) {
                urls.add(resolveAbsoluteUrl(unescaped))
            }
        }
        return urls.distinct()
    }

    private fun extractAllQuotedImageUrls(scriptContent: String): List<String> {
        val urls = ArrayList<String>()
        for (m in QUOTED_STRING_REGEX.findAll(scriptContent)) {
            val unescaped = unescapeJsString(m.groupValues[1])
            if (unescaped.isBlank() || isNonContentImage(unescaped)) continue
            if (isLikelyChapterImage(unescaped)) {
                urls.add(resolveAbsoluteUrl(unescaped))
            }
        }
        return urls.distinct()
    }

    private fun unescapeJsString(s: String): String =
        s.replace("\\/", "/")
            .replace("\\u002F", "/", ignoreCase = true)
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\\\", "\\")
            .trim()

    private fun isLikelyChapterImage(url: String): Boolean {
        if (isNonContentImage(url)) return false
        val lower = url.lowercase(Locale.ROOT)
        return lower.contains("/projects/") || lower.contains("/chapters/") ||
            lower.contains("/capitulos/") || lower.contains("/storage/") ||
            lower.contains("/uploads/") || IMAGE_EXT_REGEX.containsMatchIn(lower)
    }

    private fun isNonContentImage(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return lower.contains("/info/") || lower.contains("/previews/") ||
            lower.contains("favicon") || lower.contains("logo") ||
            lower.contains("avatar") || lower.contains("banner") ||
            lower.contains("/media/") || lower.contains("/themes/") ||
            lower.contains("placeholder") || lower.contains("adblock")
    }

    private fun extractReaderImage(img: Element): String? {
        for (attr in listOf("data-src", "data-lazy-src", "data-original", "src")) {
            val value = img.attr(attr).trim()
            if (value.isNotEmpty() && !value.startsWith("data:")) {
                return resolveAbsoluteUrl(value)
            }
        }
        return null
    }

    // ============================= Image HTTP =============================

    override fun imageRequest(page: Page): Request {
        val imgUrl = page.imageUrl ?: throw Exception("URL da imagem ausente")

        val imageHeaders = headersBuilder()
            .set("Accept", "image/*")
            .set("Sec-Fetch-Dest", "empty")
            .set("Sec-Fetch-Mode", "cors")
            .set("Sec-Fetch-Site", "same-origin")
            .removeAll("Referer")
            .removeAll("Upgrade-Insecure-Requests")
            .build()

        return GET(imgUrl, imageHeaders)
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("Not used")

    // ============================== Filters ===============================

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        LetterFilter(),
        GenreFilter(),
    )

    // ========================== Login & Settings ==========================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        cachedAppContext = runCatching {
            screen.context.javaClass.getMethod("getApplicationContext").invoke(screen.context)
        }.getOrNull() ?: screen.context
        val prefs = preferences

        val savedEmail = getSavedEmail()
        val emailPref = EditTextPreference(screen.context).apply {
            key = PREF_EMAIL_KEY
            title = "E-mail da conta Blackout Comics"
            dialogTitle = "E-mail de Login"
            setDefaultValue("")
            summary = if (savedEmail.isEmpty()) {
                "Opcional se já fez login pela WebView (🌐). Preencha para login automático."
            } else {
                "Conectado como: $savedEmail"
            }
            setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                editText.hint = "seu@email.com"
            }
            setOnPreferenceChangeListener { pref, newValue ->
                loginVerifiedInMemory = false
                val updated = (newValue as? String)?.trim().orEmpty()
                pref.summary = if (updated.isEmpty()) {
                    "Opcional se já fez login pela WebView (🌐). Preencha para login automático."
                } else {
                    "Conectado como: $updated"
                }
                prefs?.edit()
                    ?.putString(PREF_EMAIL_KEY, updated)
                    ?.putString(AWERKORI_PREF_EMAIL, updated)
                    ?.apply()
                Toast.makeText(
                    screen.context,
                    "E-mail salvo! O login será atualizado ao abrir um capítulo.",
                    Toast.LENGTH_SHORT,
                ).show()
                true
            }
        }

        val savedPass = getSavedPassword()
        val passPref = EditTextPreference(screen.context).apply {
            key = PREF_PASSWORD_KEY
            title = "Senha da conta"
            dialogTitle = "Senha"
            setDefaultValue("")
            summary = if (savedPass.isEmpty()) {
                "Preencha sua senha (ou deixe em branco se usar login via WebView)."
            } else {
                "•••••••• (senha salva)"
            }
            setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                editText.hint = "••••••••"
            }
            setOnPreferenceChangeListener { pref, newValue ->
                loginVerifiedInMemory = false
                val updated = (newValue as? String).orEmpty()
                pref.summary = if (updated.isEmpty()) {
                    "Preencha sua senha (ou deixe em branco se usar login via WebView)."
                } else {
                    "•••••••• (senha salva)"
                }
                prefs?.edit()
                    ?.putString(PREF_PASSWORD_KEY, updated)
                    ?.putString(AWERKORI_PREF_PASSWORD, updated)
                    ?.apply()
                Toast.makeText(
                    screen.context,
                    "Senha salva!",
                    Toast.LENGTH_SHORT,
                ).show()
                true
            }
        }

        val clearSessionPref = createPreference(screen.context).apply {
            key = PREF_CLEAR_SESSION_KEY
            title = "Limpar sessão"
            summary = "Remove os cookies salvos da Blackout Comics."
            setOnPreferenceClickListener {
                loginVerifiedInMemory = false
                val url = baseHttpUrl
                runCatching {
                    network.client.cookieJar.saveFromResponse(
                        url,
                        listOf(SESSION_COOKIE, XSRF_COOKIE).map { name ->
                            Cookie.Builder().name(name).value("").domain(url.host).path("/").expiresAt(0L).build()
                        },
                    )
                }
                runCatching {
                    val cookieManager = android.webkit.CookieManager.getInstance()
                    cookieManager.setCookie(baseUrl, "$SESSION_COOKIE=; Max-Age=0")
                    cookieManager.setCookie(baseUrl, "$XSRF_COOKIE=; Max-Age=0")
                    cookieManager.flush()
                }
                Toast.makeText(screen.context, "Sessão limpa!", Toast.LENGTH_SHORT).show()
                true
            }
        }

        screen.addPreference(emailPref)
        screen.addPreference(passPref)
        screen.addPreference(clearSessionPref)
    }

    private fun createPreference(context: Context): Preference = runCatching {
        Preference::class.java.getConstructor(Context::class.java).newInstance(context)
    }.getOrElse { Preference() }

    private fun getSavedEmail(): String {
        val prefs = preferences ?: return ""
        val p1 = prefs.getString(PREF_EMAIL_KEY, "")?.trim().orEmpty()
        if (p1.isNotEmpty()) return p1
        return prefs.getString(AWERKORI_PREF_EMAIL, "")?.trim().orEmpty()
    }

    private fun getSavedPassword(): String {
        val prefs = preferences ?: return ""
        val p1 = prefs.getString(PREF_PASSWORD_KEY, "").orEmpty()
        if (p1.isNotEmpty()) return p1
        return prefs.getString(AWERKORI_PREF_PASSWORD, "").orEmpty()
    }

    private fun hasSessionCookie(): Boolean {
        val webViewCookies = runCatching {
            android.webkit.CookieManager.getInstance().getCookie("$baseUrl/")
        }.getOrNull().orEmpty()
        if (webViewCookies.contains("blackout-comics-session")) return true

        val okHttpCookies = runCatching {
            network.client.cookieJar.loadForRequest(baseHttpUrl)
        }.getOrDefault(emptyList())
        return okHttpCookies.any { it.name == "blackout-comics-session" && it.value.isNotBlank() }
    }

    private fun ensureLoginSilently() {
        val email = getSavedEmail()
        val password = getSavedPassword()
        if (email.isEmpty() || password.isEmpty()) return
        if (!loginVerifiedInMemory && !hasSessionCookie()) {
            runCatching { performLogin(email, password) }
        }
    }

    @Volatile
    private var lastLoginAttemptTime = 0L

    @Synchronized
    private fun performLogin(email: String, password: String) {
        val now = System.currentTimeMillis()
        if (now - lastLoginAttemptTime < 60_000L) return
        lastLoginAttemptTime = now

        val csrfToken = ensureCsrfToken(forceRefresh = true)
        if (csrfToken.isEmpty()) return

        val formBody = FormBody.Builder()
            .add("_token", csrfToken)
            .add("USE_EMAIL", email)
            .add("password", password)
            .build()

        val loginHeaders = headersBuilder()
            .set("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .set("Origin", baseUrl)
            .set("Referer", "$baseUrl/")
            .set("X-CSRF-TOKEN", csrfToken)
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        client.newCall(POST("$baseUrl/entrar", loginHeaders, formBody)).execute().use { res ->
            if (res.code == 429) {
                throw Exception("Muitas requisições ao entrar (HTTP 429). Aguarde 1 minuto.")
            }
            loginVerifiedInMemory = true
        }
    }

    private fun ensureCsrfToken(forceRefresh: Boolean = false): String {
        if (!forceRefresh) {
            cachedCsrfToken?.takeIf { it.isNotBlank() }?.let { return it }
        }
        runCatching {
            client.newCall(GET(baseUrl, headers)).execute().use { res ->
                res.asJsoupDoc().captureCsrf()
            }
        }
        return cachedCsrfToken.orEmpty()
    }

    // ============================ Interceptors ============================

    private fun ageGateAndCookiesInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        if (url.host == baseHttpUrl.host || url.host.endsWith(".${baseHttpUrl.host}")) {
            val now = System.currentTimeMillis()
            val expires = now + 6 * 24 * 60 * 60 * 1000L
            val ageConsentValue = "%7B%22consentAt%22%3A$now%2C%22expiresAt%22%3A$expires%7D"

            runCatching {
                val cookieJar = network.client.cookieJar
                val existing = cookieJar.loadForRequest(url)
                val toSave = mutableListOf<Cookie>()

                if (existing.none { it.name == "age_gate_consent" }) {
                    toSave.add(
                        Cookie.Builder()
                            .name("age_gate_consent")
                            .value(ageConsentValue)
                            .domain(baseHttpUrl.host)
                            .path("/")
                            .build(),
                    )
                }
                if (existing.none { it.name == "_popprepop" }) {
                    toSave.add(
                        Cookie.Builder()
                            .name("_popprepop")
                            .value("1")
                            .domain(baseHttpUrl.host)
                            .path("/")
                            .build(),
                    )
                }
                if (toSave.isNotEmpty()) {
                    cookieJar.saveFromResponse(baseHttpUrl, toSave)
                    android.webkit.CookieManager.getInstance().flush()
                }
            }
        }

        // Deixa o CookieJar fornecer os cookies completos (cf_clearance, sessão, idade) sem sobrescrever o header
        return chain.proceed(request)
    }

    // ========================== Parsing Helpers ===========================

    private fun parseCatalogCards(doc: Document, sortByViews: Boolean): List<SManga> {
        val seenUrls = HashSet<String>()
        val parsed = mutableListOf<Pair<SManga, Long>>()

        for (el in doc.select("a.webtoon-card, .webtoon-grid a[href*=\"/comics/\"]")) {
            val href = el.attr("abs:href").ifBlank { el.attr("href") }
            if (href.isBlank()) continue

            val manga = SManga.create().apply {
                setUrlWithoutDomain(resolveAbsoluteUrl(href))
                title = el.selectFirst(".card-title span, .card-title, h2, h3")
                    ?.text()
                    ?.trim()
                    .orEmpty()
                thumbnail_url = extractImageUrl(el.selectFirst(".card-thumb img, img"))
            }

            if (manga.title.isEmpty() || !seenUrls.add(manga.url)) continue

            val viewsText = el.selectFirst(".views")?.text().orEmpty()
            val viewsCount = viewsText.replace(NON_DIGITS_REGEX, "").toLongOrNull() ?: 0L
            parsed.add(manga to viewsCount)
        }

        return if (sortByViews) {
            parsed.sortedByDescending { it.second }.map { it.first }.take(24)
        } else {
            parsed.map { it.first }.take(24)
        }
    }

    private fun parseRankingCards(doc: Document): List<SManga> {
        val seenUrls = HashSet<String>()
        val mangas = mutableListOf<SManga>()

        for (el in doc.select("a.ranking-featured-card, .ranking-grid a.webtoon-card")) {
            val href = el.attr("abs:href").ifBlank { el.attr("href") }
            if (href.isBlank()) continue

            val manga = SManga.create().apply {
                setUrlWithoutDomain(resolveAbsoluteUrl(href))
                title = el.selectFirst(".ranking-featured-info h2, .card-title span, .card-title, h2")
                    ?.text()
                    ?.trim()
                    .orEmpty()
                thumbnail_url = extractImageUrl(el.selectFirst("img"))
            }

            if (manga.title.isNotEmpty() && seenUrls.add(manga.url)) {
                mangas.add(manga)
            }
        }
        return mangas
    }

    private fun extractImageUrl(img: Element?): String? {
        if (img == null) return null
        val raw = img.attr("data-src")
            .ifBlank { img.attr("data-lazy-src") }
            .ifBlank { img.attr("src") }
            .trim()
        if (raw.isEmpty()) return null
        return resolveAbsoluteUrl(raw)
    }

    private fun resolveAbsoluteUrl(raw: String): String {
        val cleaned = raw.trim()
        return when {
            cleaned.isEmpty() -> ""
            cleaned.startsWith("http://") || cleaned.startsWith("https://") -> cleaned
            cleaned.startsWith("//") -> "https:$cleaned"
            cleaned.startsWith("/") -> "$baseUrl$cleaned"
            else -> "$baseUrl/$cleaned"
        }
    }

    private fun normalizeComicPath(path: String): String {
        val segments = path.trim('/').split('/')
        return if (segments.size >= 2 && segments[0] == "comics") {
            "/comics/${segments[1]}"
        } else {
            "/${path.trimStart('/')}"
        }
    }

    private fun stripLeadingZeroInChapterPath(path: String): String? {
        val replaced = LEADING_ZERO_CHAPTER_REGEX.replace(path) { match ->
            "/ler/capitulo-${match.groupValues[1]}"
        }
        return replaced.takeIf { it != path }
    }

    private fun parseChapterDate(el: Element): Long {
        val isoAttr = el.selectFirst("time[datetime]")?.attr("datetime")?.trim().orEmpty()
        if (isoAttr.length >= 10) {
            runCatching {
                SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(isoAttr.substring(0, 10))?.time
            }.getOrNull()?.let { return it }
        }

        val shortText = el.selectFirst(".cell-num .text-muted")?.text()?.trim().orEmpty()
        if (shortText.isNotEmpty()) {
            runCatching {
                SimpleDateFormat("dd.MM.yy", Locale.ROOT).parse(shortText)?.time
            }.getOrNull()?.let { return it }
        }

        return 0L
    }

    private fun Response.asJsoupDoc(): Document =
        Jsoup.parse(body?.string().orEmpty(), request.url.toString())

    private fun Document.captureCsrf(): Document = apply {
        selectFirst("meta[name=csrf-token]")?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?.let { cachedCsrfToken = it }
    }

    companion object {
        const val PREFIX_PATH_SEARCH = "path:"

        private const val PREF_EMAIL_KEY = "pref_email"
        private const val PREF_PASSWORD_KEY = "pref_password"
        private const val AWERKORI_PREF_EMAIL = "blackout_email"
        private const val AWERKORI_PREF_PASSWORD = "blackout_password"
        private const val PREF_CLEAR_SESSION_KEY = "blackout_clear_session"

        private const val SESSION_COOKIE = "blackout-comics-session"
        private const val XSRF_COOKIE = "XSRF-TOKEN"

        private val IMAGE_EXT_REGEX = Regex("""\.(?:webp|jpe?g|png|avif|gif|bmp)(?:\?|$)""", RegexOption.IGNORE_CASE)
        private val QUOTED_STRING_REGEX = Regex("""["'`]([^"'`\\]*(?:\\.[^"'`\\]*)*)["'`]""")
        private val JS_ARRAY_ASSIGNMENT_REGEX = Regex(
            """(?:[a-zA-Z0-9_$]+(?:\.[a-zA-Z0-9_$]+)*\s*=\s*|:\s*)\[([\s\S]*?)\]""",
        )
        private val GENERIC_BRACKETED_ARRAY_REGEX = Regex("""\[([\s\S]*?)\]""")
        private val NON_DIGITS_REGEX = Regex("""[^0-9]""")
        private val LEADING_ZERO_CHAPTER_REGEX = Regex("""/ler/capitulo-0+(\d+(?:[.,-]\d+)?)""")
    }
}

private class RateLimitInterceptor(
    private val permits: Int = 2,
    private val periodMs: Long = 1200L,
) : Interceptor {
    private val requestTimes = ArrayDeque<Long>()

    @Synchronized
    override fun intercept(chain: Interceptor.Chain): Response {
        val now = SystemClock.elapsedRealtime()
        while (!requestTimes.isEmpty()) {
            val earliest = requestTimes.peekFirst() ?: break
            if (now - earliest > periodMs) {
                requestTimes.pollFirst()
            } else {
                break
            }
        }
        if (requestTimes.size >= permits) {
            val earliest = requestTimes.peekFirst()
            if (earliest != null) {
                val waitMs = periodMs - (now - earliest)
                if (waitMs > 0) {
                    try {
                        Thread.sleep(waitMs)
                    } catch (_: InterruptedException) { }
                }
            }
        }
        requestTimes.addLast(SystemClock.elapsedRealtime())
        return chain.proceed(chain.request())
    }
}
