package eu.kanade.tachiyomi.extension.pt.blitztoons

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class BlitzToons : ParsedHttpSource() {

    override val name = "Blitz Toons"
    override val baseUrl = "https://blitztoons.com"
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
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/obras?ordem=populares&p=$page", headers)

    override fun popularMangaSelector(): String = "div.grid-cards article.card"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a.card-cover")?.attr("href")
            ?: element.selectFirst("h3.card-title a")?.attr("href")
            ?: ""
        setUrlWithoutDomain(link)

        title = element.selectFirst("h3.card-title a")?.text()?.trim()
            ?: element.selectFirst("a.card-cover img")?.attr("alt")?.removePrefix("Capa de ")?.trim()
            ?: ""

        thumbnail_url = element.selectFirst("a.card-cover img")?.absUrl("src")
    }

    override fun popularMangaNextPageSelector(): String = "nav.pager a:contains(Próxima)"

    // ============================== Latest Updates =============================
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/obras?ordem=recentes&p=$page", headers)

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()

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
        }

        val url = "$baseUrl/obras".toHttpUrl().newBuilder()
        url.addQueryParameter("p", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("q", query.trim())
        }

        filters.forEach { filter ->
            when (filter) {
                is CategoryFilter -> if (filter.toSlug().isNotBlank()) url.addQueryParameter("tipo", filter.toSlug())
                is SortFilter -> if (filter.toSlug().isNotBlank()) url.addQueryParameter("ordem", filter.toSlug())
                is StatusFilter -> if (filter.toSlug().isNotBlank()) url.addQueryParameter("status", filter.toSlug())
                is GenreFilter -> if (filter.toSlug().isNotBlank()) url.addQueryParameter("genero", filter.toSlug())
                else -> {}
            }
        }

        return GET(url.build().toString(), headers)
    }

    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String = popularMangaNextPageSelector()

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        if (document.selectFirst("div.grid-cards") == null && document.selectFirst("section.sbanner") != null) {
            val manga = mangaDetailsParse(document)
            return MangasPage(listOf(manga), false)
        }

        val mangas = document.select(searchMangaSelector()).map { searchMangaFromElement(it) }
        val hasNextPage = searchMangaNextPageSelector()?.let { selector ->
            document.selectFirst(selector) != null
        } ?: false

        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        CategoryFilter(),
        StatusFilter(),
        GenreFilter(),
    )

    // ============================== Manga Details ==============================
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        setUrlWithoutDomain(document.location())

        val rawTitle = document.selectFirst("div.sbanner-txt h1")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore("—")?.trim()
            ?: ""
        title = rawTitle

        thumbnail_url = document.selectFirst("img.sbanner-cover")?.absUrl("src")
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")

        val syn = document.selectFirst("div.syn")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        val altName = document.selectFirst("p.salt")?.text()?.trim()
        description = listOfNotNull(
            syn,
            if (!altName.isNullOrBlank()) "Nome alternativo: $altName" else null
        ).joinToString("\n\n")

        val genres = document.select("div.sgenres span, div.sgenres a").eachText().filter { it.isNotBlank() }
        val typePill = document.selectFirst("span.pill")?.text()?.trim()
        val allGenres = (listOfNotNull(typePill) + genres).distinct()
        genre = allGenres.joinToString(", ")

        val statusText = document.select("dl.smeta div").firstOrNull {
            it.selectFirst("dt")?.text()?.contains("Status", true) == true
        }?.selectFirst("dd")?.text()?.lowercase() ?: ""

        status = when {
            "lançamento" in statusText || "lancamento" in statusText || "ongoing" in statusText -> SManga.ONGOING
            "completo" in statusText || "finalizado" in statusText || "completed" in statusText -> SManga.COMPLETED
            "hiato" in statusText || "hiatus" in statusText -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapter List ===============================
    override fun chapterListSelector(): String = "li.chap a.chap-alvo"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select(chapterListSelector()).map { chapterFromElement(it) }
        return chapters.distinctBy { it.url }.sortedByDescending { it.chapter_number }
    }

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val href = element.attr("href")
        setUrlWithoutDomain(href)

        val nameText = element.selectFirst("span.chap-nome")?.text()?.trim()
        name = if (!nameText.isNullOrBlank()) nameText else href.substringAfterLast("/").let { "Capítulo $it" }

        val liEl = element.closest("li.chap")
        val numFromLi = liEl?.attr("data-num")?.toFloatOrNull()
        if (numFromLi != null) {
            chapter_number = numFromLi
        } else {
            val numMatch = Regex("""(?:Capítulo|Cap\.|#|\b)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(name)
                ?: Regex("""/(\d+(?:\.\d+)?)$""").find(href)
            chapter_number = numMatch?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
        }

        element.selectFirst("time")?.let { timeEl ->
            val dateStr = timeEl.attr("datetime").ifEmpty { timeEl.attr("dateTime") }
            date_upload = parseChapterDate(dateStr)
        }
    }

    private fun parseChapterDate(dateStr: String): Long {
        return try {
            if (dateStr.contains("T")) {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
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

    // ============================== Page List ==================================
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select("div.pages img.page, div.pages img").forEachIndexed { index, img ->
            val url = img.attr("abs:src").ifEmpty {
                img.attr("src").ifEmpty {
                    img.attr("data-src")
                }
            }
            if (url.isNotBlank() && !url.contains("data:image")) {
                pages.add(Page(index, "", url))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = ""
}
