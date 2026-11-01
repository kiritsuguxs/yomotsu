package eu.kanade.tachiyomi.animeextension.pt.shinokai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnimePageDto(
    @SerialName("data") val data: List<AnimeItemDto> = emptyList(),
    @SerialName("hasNextPage") val hasNextPage: Boolean = false,
    @SerialName("page") val page: Int? = 1,
    @SerialName("total") val total: Int? = 0,
)

@Serializable
data class AnimeItemDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("thumbnail") val thumbnail: String? = null,
    @SerialName("image") val image: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("synopsis") val synopsis: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("genres") val genres: List<String> = emptyList(),
    @SerialName("rating") val rating: String? = null,
    @SerialName("audio") val audio: String? = null, // "Dublado", "Legendado"
) {
    fun getCover(): String = posterUrl ?: thumbnail ?: image ?: ""
    fun getPlot(): String = synopsis ?: description ?: ""
}

@Serializable
data class AnimeDetailDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("thumbnail") val thumbnail: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("synopsis") val synopsis: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("genres") val genres: List<String> = emptyList(),
    @SerialName("episodes") val episodes: List<EpisodeDto> = emptyList(),
    @SerialName("author") val author: String? = null,
    @SerialName("artist") val artist: String? = null,
    @SerialName("release_year") val releaseYear: String? = null,
    @SerialName("audio_type") val audioType: String? = null,
)

@Serializable
data class EpisodeDto(
    @SerialName("id") val id: String,
    @SerialName("number") val number: Float? = null,
    @SerialName("episode_number") val episodeNumber: Float? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("stream_url") val streamUrl: String? = null,
) {
    fun getEpNumber(index: Int): Float = number ?: episodeNumber ?: (index + 1).toFloat()
    fun getEpTitle(index: Int): String = title ?: name ?: "Episódio ${getEpNumber(index).toInt()}"
}

@Serializable
data class StreamResponseDto(
    @SerialName("stream_url") val streamUrl: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("quality") val quality: String? = null,
    @SerialName("format") val format: String? = null, // "hls", "mp4"
    @SerialName("sources") val sources: List<VideoSourceDto> = emptyList(),
)

@Serializable
data class VideoSourceDto(
    @SerialName("url") val url: String,
    @SerialName("quality") val quality: String? = "Padrão",
    @SerialName("format") val format: String? = "mp4",
)
