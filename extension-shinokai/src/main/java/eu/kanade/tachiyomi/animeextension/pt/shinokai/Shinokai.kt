package eu.kanade.tachiyomi.animeextension.pt.shinokai

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
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
            author = detail.author ?: detail.studio
            artist = detail.artist
        }
    }

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

    // ============================ Vídeos / Streams ============================

    override fun videoListRequest(episode: SEpisode): Request {
        return if (episode.url.startsWith("http")) {
            GET(episode.url, headers)
        } else {
            GET("$baseUrl${episode.url}", headers)
        }
    }

    override fun videoListParse(response: Response): List<Video> {
        val contentType = response.header("Content-Type") ?: ""
        val url = response.request.url.toString()

        if (url.contains(".m3u8") || contentType.contains("application/x-mpegURL") || contentType.contains("application/vnd.apple.mpegurl")) {
            return playlistUtils.extractFromHls(url, masterHeaders = headers)
        }

        if (url.contains(".mp4") || contentType.contains("video/mp4")) {
            return listOf(Video(url, "Direto (MP4)", url, headers = headers))
        }

        val body = response.body?.string().orEmpty()
        val streamData = json.decodeFromString<StreamResponseDto>(body)
        val videoList = mutableListOf<Video>()

        if (streamData.sources.isNotEmpty()) {
            streamData.sources.forEach { source ->
                if (source.url.contains(".m3u8")) {
                    videoList.addAll(
                        playlistUtils.extractFromHls(
                            source.url,
                            videoNameGen = { "${source.quality ?: "HLS"} - $it" },
                            masterHeaders = headers,
                        )
                    )
                } else {
                    videoList.add(Video(source.url, source.quality ?: "Padrão", source.url, headers = headers))
                }
            }
        }

        val directUrl = streamData.streamUrl ?: streamData.videoUrl ?: streamData.url
        if (!directUrl.isNullOrBlank()) {
            if (directUrl.contains(".m3u8")) {
                videoList.addAll(playlistUtils.extractFromHls(directUrl, masterHeaders = headers))
            } else {
                videoList.add(Video(directUrl, streamData.quality ?: "Padrão", directUrl, headers = headers))
            }
        }

        return videoList.ifEmpty {
            throw Exception("Nenhum link de reprodução encontrado para este episódio.")
        }
    }

    override fun videoUrlParse(response: Response): String = ""

    override fun getFilterList(): AnimeFilterList = ShinokaiFilters.getFilterList()
}
