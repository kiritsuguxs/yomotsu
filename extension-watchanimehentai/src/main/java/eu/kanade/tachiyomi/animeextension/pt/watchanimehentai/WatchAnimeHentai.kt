package eu.kanade.tachiyomi.animeextension.pt.watchanimehentai

import eu.kanade.tachiyomi.animeextension.pt.watchanimehentai.extractors.BloggerExtractor
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

class WatchAnimeHentai : ParsedAnimeHttpSource() {

    override val name = "Watch Anime Hentai"
    override val baseUrl = "https://www.watchanimehentai.com"
    override val lang = "pt-BR"
    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .add("Referer", "$baseUrl/")

    companion object {
        const val PREFIX_PATH_SEARCH = "path:"
    }

    // ============================== Popular Anime ==============================
    override fun popularAnimeSelector(): String = "article.item.tvshows, article.item"

    override fun popularAnimeRequest(page: Int): Request {
        val url = if (page <= 1) {
            "$baseUrl/pt/lista-de-hentai/"
        } else {
            "$baseUrl/pt/lista-de-hentai/$page/"
        }
        return GET(url, headers)
    }

    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val link = element.selectFirst("div.poster a, div.data h3 a, a")!!
        val img = element.selectFirst("div.poster img, img")!!
        setUrlWithoutDomain(link.attr("href"))
        title = img.attr("alt").ifEmpty { img.attr("title") }.ifEmpty { link.text() }.trim()
        thumbnail_url = img.attr("src").ifEmpty { img.attr("data-src") }
    }

    override fun popularAnimeNextPageSelector(): String = "div.paginacao a:contains(»)"

    // ============================== Latest Updates =============================
    override fun latestUpdatesSelector(): String = "article.item.se.episodes, article.item"

    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page <= 1) {
            "$baseUrl/pt/ultimos-episodios-adicionados/"
        } else {
            "$baseUrl/pt/ultimos-episodios-adicionados/$page/"
        }
        return GET(url, headers)
    }

    override fun latestUpdatesFromElement(element: Element): SAnime = SAnime.create().apply {
        val link = element.selectFirst("a[href]")!!
        val img = element.selectFirst("div.poster img, img")!!
        setUrlWithoutDomain(link.attr("href"))
        title = img.attr("alt").ifEmpty { img.attr("title") }.ifEmpty { link.text() }.trim()
        thumbnail_url = img.attr("src").ifEmpty { img.attr("data-src") }
    }

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== Search Anime ===============================
    override fun searchAnimeSelector(): String = "article.search-result, article.item.tvshows, article.item"

    override fun searchAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val link = element.selectFirst("h2.search-result-title a, a.search-result-poster, div.poster a, div.data h3 a, a")!!
        val img = element.selectFirst("img")!!
        setUrlWithoutDomain(link.attr("href"))
        title = img.attr("alt").ifEmpty { img.attr("title") }.ifEmpty { link.text() }.trim()
        thumbnail_url = img.attr("src").ifEmpty { img.attr("data-src") }
    }

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        if (query.startsWith(PREFIX_PATH_SEARCH)) {
            val path = query.removePrefix(PREFIX_PATH_SEARCH)
            val fullUrl = if (path.startsWith("http")) path else baseUrl + path
            return GET(fullUrl, headers)
        }

        if (query.isNotBlank()) {
            val cleanQuery = query.trim().replace(" ", "+")
            val url = if (page <= 1) {
                "$baseUrl/pt/busca/$cleanQuery/"
            } else {
                "$baseUrl/pt/busca/$cleanQuery/?pagination=$page"
            }
            return GET(url, headers)
        }

        val genre = filters.filterIsInstance<GeneroFilter>().firstOrNull()?.toSlug() ?: ""
        if (genre.isNotBlank()) {
            val url = if (page <= 1) {
                "$baseUrl/pt/genero/$genre/"
            } else {
                "$baseUrl/pt/genero/$genre/$page/"
            }
            return GET(url, headers)
        }

        return popularAnimeRequest(page)
    }

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(GeneroFilter())

    // ============================== Anime Details ==============================
    override fun animeDetailsParse(document: Document): SAnime {
        val doc = getRealAnimeDoc(document)
        val sheader = doc.selectFirst("div.sheader") ?: doc

        return SAnime.create().apply {
            setUrlWithoutDomain(doc.location())

            sheader.selectFirst("div.poster img")?.let { img ->
                thumbnail_url = img.attr("src").ifEmpty { img.attr("data-src") }
                title = img.attr("alt").ifEmpty {
                    sheader.selectFirst("div.data h1")?.text() ?: ""
                }.replace("Todos os Episodios Online", "").trim()
            }

            if (title.isBlank()) {
                title = sheader.selectFirst("div.data h1")?.text()?.trim() ?: ""
            }

            genre = sheader.select("div.data div.sgeneros a, div.sgeneros a")
                .eachText()
                .distinct()
                .joinToString(", ")

            description = doc.selectFirst("div.wp-content p")?.text()?.trim()

            val studio = doc.selectFirst("div.custom_fields:has(b:contains(Produtora)) div.sgeneros a")?.text()?.trim()
            if (!studio.isNullOrBlank()) {
                author = studio
                artist = studio
            }

            status = SAnime.COMPLETED
        }
    }

    private fun getRealAnimeDoc(document: Document): Document {
        val menu = document.selectFirst("div.pag_episodes div.item a[href*='/info/']")
        return if (menu != null) {
            val originalUrl = menu.attr("href")
            val fullUrl = if (originalUrl.startsWith("http")) originalUrl else "$baseUrl$originalUrl"
            val req = client.newCall(GET(fullUrl, headers)).execute()
            req.asJsoup()
        } else {
            document
        }
    }

    // ============================== Seasons ====================================
    override fun seasonListSelector(): String = "div#seasons"

    override fun seasonFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun seasonListParse(response: Response): List<SAnime> = emptyList()

    // ============================== Episodes ===================================
    override fun episodeListSelector(): String = "article.item.se.episodes, div#seasons ul.episodios > li"

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = getRealAnimeDoc(response.asJsoup())
        val episodes = document.select(episodeListSelector())
        return if (episodes.isEmpty()) {
            listOf(
                SEpisode.create().apply {
                    setUrlWithoutDomain(document.location())
                    name = "Episódio 1"
                    episode_number = 1F
                }
            )
        } else {
            episodes.map { episodeFromElement(it) }.reversed()
        }
    }

    override fun episodeFromElement(element: Element): SEpisode {
        val link = element.selectFirst("a[href]")!!
        val epTitle = element.selectFirst("span.c, div.data h3, div.episodiotitle a, a")?.text()?.trim() ?: "Episódio"
        val epNumber = Regex("""(?i)epis[oó]dio\s*(\d+)""").find(epTitle)?.groupValues?.getOrNull(1)?.toFloatOrNull()
            ?: Regex("""(\d+)""").find(epTitle)?.value?.toFloatOrNull()
            ?: 1F

        return SEpisode.create().apply {
            setUrlWithoutDomain(link.attr("href"))
            name = epTitle
            episode_number = epNumber
        }
    }

    // ============================== Hosters & Videos ===========================
    private val bloggerExtractor by lazy { BloggerExtractor(client) }

    override fun hosterListParse(response: Response): List<Hoster> {
        val document = response.asJsoup()
        val hosters = mutableListOf<Hoster>()

        val optionLabels = document.select("ul.sourceslist li a").associate {
            val targetId = it.attr("href").removePrefix("#")
            val label = it.text().trim()
            targetId to label
        }

        document.select("div.playex div.play-box-iframe").forEach { box ->
            val boxId = box.id()
            val label = optionLabels[boxId] ?: "Player"
            val iframe = box.selectFirst("iframe") ?: return@forEach
            val iframeSrc = iframe.attr("src").trim()
            val dataInfo = iframe.attr("data-infoplayer").trim()
            val displayName = if (dataInfo.isNotBlank()) dataInfo else label

            if (iframeSrc.contains("padrao=") || iframeSrc.contains("/players/p2/")) {
                val token = iframeSrc.substringAfter("padrao=", "")
                if (token.isNotBlank()) {
                    val resolveUrl = "$baseUrl/players/p2/p2.php?id=$token"
                    val videoHeaders = headersBuilder().set("Referer", "$baseUrl/").build()
                    val finalUrl = resolveRedirectUrl(resolveUrl, videoHeaders)
                    val video = Video(finalUrl, "Watch Anime Hentai ($displayName - MP4)", finalUrl, videoHeaders)
                    hosters.add(
                        Hoster(
                            hosterUrl = finalUrl,
                            hosterName = "Watch Anime Hentai - $displayName",
                            videoList = listOf(video),
                            internalData = "",
                            lazy = false,
                        )
                    )
                }
            } else if (iframeSrc.contains("hentaitube.online") || iframeSrc.contains("blogger.com")) {
                val bloggerVideos = extractBloggerVideos(iframeSrc, displayName)
                if (bloggerVideos.isNotEmpty()) {
                    hosters.add(
                        Hoster(
                            hosterUrl = iframeSrc,
                            hosterName = "Blogger ($displayName)",
                            videoList = bloggerVideos,
                            internalData = "",
                            lazy = false,
                        )
                    )
                }
            }
        }

        if (hosters.isEmpty()) {
            document.select("iframe[src]").forEach { iframe ->
                val src = iframe.attr("src").trim()
                if (src.contains("padrao=")) {
                    val token = src.substringAfter("padrao=", "")
                    if (token.isNotBlank()) {
                        val resolveUrl = "$baseUrl/players/p2/p2.php?id=$token"
                        val videoHeaders = headersBuilder().set("Referer", "$baseUrl/").build()
                        val finalUrl = resolveRedirectUrl(resolveUrl, videoHeaders)
                        val video = Video(finalUrl, "Watch Anime Hentai (MP4)", finalUrl, videoHeaders)
                        hosters.add(
                            Hoster(
                                hosterUrl = finalUrl,
                                hosterName = "Watch Anime Hentai",
                                videoList = listOf(video),
                                internalData = "",
                                lazy = false,
                            )
                        )
                    }
                }
            }
        }

        return hosters
    }

    private fun resolveRedirectUrl(url: String, requestHeaders: Headers): String {
        return try {
            val headReq = Request.Builder()
                .url(url)
                .head()
                .headers(requestHeaders)
                .build()
            val noRedirectClient = client.newBuilder().followRedirects(false).build()
            val headRes = noRedirectClient.newCall(headReq).execute()
            val location = headRes.header("Location")
            headRes.close()
            if (!location.isNullOrBlank()) location else url
        } catch (_: Exception) {
            url
        }
    }

    private fun extractBloggerVideos(url: String, prefix: String): List<Video> {
        return try {
            val targetUrl = if (url.contains("hentaitube.online")) {
                val res = client.newCall(GET(url, headersBuilder().set("Referer", "$baseUrl/").build())).execute()
                val doc = res.asJsoup()
                doc.selectFirst("iframe[src*=blogger.com]")?.attr("src") ?: url
            } else {
                url
            }
            if (targetUrl.contains("blogger.com")) {
                bloggerExtractor.videosFromUrl(targetUrl, headers, prefix)
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun videoListParse(response: Response, hoster: Hoster): List<Video> {
        return hoster.videoList.orEmpty()
    }

    override fun videoUrlParse(response: Response): String = response.request.url.toString()
}
