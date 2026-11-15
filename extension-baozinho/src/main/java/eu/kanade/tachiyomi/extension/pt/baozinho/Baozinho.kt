package eu.kanade.tachiyomi.extension.pt.baozinho

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class Baozinho : ParsedHttpSource() {

    override val name = "Baozinho"
    override val baseUrl = "https://baozinho.com"
    override val lang = "pt-BR"
    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
        .add("Referer", "$baseUrl/")

    companion object {
        const val PREFIX_PATH_SEARCH = "path:"
        private const val API_BASE_URL = "https://api.baozinho.com"
        private const val CDN_BASE_URL = "https://cdn.baozinho.com"
    }

    private val slugToIdMap = mutableMapOf<String, Int>()

    private fun getObraId(slug: String): Int? {
        if (slug.isBlank()) return null
        slugToIdMap[slug]?.let { return it }

        try {
            val req = GET("$API_BASE_URL/obras/slug/$slug", headers)
            client.newCall(req).execute().use { res ->
                val body = res.body?.string()
                if (res.isSuccessful && !body.isNullOrBlank() && body.trimStart().startsWith("{")) {
                    val itemId = JSONObject(body).optInt("id", -1)
                    if (itemId != -1) {
                        slugToIdMap[slug] = itemId
                        return itemId
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            val req = GET("$API_BASE_URL/obras?limit=200", headers)
            client.newCall(req).execute().use { res ->
                val body = res.body?.string() ?: return null
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return null
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val itemSlug = item.optString("slug")
                    val itemId = item.optInt("id", -1)
                    if (itemSlug.isNotBlank() && itemId != -1) {
                        slugToIdMap[itemSlug] = itemId
                    }
                }
            }
        } catch (_: Exception) {}
        return slugToIdMap[slug]
    }

    // ============================== Popular Manga ==============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/obras", headers)

    override fun popularMangaSelector(): String = "a.portrait-card, a.fileira-link"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.attr("href")
        setUrlWithoutDomain(link)

        val titleEl = element.selectFirst("span.portrait-card-titulo, span.fileira-rank-titulo")
        val img = element.selectFirst("img")

        val rawTitle = titleEl?.text()?.ifBlank { null }
            ?: element.attr("aria-label").substringAfter("—").trim().ifBlank { null }
            ?: img?.attr("alt")?.ifBlank { null }
            ?: link.substringAfterLast("/").replace("-", " ")
        title = rawTitle.trim()

        val srcSet = img?.attr("srcSet")?.ifBlank { img.attr("srcset") }
        val highRes = srcSet?.split(",")?.lastOrNull()?.trim()?.split(" ")?.firstOrNull()
        thumbnail_url = highRes ?: img?.attr("src")?.ifEmpty { img.attr("data-src") }
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector())
            .distinctBy { it.attr("href") }
            .map { popularMangaFromElement(it) }
        return MangasPage(mangas, false)
    }

    // ============================== Latest Updates =============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/atualizacoes", headers)

    override fun latestUpdatesSelector(): String = "a.atualizacoes-item"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        val rawHref = element.attr("href")
        val cleanHref = rawHref.substringBefore("/capitulo-")
        setUrlWithoutDomain(cleanHref)

        val img = element.selectFirst("img")
        val titleText = element.selectFirst("span.atualizacoes-obra, span.atualizacoes-nome, span.atualizacoes-titulo")
            ?.text()
            ?.ifBlank { null }
            ?: img?.attr("alt")?.ifBlank { null }
            ?: cleanHref.substringAfterLast("/").replace("-", " ")
        title = titleText.trim()

        val srcSet = img?.attr("srcSet")?.ifBlank { img.attr("srcset") }
        val highRes = srcSet?.split(",")?.lastOrNull()?.trim()?.split(" ")?.firstOrNull()
        thumbnail_url = highRes ?: img?.attr("src")?.ifEmpty { img.attr("data-src") }
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector())
            .distinctBy { it.attr("href").substringBefore("/capitulo-") }
            .map { latestUpdatesFromElement(it) }
        return MangasPage(mangas, false)
    }

    // ============================== Search Manga ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val trimmed = query.trim()
            if (trimmed.startsWith(PREFIX_PATH_SEARCH)) {
                return GET(baseUrl + trimmed.removePrefix(PREFIX_PATH_SEARCH), headers)
            }
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                return GET(trimmed, headers)
            }
            if (trimmed.startsWith("/")) {
                return GET(baseUrl + trimmed, headers)
            }
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
            return GET("$baseUrl/obras?q=$encodedQuery", headers)
        }

        val genre = filters.filterIsInstance<GenreFilter>().firstOrNull()?.toSlug() ?: ""
        return if (genre.isNotBlank()) {
            GET("$baseUrl/busca?tagSlugs=$genre", headers)
        } else {
            GET("$baseUrl/obras", headers)
        }
    }

    override fun searchMangaSelector(): String = "a.portrait-card, a.atualizacoes-item"

    override fun searchMangaFromElement(element: Element): SManga {
        return if (element.hasClass("atualizacoes-item")) {
            latestUpdatesFromElement(element)
        } else {
            popularMangaFromElement(element)
        }
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector())
            .distinctBy { it.attr("href").substringBefore("/capitulo-") }
            .map { searchMangaFromElement(it) }
        return MangasPage(mangas, false)
    }

    override fun getFilterList(): FilterList = FilterList(
        GenreFilter(),
    )

    // ============================== Manga Details ==============================
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        setUrlWithoutDomain(document.location())

        val rawTitle = document.selectFirst("h1")?.text()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
            ?: ""
        title = rawTitle.replace(" | Baozinho", "").trim()

        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: document.selectFirst("span.portrait-card-clip img")?.attr("src")

        description = document.selectFirst("meta[property=og:description]")?.attr("content")
            ?: document.selectFirst("p.obra-sinopse")?.text()?.trim()

        genre = document.select("ul.obra-mobile-tags li, ul.obra-tags li, ul[aria-label=Gêneros] li")
            .eachText()
            .joinToString(", ")

        document.selectFirst("p:contains(·)")?.text()?.let { metaText ->
            val parts = metaText.split("·")
            if (parts.size >= 2) {
                author = parts[1].trim()
                artist = parts[1].trim()
            }
        }

        val statusText = document.selectFirst("span.obra-mobile-status, span.obra-ficha-valor")?.text()?.lowercase() ?: ""
        status = when {
            "lançamento" in statusText || "lancamento" in statusText -> SManga.ONGOING
            "completo" in statusText || "finalizado" in statusText -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapter List ===============================
    override fun chapterListSelector(): String = "ul.obra-ep-list li.obra-ep-item a.obra-ep-link"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val url = response.request.url.toString()
        val slug = url.substringAfter("/obras/").substringBefore("/").substringBefore("?")

        // 1. Fetch complete and ordered chapter list directly from API
        val obraId = getObraId(slug) ?: document.selectFirst("ul[id^=lista-]")?.id()?.removePrefix("lista-")?.toIntOrNull()
        if (obraId != null) {
            try {
                val apiReq = GET("$API_BASE_URL/capitulos/obra/$obraId", headers)
                val apiBody = client.newCall(apiReq).execute().use { it.body?.string() }
                if (!apiBody.isNullOrBlank() && apiBody.trimStart().startsWith("[")) {
                    val array = JSONArray(apiBody)
                    val chapters = mutableListOf<SChapter>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val capId = obj.optInt("id", -1)
                        val numDouble = obj.optDouble("numero", -1.0)
                        if (numDouble < 0.0) continue
                        val numStr = formatChapterNumber(numDouble)
                        val nameStr = obj.optString("nome").ifBlank { "Capítulo $numStr" }
                        val dateStr = obj.optString("lancadoEm")
                        chapters.add(
                            SChapter.create().apply {
                                this.url = if (capId > 0) {
                                    "/obras/$slug/capitulo-$numStr#$capId"
                                } else {
                                    "/obras/$slug/capitulo-$numStr"
                                }
                                this.name = nameStr
                                this.chapter_number = numDouble.toFloat()
                                this.date_upload = parseChapterDate(dateStr)
                            }
                        )
                    }
                    if (chapters.isNotEmpty()) {
                        return chapters.sortedByDescending { it.chapter_number }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Fallback to HTML elements sorted in descending order
        val chapters = mutableListOf<SChapter>()
        document.select(chapterListSelector()).forEach { el ->
            val href = el.attr("href")
            if (href.contains("/capitulo-")) {
                chapters.add(chapterFromElement(el))
            }
        }

        return chapters.distinctBy { it.url }.sortedByDescending { it.chapter_number }
    }

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val href = element.attr("href")
        setUrlWithoutDomain(href)

        val epMatch = Regex("""capitulo-(\d+(?:-\d+)?)""").find(href)
        val epNumStr = epMatch?.groupValues?.get(1)?.replace("-", ".") ?: "1"
        chapter_number = epNumStr.toFloatOrNull() ?: 1f

        val epTitle = element.selectFirst("span.obra-ep-title")?.text()?.replace("<!-- -->", "")?.trim()
        name = if (!epTitle.isNullOrBlank()) {
            if (epTitle.startsWith("Ep.", ignoreCase = true)) {
                epTitle.replace("Ep.", "Capítulo ").trim()
            } else {
                "Capítulo $epNumStr - $epTitle"
            }
        } else {
            "Capítulo $epNumStr"
        }

        element.selectFirst("span.obra-ep-date, time.atualizacoes-hora")?.let { dateEl ->
            val dateStr = dateEl.attr("dateTime").ifEmpty { dateEl.text() }
            date_upload = parseChapterDate(dateStr)
        }
    }

    private fun formatChapterNumber(num: Double): String {
        return if (num % 1.0 == 0.0) {
            num.toInt().toString()
        } else {
            num.toString().replace('.', '-')
        }
    }

    private fun parseChapterDate(dateStr: String): Long {
        return try {
            if (dateStr.contains("T")) {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                format.parse(dateStr.substringBefore("."))?.time ?: 0L
            } else {
                0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url.substringBefore("#")

    // ============================== Page List ==================================
    override fun pageListRequest(chapter: SChapter): Request {
        val capId = chapter.url.substringAfter("#", "").toIntOrNull()
        return if (capId != null && capId > 0) {
            GET("$API_BASE_URL/capitulos/$capId", headers)
        } else {
            GET(baseUrl + chapter.url.substringBefore("#"), headers)
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val reqUrl = response.request.url.toString()
        val body = response.use { it.body?.string().orEmpty() }

        // 1. Direct JSON response from api.baozinho.com/capitulos/{id}
        if (response.request.url.host == "api.baozinho.com" || body.trimStart().startsWith("{")) {
            val pages = parsePagesFromChapterJson(body)
            if (pages.isNotEmpty()) {
                return pages
            }
        }

        // 2. HTML fallback (e.g. legacy saved chapters whose URL is /obras/{slug}/capitulo-{num} without #{id})
        val capId = resolveChapterIdFromHtmlOrApi(body, reqUrl)
        if (capId != null) {
            try {
                val apiReq = GET("$API_BASE_URL/capitulos/$capId", headers)
                val apiBody = client.newCall(apiReq).execute().use { it.body?.string().orEmpty() }
                val pages = parsePagesFromChapterJson(apiBody)
                if (pages.isNotEmpty()) {
                    return pages
                }
            } catch (_: Exception) {}
        }

        // 3. Last-resort HTML scan
        return pageListParse(Jsoup.parse(body, reqUrl))
    }

    private fun parsePagesFromChapterJson(body: String): List<Page> {
        if (body.isBlank()) return emptyList()
        return try {
            val json = JSONObject(body)
            val paginas = json.optJSONArray("paginas") ?: return emptyList()
            val indexed = ArrayList<Pair<Int, String>>(paginas.length())
            for (i in 0 until paginas.length()) {
                val pageObj = paginas.optJSONObject(i) ?: continue
                val rawUrl = pageObj.optString("url").trim()
                if (rawUrl.isBlank()) continue
                val fullUrl = when {
                    rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> rawUrl
                    rawUrl.startsWith("/") -> "$CDN_BASE_URL$rawUrl"
                    else -> "$CDN_BASE_URL/$rawUrl"
                }
                val order = pageObj.optInt("ordem", i)
                indexed.add(order to fullUrl)
            }
            indexed.sortedBy { it.first }
                .mapIndexed { index, (_, imgUrl) -> Page(index, "", imgUrl) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun resolveChapterIdFromHtmlOrApi(html: String, reqUrl: String): Int? {
        val slug = reqUrl.substringAfter("/obras/").substringBefore("/").substringBefore("?").substringBefore("#")
        val capNumMatch = Regex("""capitulo-(\d+(?:-\d+)?)""").find(reqUrl)
        val targetNum = capNumMatch?.groupValues?.get(1)?.replace("-", ".")?.toDoubleOrNull()

        if (targetNum != null && html.isNotEmpty()) {
            val unescaped = html.replace("\\\"", "\"")
            val targetIntOrDecimal = if (targetNum % 1.0 == 0.0) {
                targetNum.toInt().toString()
            } else {
                targetNum.toString()
            }
            val htmlIdRegex = Regex(
                """"id":(\d+),"uuid":"[^"]+","obrId":\d+,"nome":"[^"]*","descricao":[^}]*"numero":$targetIntOrDecimal(?:\.0)?\b""",
            )
            htmlIdRegex.find(unescaped)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        }

        if (slug.isNotBlank() && targetNum != null) {
            val obraId = getObraId(slug)
            if (obraId != null) {
                try {
                    val apiReq = GET("$API_BASE_URL/capitulos/obra/$obraId", headers)
                    val apiBody = client.newCall(apiReq).execute().use { it.body?.string().orEmpty() }
                    if (apiBody.trimStart().startsWith("[")) {
                        val array = JSONArray(apiBody)
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            if (obj.optDouble("numero", -1.0) == targetNum) {
                                val id = obj.optInt("id", -1)
                                if (id > 0) return id
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        return null
    }

    override fun pageListParse(document: Document): List<Page> {
        val html = document.html()
        val urlRegex = Regex("""https://cdn\.baozinho\.com/obras/\d+/capitulos/\d+/[a-f0-9]+\.(?:webp|png|jpg|jpeg)""")
        val urls = urlRegex.findAll(html).map { it.value }.distinct().toList()

        if (urls.isNotEmpty()) {
            return urls.mapIndexed { index, imgUrl ->
                Page(index, "", imgUrl)
            }
        }

        return document.select("img[src*='cdn.baozinho.com']").mapIndexed { index, img ->
            Page(index, "", img.attr("src"))
        }
    }

    override fun imageUrlParse(document: Document): String = ""
}
