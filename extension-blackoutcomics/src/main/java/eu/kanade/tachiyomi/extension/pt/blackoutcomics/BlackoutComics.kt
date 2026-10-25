package eu.kanade.tachiyomi.extension.pt.blackoutcomics

import android.content.SharedPreferences
import android.text.InputType
import android.widget.Toast
import androidx.preference.EditTextPreference
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
import java.util.Locale

class BlackoutComics : HttpSource(), ConfigurableSource {

    override val name = "Blackout Comics"

    override val baseUrl = "https://blackoutcomics.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    private val baseHttpUrl by lazy { baseUrl.toHttpUrl() }

    // O stub de Context do extensions-lib 1.4.4 não expõe getSharedPreferences/applicationContext,
    // então o acesso é feito por reflexão (o app hospedeiro fornece a implementação real em runtime).
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
            .addInterceptor(::ageGateAndCookiesInterceptor)
            .addInterceptor(::anti403RetryInterceptor)
            .build()
    }

    private val appDefaultUserAgent: String by lazy {
        runCatching { super.headersBuilder().build()["User-Agent"]?.trim().orEmpty() }.getOrDefault("")
    }

    private fun resolveUserAgent(): String {
        val defaultUa = appDefaultUserAgent
        return if (defaultUa.isBlank() || defaultUa.contains("okhttp", ignoreCase = true)) {
            FALLBACK_USER_AGENT
        } else {
            defaultUa
        }
    }

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .set("User-Agent", resolveUserAgent())
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
        .set("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
        .set("Referer", "$baseUrl/")
        .set("DNT", "1")
        .set("Upgrade-Insecure-Requests", "1")
        .set("Sec-Fetch-Dest", "document")
        .set("Sec-Fetch-Mode", "navigate")
        .set("Sec-Fetch-Site", "same-origin")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/comics", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = response.asJsoupDoc().captureCsrf()
        val mangas = parseCatalogCards(doc, sortByViews = true)
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

        // Suporte a DeepLink (path:/comics/123) ou URL colada diretamente na busca
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

        // Fallback para o endpoint AJAX /comics/search-preview caso o filtro GET não encontre
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
            title = doc.selectFirst(".project-title, h1")?.text()?.trim().orEmpty()
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
            tryAutoLoginSilently()
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
        val parentComicReferer = extractParentComicUrl(chapter.url) ?: "$baseUrl/"
        val reqHeaders = headersBuilder()
            .set("Referer", parentComicReferer)
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

        if (isLoginRequiredDocument(doc, response)) {
            throw Exception(LOGIN_REQUIRED_MESSAGE)
        }

        throw Exception("Nenhuma página encontrada neste capítulo.")
    }

    private fun loadChapterPagesWithAuth(chapter: SChapter): List<Page> {
        if (hasConfiguredCredentials() && !loginVerifiedInMemory && !hasSessionCookie()) {
            performCredentialsLogin(force = false)
        }

        val firstAttempt = executeChapterCall(chapter.url)
        if (firstAttempt.pages.isNotEmpty()) {
            return firstAttempt.pages
        }

        // Se a sessão expirou ou redirecionou para login e o usuário configurou E-mail/Senha, renova o login e tenta novamente
        if (firstAttempt.loginRequired && hasConfiguredCredentials()) {
            performCredentialsLogin(force = true)
            val retryAfterLogin = executeChapterCall(chapter.url)
            if (retryAfterLogin.pages.isNotEmpty()) {
                return retryAfterLogin.pages
            }
        }

        // Fallback caso o capítulo salvo tenha zero à esquerda (ex.: capitulo-01 -> capitulo-1)
        val altPath = stripLeadingZeroInChapterPath(chapter.url)
        if (altPath != null && altPath != chapter.url) {
            val altAttempt = executeChapterCall(altPath)
            if (altAttempt.pages.isNotEmpty()) {
                return altAttempt.pages
            }
            if (altAttempt.loginRequired) {
                throw Exception(LOGIN_REQUIRED_MESSAGE)
            }
        }

        if (firstAttempt.loginRequired) {
            throw Exception(LOGIN_REQUIRED_MESSAGE)
        }

        if (firstAttempt.httpCode == 403) {
            throw Exception(
                "Acesso bloqueado (HTTP 403). Abra a WebView (ícone 🌐 no topo), " +
                    "aguarde carregar o site/faça login na sua conta e tente novamente.",
            )
        }

        throw Exception("Nenhuma página encontrada neste capítulo (HTTP ${firstAttempt.httpCode}).")
    }

    private data class ChapterFetchOutcome(
        val pages: List<Page>,
        val loginRequired: Boolean,
        val httpCode: Int,
    )

    private fun executeChapterCall(chapterPath: String): ChapterFetchOutcome {
        val tempChapter = SChapter.create().apply { url = chapterPath }
        val req = pageListRequest(tempChapter)
        client.newCall(req).execute().use { response ->
            val code = response.code
            val doc = response.asJsoupDoc().captureCsrf()
            val pages = extractPagesFromDocument(doc, req.url.toString())
            if (pages.isNotEmpty()) {
                return ChapterFetchOutcome(pages, false, code)
            }
            val loginNeeded = isLoginRequiredDocument(doc, response)
            return ChapterFetchOutcome(emptyList(), loginNeeded, code)
        }
    }

    private fun extractPagesFromDocument(doc: Document, chapterUrl: String): List<Page> {
        // Estratégia 1: Array JS inline `S = [...]` no leitor da Blackout
        for (script in doc.select("script:not([src])")) {
            val scriptContent = script.data().ifEmpty { script.html() }
            val match = PAGE_LIST_REGEX.find(scriptContent) ?: continue
            val rawArray = runCatching { JSONArray(match.groupValues[1]) }.getOrNull() ?: continue
            val urls = ArrayList<String>(rawArray.length())
            for (i in 0 until rawArray.length()) {
                val item = rawArray.optString(i, "").trim()
                if (item.isNotEmpty()) {
                    urls.add(resolveAbsoluteUrl(item))
                }
            }
            if (urls.isNotEmpty()) {
                return urls.mapIndexed { index, imgUrl ->
                    Page(index, chapterUrl, imgUrl)
                }
            }
        }

        // Estratégia 2: Fallback direto nas tags <img> do container #pages / #viewer-img
        val domImages = doc.select("#pages img.slice, #pages img, #viewer-img img")
            .mapNotNull { extractImageUrl(it) }
            .filter { it.isNotBlank() }

        if (domImages.isNotEmpty()) {
            return domImages.mapIndexed { index, imgUrl ->
                Page(index, chapterUrl, imgUrl)
            }
        }

        return emptyList()
    }

    private fun isLoginRequiredDocument(doc: Document, response: Response): Boolean {
        val finalUrl = response.request.url
        val finalPath = finalUrl.encodedPath.trimEnd('/')
        if (finalPath.isEmpty() || finalPath == "/login" || finalPath == "/entrar") {
            return true
        }
        val html = doc.outerHtml()
        return html.contains("showLoginModal()") ||
            (doc.selectFirst("#modal-login.show") != null) ||
            (!finalPath.contains("/ler/") && response.priorResponse != null)
    }

    // ============================= Image HTTP =============================

    override fun imageRequest(page: Page): Request {
        val imgUrl = page.imageUrl ?: throw Exception("URL da imagem ausente")
        val targetHttpUrl = imgUrl.toHttpUrlOrNull()
        val sameOrigin = targetHttpUrl?.host == baseHttpUrl.host
        val chapterReferer = page.url.takeIf { it.startsWith("http") } ?: "$baseUrl/"

        val imgHeaders = headersBuilder()
            .removeAll("Referer")
            .removeAll("Upgrade-Insecure-Requests")
            .removeAll("Sec-Fetch-Dest")
            .removeAll("Sec-Fetch-Mode")
            .removeAll("Sec-Fetch-Site")
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .set("Sec-Fetch-Dest", "image")
            .set("Sec-Fetch-Mode", "no-cors")
            .set("Sec-Fetch-Site", if (sameOrigin) "same-origin" else "cross-site")
            .set(INTERNAL_CHAPTER_REF_HEADER, chapterReferer)
            .build()

        return GET(imgUrl, imgHeaders)
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

        val savedEmail = prefs?.getString(PREF_EMAIL_KEY, "")?.trim().orEmpty()
        val emailPref = EditTextPreference(screen.context).apply {
            key = PREF_EMAIL_KEY
            title = "E-mail da conta Blackout Comics"
            dialogTitle = "E-mail de Login"
            setDefaultValue("")
            summary = if (savedEmail.isEmpty()) {
                "Opcional se você já fez login pela WebView (🌐). Preencha para login automático."
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
                    "Opcional se você já fez login pela WebView (🌐). Preencha para login automático."
                } else {
                    "Conectado como: $updated"
                }
                Toast.makeText(
                    screen.context,
                    "E-mail salvo! O login será atualizado ao abrir um capítulo.",
                    Toast.LENGTH_SHORT,
                ).show()
                true
            }
        }

        val savedPass = prefs?.getString(PREF_PASSWORD_KEY, "").orEmpty()
        val passPref = EditTextPreference(screen.context).apply {
            key = PREF_PASSWORD_KEY
            title = "Senha da conta"
            dialogTitle = "Senha"
            setDefaultValue("")
            summary = if (savedPass.isEmpty()) {
                "Preencha sua senha (ou deixe em branco para usar apenas o login via WebView)."
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
                    "Preencha sua senha (ou deixe em branco para usar apenas o login via WebView)."
                } else {
                    "•••••••• (senha salva)"
                }
                Toast.makeText(
                    screen.context,
                    "Senha salva!",
                    Toast.LENGTH_SHORT,
                ).show()
                true
            }
        }

        screen.addPreference(emailPref)
        screen.addPreference(passPref)
    }

    private fun hasConfiguredCredentials(): Boolean {
        val prefs = preferences ?: return false
        val email = prefs.getString(PREF_EMAIL_KEY, "")?.trim().orEmpty()
        val password = prefs.getString(PREF_PASSWORD_KEY, "").orEmpty()
        return email.isNotEmpty() && password.isNotEmpty()
    }

    private fun hasSessionCookie(): Boolean {
        val cookies = runCatching { network.client.cookieJar.loadForRequest(baseHttpUrl) }.getOrDefault(emptyList())
        return cookies.any { it.name == "blackout-comics-session" && it.value.isNotBlank() }
    }

    private fun tryAutoLoginSilently() {
        if (!hasConfiguredCredentials() || loginVerifiedInMemory) return
        runCatching { performCredentialsLogin(force = false) }
    }

    @Synchronized
    private fun performCredentialsLogin(force: Boolean) {
        if (!force && loginVerifiedInMemory) return

        val prefs = preferences ?: return
        val email = prefs.getString(PREF_EMAIL_KEY, "")?.trim().orEmpty()
        val password = prefs.getString(PREF_PASSWORD_KEY, "").orEmpty()
        if (email.isEmpty() || password.isEmpty()) return

        val csrfToken = ensureCsrfToken(forceRefresh = true)
        if (csrfToken.isEmpty()) {
            throw Exception("Não foi possível obter o token CSRF do site. Abra na WebView (🌐) uma vez.")
        }

        val formBody = FormBody.Builder()
            .add("_token", csrfToken)
            .add("USE_EMAIL", email)
            .add("password", password)
            .build()

        val loginHeaders = headersBuilder()
            .set("Accept", "application/json, text/javascript, */*; q=0.01")
            .set("Origin", baseUrl)
            .set("Referer", "$baseUrl/")
            .set("X-CSRF-TOKEN", csrfToken)
            .set("X-Requested-With", "XMLHttpRequest")
            .removeAll("Upgrade-Insecure-Requests")
            .set("Sec-Fetch-Dest", "empty")
            .set("Sec-Fetch-Mode", "cors")
            .set("Sec-Fetch-Site", "same-origin")
            .build()

        client.newCall(POST("$baseUrl/entrar", loginHeaders, formBody)).execute().use { res ->
            val rawBody = res.body?.string().orEmpty()
            val json = runCatching { JSONObject(rawBody) }.getOrNull()

            if (json != null) {
                if (json.optBoolean("two_factor_required", false)) {
                    throw Exception(
                        "Sua conta usa verificação em 2 etapas (2FA). " +
                            "Abra a WebView (ícone 🌐 no topo) e faça o login por lá.",
                    )
                }
                if (json.optBoolean("email_verification_required", false)) {
                    val msg = json.optString("error").ifBlank {
                        json.optString("message", "Conta aguardando verificação de e-mail.")
                    }
                    throw Exception(msg)
                }
                if (json.optBoolean("security_update_required", false)) {
                    throw Exception(
                        "O site solicitou atualização de segurança na conta. " +
                            "Abra a WebView (ícone 🌐 no topo) para concluir.",
                    )
                }
                if (!res.isSuccessful) {
                    val errMsg = json.optString("message").ifBlank {
                        json.optString("error", "Credenciais inválidas na Blackout Comics.")
                    }
                    throw Exception("Falha no login: $errMsg")
                }
            } else if (!res.isSuccessful) {
                throw Exception(
                    "Falha no login automático (HTTP ${res.code}). " +
                        "Verifique seu e-mail/senha ou faça login pela WebView (🌐).",
                )
            }

            loginVerifiedInMemory = true
        }
    }

    private fun ensureCsrfToken(forceRefresh: Boolean = false): String {
        if (!forceRefresh) {
            cachedCsrfToken?.takeIf { it.isNotBlank() }?.let { return it }
        }
        client.newCall(GET(baseUrl, headers)).execute().use { res ->
            res.asJsoupDoc().captureCsrf()
        }
        return cachedCsrfToken.orEmpty()
    }

    // ============================ Interceptors ============================

    private fun ageGateAndCookiesInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        if (url.host == baseHttpUrl.host || url.host.endsWith(".${baseHttpUrl.host}")) {
            val cookieJar = network.client.cookieJar
            val existing = runCatching { cookieJar.loadForRequest(url) }.getOrDefault(emptyList())
            val toSave = mutableListOf<Cookie>()

            if (existing.none { it.name == "age_gate_consent" }) {
                toSave.add(
                    Cookie.Builder()
                        .name("age_gate_consent")
                        .value("""{"consentAt":1790812800000,"expiresAt":2145916800000}""")
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
                runCatching { cookieJar.saveFromResponse(baseHttpUrl, toSave) }
            }
        }

        return chain.proceed(request)
    }

    private fun anti403RetryInterceptor(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val chapterRef = original.header(INTERNAL_CHAPTER_REF_HEADER)
        val cleanRequest = if (chapterRef != null) {
            original.newBuilder().removeHeader(INTERNAL_CHAPTER_REF_HEADER).build()
        } else {
            original
        }

        val isImage = cleanRequest.header("Sec-Fetch-Dest") == "image" || chapterRef != null
        val firstResponse = chain.proceed(cleanRequest)

        if (firstResponse.code != 403 && firstResponse.code != 401) {
            return firstResponse
        }

        // Retentativa automática inteligente contra HTTP 403 em imagens (Hotlink com ou sem Referer)
        if (isImage) {
            firstResponse.close()
            val retryWithReferer = cleanRequest.newBuilder()
                .header("User-Agent", resolveUserAgent())
                .header("Referer", chapterRef ?: "$baseUrl/")
                .header("Sec-Fetch-Site", "same-origin")
                .build()

            val secondResponse = chain.proceed(retryWithReferer)
            if (secondResponse.code != 403) {
                return secondResponse
            }

            secondResponse.close()
            val retryFallbackUa = cleanRequest.newBuilder()
                .header("User-Agent", FALLBACK_USER_AGENT)
                .header("Referer", "$baseUrl/")
                .build()
            return chain.proceed(retryFallbackUa)
        }

        // Retentativa automática para páginas HTML caso o User-Agent customizado do app seja barrado pelo WAF
        if (cleanRequest.method == "GET" && cleanRequest.header("User-Agent") != FALLBACK_USER_AGENT) {
            firstResponse.close()
            val retryHtml = cleanRequest.newBuilder()
                .header("User-Agent", FALLBACK_USER_AGENT)
                .header("Referer", "$baseUrl/")
                .build()
            return chain.proceed(retryHtml)
        }

        return firstResponse
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
            parsed.sortedByDescending { it.second }.map { it.first }
        } else {
            parsed.map { it.first }
        }
    }

    private fun parseRankingCards(doc: Document): List<SManga> {
        val seenUrls = HashSet<String>()
        val mangas = mutableListOf<SManga>()

        // Inclui Top 1–3 (.ranking-featured-card) + Top 4–10 (.ranking-grid a.webtoon-card)
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

    private fun extractParentComicUrl(chapterUrl: String): String? {
        val cleanPath = chapterUrl.substringBefore('?').trim('/')
        val segments = cleanPath.split('/')
        return if (segments.size >= 2 && segments[0] == "comics") {
            "$baseUrl/comics/${segments[1]}"
        } else {
            null
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
        private const val INTERNAL_CHAPTER_REF_HEADER = "X-Blackout-Chapter-Ref"

        private const val FALLBACK_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        private const val LOGIN_REQUIRED_MESSAGE =
            "Login necessário para ler capítulos: abra a WebView (ícone 🌐 no topo) para entrar na sua conta, " +
                "ou preencha seu E-mail e Senha nas Configurações da extensão (⚙️)."

        private val PAGE_LIST_REGEX = Regex("""\bS\s*=\s*(\[[\s\S]*?])""")
        private val NON_DIGITS_REGEX = Regex("""[^0-9]""")
        private val LEADING_ZERO_CHAPTER_REGEX = Regex("""/ler/capitulo-0+(\d+(?:[.,-]\d+)?)""")
    }
}
