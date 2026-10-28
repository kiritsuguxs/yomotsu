package eu.kanade.tachiyomi.animeextension.pt.animeshentai

import eu.kanade.tachiyomi.animeextension.pt.animeshentai.extractors.BloggerExtractor
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

class AnimesHentai : ParsedAnimeHttpSource() {

    override val name = "Animes Hentai"
    override val baseUrl = "https://animeshentai.biz"
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
    override fun popularAnimeSelector(): String = "article.item.tvshows"

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/hentai/page/$page/", headers)

    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val link = element.selectFirst("div.poster a, div.data h3 a")!!
        val img = element.selectFirst("div.poster img")!!
        setUrlWithoutDomain(link.attr("href"))
        title = img.attr("alt").ifEmpty { link.text() }.trim()
        thumbnail_url = img.attr("src").ifEmpty { img.attr("data-src") }
    }

    override fun popularAnimeNextPageSelector(): String = "div.pagination a:contains(Proxima)"

    // ============================== Latest Updates =============================
    override fun latestUpdatesSelector(): String = "article.item.episodes, article.item"

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/episodio/page/$page/", headers)

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== Search Anime ===============================
    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return if (query.startsWith(PREFIX_PATH_SEARCH)) {
            val path = query.removePrefix(PREFIX_PATH_SEARCH)
            GET(baseUrl + path, headers)
        } else {
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                if (query.isNotBlank()) {
                    addPathSegment("search")
                    addPathSegment(query)
                    if (page > 1) {
                        addPathSegment("page")
                        addPathSegment(page.toString())
                    }
                    addPathSegment("")
                } else {
                    addPathSegment("hentai")
                    if (page > 1) {
                        addPathSegment("page")
                        addPathSegment(page.toString())
                    }
                    addPathSegment("")
                }
            }.build()

            GET(url.toString(), headers)
        }
    }

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

            genre = sheader.select("div.data div.sgeneros a")
                .eachText()
                .joinToString(", ")

            description = doc.selectFirst("div.wp-content p")?.text()?.trim()
            status = SAnime.COMPLETED
        }
    }

    private fun getRealAnimeDoc(document: Document): Document {
        val menu = document.selectFirst("div.pag_episodes div.item a[href]:has(i.icon-bars-eps)")
        return if (menu != null) {
            val originalUrl = menu.attr("href")
            val req = client.newCall(GET(originalUrl, headers)).execute()
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
    override fun episodeListSelector(): String = "div#seasons ul.episodios > li"

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = getRealAnimeDoc(response.asJsoup())
        val seasons = document.select(episodeListSelector())
        return if (seasons.isEmpty()) {
            listOf(
                SEpisode.create().apply {
                    setUrlWithoutDomain(document.location())
                    name = "Episódio 1"
                    episode_number = 1F
                }
            )
        } else {
            seasons.map { episodeFromElement(it) }.reversed()
        }
    }

    override fun episodeFromElement(element: Element): SEpisode {
        val link = element.selectFirst("div.episodiotitle a, a")!!
        return SEpisode.create().apply {
            setUrlWithoutDomain(link.attr("href"))
            name = link.text().trim()
            episode_number = Regex("(\\d+)$").find(name)?.value?.toFloatOrNull() ?: 1F
        }
    }

    // ============================== Hosters & Videos ===========================
    private val bloggerExtractor by lazy { BloggerExtractor(client) }

    override fun hosterListParse(response: Response): List<Hoster> {
        val document = response.asJsoup()
        val hosterList = mutableListOf<Hoster>()

        val optionLabels = document.select("ul.sourceslist li a").associate {
            val targetId = it.attr("href").removePrefix("#")
            val label = it.text().trim()
            targetId to label
        }

        document.select("div.playex div.play-box-iframe").forEach { box ->
            val boxId = box.id()
            val qualityPrefix = optionLabels[boxId] ?: "Player"
            val iframeSrc = box.selectFirst("iframe")?.attr("src") ?: return@forEach

            if ("blogger.com" in iframeSrc) {
                val videos = bloggerExtractor.videosFromUrl(iframeSrc, headers, qualityPrefix)
                if (videos.isNotEmpty()) {
                    hosterList.add(
                        Hoster(
                            hosterUrl = iframeSrc,
                            hosterName = "Blogger ($qualityPrefix)",
                            videoList = videos,
                            internalData = "",
                            lazy = false,
                        )
                    )
                }
            }
        }

        return hosterList
    }

    override fun videoListParse(response: Response, hoster: Hoster): List<Video> {
        return hoster.videoList.ifEmpty {
            bloggerExtractor.videosFromUrl(hoster.hosterUrl, headers, hoster.hosterName)
        }
    }

    override fun videoUrlParse(response: Response): String = response.request.url.toString()
}
