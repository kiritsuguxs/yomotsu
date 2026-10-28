package eu.kanade.tachiyomi.extension.pt.baozinho

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
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
    }

    // ============================== Popular Manga ==============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ranking", headers)

    override fun popularMangaSelector(): String = "a.portrait-card"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.attr("href")
        setUrlWithoutDomain(link)

        val titleEl = element.selectFirst("span.portrait-card-titulo")
        val img = element.selectFirst("img")

        val rawTitle = titleEl?.text()?.ifBlank { null }
            ?: img?.attr("alt")
            ?: ""
        title = rawTitle.trim()

        val srcSet = img?.attr("srcSet")
        val highRes = srcSet?.split(",")?.lastOrNull()?.trim()?.split(" ")?.firstOrNull()
        thumbnail_url = highRes ?: img?.attr("src")?.ifEmpty { img.attr("data-src") }
    }

    override fun popularMangaNextPageSelector(): String? = null

    // ============================== Latest Updates =============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/atualizacoes", headers)

    override fun latestUpdatesSelector(): String = "a.atualizacoes-item"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        val rawHref = element.attr("href")
        val cleanHref = rawHref.substringBefore("/capitulo-")
        setUrlWithoutDomain(cleanHref)

        val img = element.selectFirst("img")
        val titleText = element.selectFirst("span.atualizacoes-nome, span.atualizacoes-titulo")?.text()
            ?: img?.attr("alt")
            ?: cleanHref.substringAfterLast("/").replace("-", " ")
        title = titleText.trim()

        val srcSet = img?.attr("srcSet")
        val highRes = srcSet?.split(",")?.lastOrNull()?.trim()?.split(" ")?.firstOrNull()
        thumbnail_url = highRes ?: img?.attr("src")?.ifEmpty { img.attr("data-src") }
    }

    override fun latestUpdatesNextPageSelector(): String? = null

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
    override fun chapterListSelector(): String = "a.obra-ep-link, a[href*='/capitulo-']"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()

        document.select(chapterListSelector()).forEach { el ->
            val href = el.attr("href")
            if (href.contains("/capitulo-")) {
                chapters.add(chapterFromElement(el))
            }
        }

        return chapters.distinctBy { it.url }
    }

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val href = element.attr("href")
        setUrlWithoutDomain(href)

        val epMatch = Regex("""capitulo-(\d+)""").find(href)
        val epNum = epMatch?.groupValues?.get(1) ?: "1"
        chapter_number = epNum.toFloatOrNull() ?: 1F

        val epTitle = element.selectFirst("span.obra-ep-title")?.text()?.replace("<!-- -->", "")?.trim()
        name = if (!epTitle.isNullOrBlank()) {
            if (epTitle.startsWith("Ep.", ignoreCase = true)) {
                epTitle.replace("Ep.", "Capítulo ").trim()
            } else {
                "Capítulo $epNum - $epTitle"
            }
        } else {
            "Capítulo $epNum"
        }

        element.selectFirst("span.obra-ep-date, time.atualizacoes-hora")?.let { dateEl ->
            val dateStr = dateEl.attr("dateTime").ifEmpty { dateEl.text() }
            date_upload = parseChapterDate(dateStr)
        }
    }

    private fun parseChapterDate(dateStr: String): Long {
        return try {
            if (dateStr.contains("T")) {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                format.parse(dateStr.substringBefore("."))?.time ?: 0L
            } else {
                0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    // ============================== Page List ==================================
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
