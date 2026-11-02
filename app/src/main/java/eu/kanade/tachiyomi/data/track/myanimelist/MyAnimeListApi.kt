package eu.kanade.tachiyomi.data.track.myanimelist

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALListItem
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALListItemStatus
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALManga
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALOAuth
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALSearchResult
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALUser
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.PkceUtil
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import tachiyomi.domain.track.model.Track as DomainTrack

class MyAnimeListApi(
    private val trackId: Long,
    private val client: OkHttpClient,
    interceptor: MyAnimeListInterceptor,
) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun getAccessToken(authCode: String): MALOAuth {
        return withIOContext {
            val formBody: RequestBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("code", authCode)
                .add("code_verifier", codeVerifier)
                .add("grant_type", "authorization_code")
                .build()
            with(json) {
                client.newCall(POST("$BASE_OAUTH_URL/token", body = formBody))
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    suspend fun getCurrentUser(): String {
        return withIOContext {
            val request = Request.Builder()
                .url("$BASE_API_URL/users/@me")
                .get()
                .build()
            with(json) {
                authClient.newCall(request)
                    .awaitSuccess()
                    .parseAs<MALUser>()
                    .name
            }
        }
    }

    suspend fun search(query: String): List<TrackSearch> {
        return search(query, isAnime = false)
    }

    suspend fun search(query: String, isAnime: Boolean = false): List<TrackSearch> {
        return withIOContext {
            val endpoint = if (isAnime) "anime" else "manga"
            val fields = if (isAnime) ANIME_SEARCH_FIELDS else SEARCH_FIELDS
            val url = "$BASE_API_URL/$endpoint".toUri().buildUpon()
                // MAL API throws a 400 when the query is over 64 characters...
                .appendQueryParameter("q", query.take(64))
                .appendQueryParameter("nsfw", "true")
                .appendQueryParameter("fields", fields)
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<MALSearchResult>()
                    .data
                    .filter { if (isAnime) true else !(it.node.mediaType.contains("novel")) }
                    .map { parseSearchItem(it.node, isAnime) }
            }
        }
    }

    suspend fun getMangaDetails(id: Int): TrackSearch {
        return withIOContext {
            val url = "$BASE_API_URL/manga".toUri().buildUpon()
                .appendPath(id.toString())
                .appendQueryParameter("fields", SEARCH_FIELDS)
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<MALManga>()
                    .let { parseSearchItem(it) }
            }
        }
    }

    suspend fun updateItem(track: Track): Track {
        return withIOContext {
            val isAnime = track.tracking_url.contains("/anime")
            val endpointUrl = if (isAnime) animeUrl(track.remote_id) else mangaUrl(track.remote_id)
            val isRepeating = track.status == MyAnimeList.REREADING
            val statusString = track.toMyAnimeListStatus(isAnime) ?: if (isAnime) "watching" else "reading"

            val formBodyBuilder = FormBody.Builder()
                .add("status", statusString)
                .add(if (isAnime) "is_rewatching" else "is_rereading", isRepeating.toString())
                .add("score", track.score.toString())
                .add(if (isAnime) "num_watched_episodes" else "num_chapters_read", track.last_chapter_read.toInt().toString())
            convertToIsoDate(track.started_reading_date)?.let {
                formBodyBuilder.add("start_date", it)
            }
            convertToIsoDate(track.finished_reading_date)?.let {
                formBodyBuilder.add("finish_date", it)
            }

            val request = Request.Builder()
                .url(endpointUrl.toString())
                .put(formBodyBuilder.build())
                .build()
            with(json) {
                val response = authClient
                    .newCall(request)
                    .await()

                if (!response.isSuccessful) {
                    if (response.body.string().contains("invalid_content")) {
                        // MAL returns unapproved titles in search but does not allow adding them to the list
                        // returns 400 with this body: {"message":"Invalid content","error":"invalid_content"}
                        // These unapproved titles cannot be filtered out in search and are also returned by the
                        // endpoint we use for id prefix search
                        throw MALTitleNotApproved()
                    } else {
                        throw HttpException(response.code)
                    }
                }

                response
                    .parseAs<MALListItemStatus>()
                    .let { parseMangaItem(it, track) }
            }
        }
    }

    suspend fun deleteItem(track: DomainTrack) {
        withIOContext {
            val isAnime = track.remoteUrl.contains("/anime")
            val endpointUrl = if (isAnime) animeUrl(track.remoteId) else mangaUrl(track.remoteId)
            authClient
                .newCall(DELETE(endpointUrl.toString()))
                .awaitSuccess()
        }
    }

    suspend fun findListItem(track: Track): Track? {
        return withIOContext {
            val isAnime = track.tracking_url.contains("/anime")
            val endpoint = if (isAnime) "anime" else "manga"
            val countField = if (isAnime) "num_episodes" else "num_chapters"
            val uri = "$BASE_API_URL/$endpoint".toUri().buildUpon()
                .appendPath(track.remote_id.toString())
                .appendQueryParameter("fields", "$countField,my_list_status{start_date,finish_date}")
                .build()
            with(json) {
                authClient.newCall(GET(uri.toString()))
                    .awaitSuccess()
                    .parseAs<MALListItem>()
                    .let { item ->
                        val eps = item.numEpisodes
                        val chs = item.numChapters
                        track.total_chapters = if (isAnime && eps > 0) eps else if (chs > 0) chs else eps
                        item.myListStatus?.let { parseMangaItem(it, track) }
                    }
            }
        }
    }

    suspend fun findListItems(query: String, offset: Int = 0): List<TrackSearch> {
        return withIOContext {
            val myListSearchResult = getListPage(offset)

            val matches = myListSearchResult.data
                .filter { it.node.title.contains(query, ignoreCase = true) }
                .map { parseSearchItem(it.node) }

            // Check next page if there's more
            if (!myListSearchResult.paging.next.isNullOrBlank()) {
                matches + findListItems(query, offset + LIST_PAGINATION_AMOUNT)
            } else {
                matches
            }
        }
    }

    private suspend fun getListPage(offset: Int): MALSearchResult {
        return withIOContext {
            val urlBuilder = "$BASE_API_URL/users/@me/mangalist".toUri().buildUpon()
                .appendQueryParameter("fields", SEARCH_FIELDS)
                .appendQueryParameter("limit", LIST_PAGINATION_AMOUNT.toString())
            if (offset > 0) {
                urlBuilder.appendQueryParameter("offset", offset.toString())
            }

            val request = Request.Builder()
                .url(urlBuilder.build().toString())
                .get()
                .build()
            with(json) {
                authClient.newCall(request)
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    private fun parseMangaItem(listStatus: MALListItemStatus, track: Track): Track {
        return track.apply {
            val isRereading = listStatus.isRereading || listStatus.isRewatching
            status = if (isRereading) MyAnimeList.REREADING else getStatus(listStatus.status)
            val readCount = if (listStatus.numEpisodesWatched > 0) listStatus.numEpisodesWatched else listStatus.numChaptersRead
            last_chapter_read = readCount
            score = listStatus.score.toDouble()
            listStatus.startDate?.let { started_reading_date = parseDate(it) }
            listStatus.finishDate?.let { finished_reading_date = parseDate(it) }
        }
    }

    private fun parseSearchItem(searchItem: MALManga, isAnime: Boolean = false): TrackSearch {
        return TrackSearch.create(trackId).apply {
            remote_id = searchItem.id
            title = searchItem.title
            summary = searchItem.synopsis
            val eps = searchItem.numEpisodes
            val chs = searchItem.numChapters
            total_chapters = if (isAnime && eps > 0) eps else if (chs > 0) chs else eps
            score = searchItem.mean
            cover_url = searchItem.covers?.large.orEmpty()
            tracking_url = if (isAnime) "https://myanimelist.net/anime/$remote_id" else "https://myanimelist.net/manga/$remote_id"
            publishing_status = searchItem.status.replace("_", " ")
            publishing_type = searchItem.mediaType.replace("_", " ")
            start_date = searchItem.startDate ?: ""
            artists = searchItem.authors
                .filter { authorNode -> authorNode.role == "Art" }
                .mapNotNull { authorNode -> authorNode.node.getFullName() }
            authors = searchItem.authors
                // count all with "Story" or "Story & Art" as authors, like is done for library entries
                .filter { authorNode -> authorNode.role.contains("Story") }
                .mapNotNull { authorNode -> authorNode.node.getFullName() }
        }
    }

    private fun parseDate(isoDate: String): Long {
        val pattern = when (isoDate.length) {
            10 -> "yyyy-MM-dd"
            7 -> "yyyy-MM"
            4 -> "yyyy"
            else -> throw IllegalArgumentException("Unsupported date format: \"$isoDate\"")
        }
        return SimpleDateFormat(pattern, Locale.US).parse(isoDate)?.time ?: 0L
    }

    private fun convertToIsoDate(epochTime: Long): String? {
        if (epochTime == 0L) {
            return ""
        }
        return try {
            val outputDf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            outputDf.format(epochTime)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val CLIENT_ID = "c46c9e24640a64dad5be5ca7a1a53a0f"

        private const val BASE_OAUTH_URL = "https://myanimelist.net/v1/oauth2"
        private const val BASE_API_URL = "https://api.myanimelist.net/v2"

        private const val SEARCH_FIELDS =
            "id,title,synopsis,num_chapters,mean,main_picture,status,media_type,start_date,authors{first_name,last_name}"

        private const val ANIME_SEARCH_FIELDS =
            "id,title,synopsis,num_episodes,mean,main_picture,status,media_type,start_date"

        private const val LIST_PAGINATION_AMOUNT = 250

        private var codeVerifier: String = ""

        fun authUrl(): Uri = "$BASE_OAUTH_URL/authorize".toUri().buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("code_challenge", getPkceChallengeCode())
            .appendQueryParameter("response_type", "code")
            .build()

        fun mangaUrl(id: Long): Uri = "$BASE_API_URL/manga".toUri().buildUpon()
            .appendPath(id.toString())
            .appendPath("my_list_status")
            .build()

        fun animeUrl(id: Long): Uri = "$BASE_API_URL/anime".toUri().buildUpon()
            .appendPath(id.toString())
            .appendPath("my_list_status")
            .build()

        fun refreshTokenRequest(oauth: MALOAuth): Request {
            val formBody: RequestBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("refresh_token", oauth.refreshToken)
                .add("grant_type", "refresh_token")
                .build()

            // Add the Authorization header manually as this particular
            // request is called by the interceptor itself so it doesn't reach
            // the part where the token is added automatically.
            val headers = Headers.Builder()
                .add("Authorization", "Bearer ${oauth.accessToken}")
                .build()

            return POST("$BASE_OAUTH_URL/token", body = formBody, headers = headers)
        }

        private fun getPkceChallengeCode(): String {
            codeVerifier = PkceUtil.generateCodeVerifier()
            return codeVerifier
        }
    }
}
