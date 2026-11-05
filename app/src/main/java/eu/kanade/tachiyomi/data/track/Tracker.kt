package eu.kanade.tachiyomi.data.track

import androidx.annotation.CallSuper
import androidx.annotation.DrawableRes
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import tachiyomi.domain.track.model.Track as DomainTrack

interface Tracker {

    val id: Long

    val name: String

    val client: OkHttpClient

    // Application and remote support for reading dates
    val supportsReadingDates: Boolean

    val supportsPrivateTracking: Boolean

    val supportsAnime: Boolean
        get() = false

    @DrawableRes
    fun getLogo(): Int

    fun getStatusList(): List<Long>

    fun getStatus(status: Long): StringResource?

    fun getStatus(status: Long, isAnime: Boolean): StringResource? = getStatus(status)

    fun getReadingStatus(): Long

    fun getRereadingStatus(): Long

    fun getCompletionStatus(): Long

    fun getScoreList(): List<String>

    // Note: Normalized 10-point scoring implementation
    fun get10PointScore(track: DomainTrack): Double

    fun indexToScore(index: Int): Double

    fun displayScore(track: DomainTrack): String

    suspend fun update(track: Track, didReadChapter: Boolean = false): Track

    suspend fun bind(track: Track, hasReadChapters: Boolean = false): Track

    suspend fun search(query: String): List<TrackSearch>

    suspend fun search(query: String, isAnime: Boolean): List<TrackSearch> = search(query)

    suspend fun refresh(track: Track): Track

    suspend fun login(username: String, password: String)

    @CallSuper
    fun logout()

    val isLoggedIn: Boolean

    val isLoggedInFlow: Flow<Boolean>

    fun getUsername(): String

    fun getPassword(): String

    fun getDisplayUsername(): String

    fun saveDisplayUsername(displayName: String)

    fun saveCredentials(username: String, password: String)

    // Note: tracker registration interface
    suspend fun register(item: Track, mangaId: Long)

    suspend fun setRemoteStatus(track: Track, status: Long)

    suspend fun setRemoteLastChapterRead(track: Track, chapterNumber: Int)

    suspend fun setRemoteScore(track: Track, scoreString: String)

    suspend fun setRemoteStartDate(track: Track, epochMillis: Long)

    suspend fun setRemoteFinishDate(track: Track, epochMillis: Long)

    suspend fun setRemotePrivate(track: Track, private: Boolean)
}
