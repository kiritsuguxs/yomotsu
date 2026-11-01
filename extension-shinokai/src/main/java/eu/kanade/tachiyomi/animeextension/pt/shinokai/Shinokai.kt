package eu.kanade.tachiyomi.animeextension.pt.shinokai

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class Shinokai : AnimeHttpSource() {

    override val name = "ShinoKai"

    override val baseUrl = "https://api-prod.shinokai.online"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client

    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)
        .add("Accept", "application/json, text/plain, */*")
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile; ShinokaiApp) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")

    // ============================== Populares ==============================

    override fun popularAnimeRequest(page: Int): Request {
        val url = "$baseUrl/anime/popular".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "24")
            .build()
        return GET(url.toString(), headers)
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        val body = response.body?.string().orEmpty()
        return try {
            val pageDto = json.decodeFromString<AnimePageDto>(body)
            val animeList = pageDto.data.map { item ->
                SAnime.create().apply {
                    title = item.title
                    thumbnail_url = item.getCover()
                    url = "/anime/${item.id}"
                }
            }
            AnimesPage(animeList, pageDto.hasNextPage)
        } catch (_: Exception) {
            val items = json.decodeFromString<List<AnimeItemDto>>(body)
            val animeList = items.map { item ->
                SAnime.create().apply {
                    title = item.title
                    thumbnail_url = item.getCover()
                    url = "/anime/${item.id}"
                }
            }
            AnimesPage(animeList, hasNextPage = items.size >= 20)
        }
    }

    // ============================== Lançamentos ==============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/anime/recentes".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "24")
            .build()
        return GET(url.toString(), headers)
    }

    override fun latestUpdatesParse(response: Response): AnimesPage = popularAnimeParse(response)

    // =============================== Pesquisa ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = "$baseUrl/anime/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "24")

        if (query.isNotBlank()) {
            url.addQueryParameter("q", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is ShinokaiFilters.AudioFilter -> {
                    if (filter.selected.isNotBlank()) {
                        url.addQueryParameter("audio", filter.selected)
                    }
                }
                is ShinokaiFilters.GenreFilter -> {
                    if (filter.selected.isNotBlank()) {
                        url.addQueryParameter("genre", filter.selected)
                    }
                }
                is ShinokaiFilters.OrderFilter -> {
                    if (filter.selected.isNotBlank()) {
                        url.addQueryParameter("sort", filter.selected)
                    }
                }
                else -> {}
            }
        }

        return GET(url.build().toString(), headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage = popularAnimeParse(response)

    // =============================== Detalhes ===============================

    override fun animeDetailsRequest(anime: SAnime): Request {
        return GET("$baseUrl${anime.url}", headers)
    }

    override fun animeDetailsParse(response: Response): SAnime {
        val body = response.body?.string().orEmpty()
        val detail = json.decodeFromString<AnimeDetailDto>(body)
        return SAnime.create().apply {
            title = detail.title
            thumbnail_url = detail.posterUrl ?: detail.thumbnail
            description = buildString {
                detail.synopsis?.takeIf { it.isNotBlank() }?.let { append(it).append("\n\n") }
                detail.releaseYear?.takeIf { it.isNotBlank() }?.let { append("Ano: ").append(it).append("\n") }
                detail.audioType?.takeIf { it.isNotBlank() }?.let { append("Áudio: ").append(it).append("\n") }
            }.trim()
            genre = detail.genres.joinToString(", ")
            status = when (detail.status?.lowercase()) {
                "completo", "completed", "finalizado" -> SAnime.COMPLETED
                "lancamento", "em lançamento", "ongoing" -> SAnime.ONGOING
                else -> SAnime.UNKNOWN
            }
            author = detail.author ?: detail.artist
            artist = detail.artist
        }
    }

    // =========================== Seasons (Temporadas) ===========================

    override fun seasonListParse(response: Response): List<SAnime> = emptyList()

    // =========================== Lista de Episódios ===========================

    override fun episodeListRequest(anime: SAnime): Request {
        return GET("$baseUrl${anime.url}/episodes", headers)
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val body = response.body?.string().orEmpty()
        val episodesDto = try {
            json.decodeFromString<List<EpisodeDto>>(body)
        } catch (_: Exception) {
            val detail = json.decodeFromString<AnimeDetailDto>(body)
            detail.episodes
        }

        val episodes = episodesDto.mapIndexed { index, ep ->
            SEpisode.create().apply {
                name = ep.getEpTitle(index)
                episode_number = ep.getEpNumber(index)
                url = if (ep.streamUrl != null) {
                    ep.streamUrl
                } else {
                    "/episode/${ep.id}/stream"
                }
                date_upload = 0L
            }
        }

        return episodes.reversed()
    }

    // ============================ Hosters / Vídeos ============================

    override fun hosterListParse(response: Response): List<Hoster> {
        val contentType = response.header("Content-Type") ?: ""
        val url = response.request.url.toString()
        val hosters = mutableListOf<Hoster>()

        // 1. Caso a requisição já tenha retornado o manifesto HLS (.m3u8)
        if (url.contains(".m3u8") || contentType.contains("application/x-mpegURL") || contentType.contains("application/vnd.apple.mpegurl")) {
            val videos = playlistUtils.extractFromHls(url, masterHeaders = headers)
            return listOf(
                Hoster(
                    hosterUrl = url,
                    hosterName = "ShinoKai HLS",
                    videoList = videos,
                    internalData = "",
                    lazy = false,
                )
            )
        }

        // 2. Se for um arquivo de vídeo direto (.mp4, .mkv)
        if (url.contains(".mp4") || contentType.contains("video/mp4")) {
            val video = Video(url, "Direto (MP4)", url, headers = headers)
            return listOf(
                Hoster(
                    hosterUrl = url,
                    hosterName = "ShinoKai MP4",
                    videoList = listOf(video),
                    internalData = "",
                    lazy = false,
                )
            )
        }

        // 3. Resposta em JSON contendo stream_url ou array de fontes
        val body = response.body?.string().orEmpty()
        val streamData = try {
            json.decodeFromString<StreamResponseDto>(body)
        } catch (_: Exception) {
            null
        }

        if (streamData != null) {
            streamData.sources.forEachIndexed { i, source ->
                val videos = if (source.url.contains(".m3u8")) {
                    playlistUtils.extractFromHls(
                        source.url,
                        videoNameGen = { "${source.quality ?: "HLS"} - $it" },
                        masterHeaders = headers,
                    )
                } else {
                    listOf(Video(source.url, source.quality ?: "Padrão", source.url, headers = headers))
                }
                if (videos.isNotEmpty()) {
                    hosters.add(
                        Hoster(
                            hosterUrl = source.url,
                            hosterName = "Servidor ${i + 1} (${source.quality ?: "Padrão"})",
                            videoList = videos,
                            internalData = "",
                            lazy = false,
                        )
                    )
                }
            }

            val directUrl = streamData.streamUrl ?: streamData.videoUrl ?: streamData.url
            if (!directUrl.isNullOrBlank() && hosters.none { it.hosterUrl == directUrl }) {
                val videos = if (directUrl.contains(".m3u8")) {
                    playlistUtils.extractFromHls(directUrl, masterHeaders = headers)
                } else {
                    listOf(Video(directUrl, streamData.quality ?: "Padrão", directUrl, headers = headers))
                }
                if (videos.isNotEmpty()) {
                    hosters.add(
                        Hoster(
                            hosterUrl = directUrl,
                            hosterName = "ShinoKai Principal",
                            videoList = videos,
                            internalData = "",
                            lazy = false,
                        )
                    )
                }
            }
        }

        return hosters
    }

    override fun videoListParse(response: Response, hoster: Hoster): List<Video> {
        return hoster.videoList
    }

    override fun videoUrlParse(response: Response): String = response.request.url.toString()

    override fun getFilterList(): AnimeFilterList = ShinokaiFilters.getFilterList()
}
