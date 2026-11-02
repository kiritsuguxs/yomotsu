package eu.kanade.tachiyomi.data.track.myanimelist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MALListItem(
    @SerialName("num_chapters")
    val numChapters: Long = 0,
    @SerialName("num_episodes")
    val numEpisodes: Long = 0,
    @SerialName("my_list_status")
    val myListStatus: MALListItemStatus?,
)

@Serializable
data class MALListItemStatus(
    @SerialName("is_rereading")
    val isRereading: Boolean = false,
    @SerialName("is_rewatching")
    val isRewatching: Boolean = false,
    val status: String = "",
    @SerialName("num_chapters_read")
    val numChaptersRead: Double = 0.0,
    @SerialName("num_episodes_watched")
    val numEpisodesWatched: Double = 0.0,
    val score: Int = 0,
    @SerialName("start_date")
    val startDate: String? = null,
    @SerialName("finish_date")
    val finishDate: String? = null,
)
