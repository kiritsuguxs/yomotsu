package tachiyomi.source.local

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.UnmeteredSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import logcat.LogPriority
import rx.Observable
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.core.metadata.tachiyomi.MangaDetails
import tachiyomi.domain.chapter.service.ChapterRecognition
import tachiyomi.source.local.filter.AnimeOrderBy
import tachiyomi.source.local.image.LocalAnimeCoverManager
import tachiyomi.source.local.io.LocalAnimeSourceFileSystem
import uy.kohesive.injekt.injectLazy
import kotlin.time.Duration.Companion.days

class LocalAnimeSource(
    private val context: Context,
    private val fileSystem: LocalAnimeSourceFileSystem,
    private val coverManager: LocalAnimeCoverManager,
) : AnimeCatalogueSource, UnmeteredSource {

    private val json: Json by injectLazy()

    @Suppress("PrivatePropertyName")
    private val PopularFilters = AnimeFilterList(AnimeOrderBy.Popular(context))

    @Suppress("PrivatePropertyName")
    private val LatestFilters = AnimeFilterList(AnimeOrderBy.Latest(context))

    override val name: String = "Local anime"

    override val id: Long = ID

    override val lang: String = "other"

    override fun toString() = name

    override val supportsLatest: Boolean = true

    override suspend fun getPopularAnime(page: Int): AnimesPage = getSearchAnime(page, "", PopularFilters)

    override suspend fun getLatestUpdates(page: Int): AnimesPage = getSearchAnime(page, "", LatestFilters)

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage = withIOContext {
        val lastModifiedLimit = if (filters === LatestFilters) {
            System.currentTimeMillis() - LATEST_THRESHOLD
        } else {
            0L
        }

        var animeDirs = fileSystem.getFilesInBaseDirectory()
            // Filter out files that are hidden and is not a folder
            .filter { it.isDirectory && !it.name.orEmpty().startsWith('.') }
            .distinctBy { it.name }
            .filter {
                if (lastModifiedLimit == 0L && query.isBlank()) {
                    true
                } else if (lastModifiedLimit == 0L) {
                    it.name.orEmpty().contains(query, ignoreCase = true)
                } else {
                    it.lastModified() >= lastModifiedLimit
                }
            }

        filters.forEach { filter ->
            when (filter) {
                is AnimeOrderBy.Popular -> {
                    animeDirs = if (filter.state!!.ascending) {
                        animeDirs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() })
                    } else {
                        animeDirs.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() })
                    }
                }
                is AnimeOrderBy.Latest -> {
                    animeDirs = if (filter.state!!.ascending) {
                        animeDirs.sortedBy(UniFile::lastModified)
                    } else {
                        animeDirs.sortedByDescending(UniFile::lastModified)
                    }
                }
                else -> {
                    /* Do nothing */
                }
            }
        }

        val animes = animeDirs.map { animeDir ->
            SAnime.create().apply {
                title = animeDir.name.orEmpty()
                url = animeDir.name.orEmpty()

                coverManager.find(url)?.let {
                    thumbnail_url = it.uri.toString()
                }
            }
        }

        AnimesPage(animes, false)
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime = withIOContext {
        coverManager.find(anime.url)?.let {
            anime.thumbnail_url = it.uri.toString()
        }

        val animeDir = fileSystem.getAnimeDirectory(anime.url)
        val detailsFile = animeDir?.findFile("details.json")

        if (detailsFile != null && detailsFile.exists()) {
            try {
                val detailsJson = detailsFile.openInputStream()?.use {
                    json.decodeFromStream<MangaDetails>(it)
                }
                if (detailsJson != null) {
                    anime.title = detailsJson.title ?: anime.title
                    anime.author = detailsJson.author ?: anime.author
                    anime.artist = detailsJson.artist ?: anime.artist
                    anime.description = detailsJson.description ?: anime.description
                    anime.genre = detailsJson.genre?.joinToString(", ") ?: anime.genre
                    anime.status = detailsJson.status ?: anime.status
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Error reading details.json for ${anime.title}" }
            }
        }

        anime.initialized = true
        anime
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = withIOContext {
        val episodes = fileSystem.getFilesInAnimeDirectory(anime.url)
            .filterNot { it.name.orEmpty().startsWith('.') }
            .filter { file ->
                val ext = file.extension?.lowercase().orEmpty()
                ext in SUPPORTED_VIDEO_EXTENSIONS
            }
            .map { file ->
                SEpisode.create().apply {
                    url = "${anime.url}/${file.name}"
                    name = file.nameWithoutExtension.orEmpty()
                    date_upload = file.lastModified()
                    episode_number = ChapterRecognition
                        .parseChapterNumber(anime.title, this.name, this.episode_number.toDouble())
                        .toFloat()
                }
            }
            .sortedWith { e1, e2 ->
                e2.name.compareToCaseInsensitiveNaturalOrder(e1.name)
            }

        episodes
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val (animeDirName, episodeName) = episode.url.split('/', limit = 2)
        val file = fileSystem.getAnimeDirectory(animeDirName)?.findFile(episodeName)
            ?: throw Exception("Video file not found: ${episode.url}")

        val filePath = file.filePath
        val finalUrl = if (filePath != null && java.io.File(filePath).exists()) {
            "file://$filePath"
        } else {
            file.uri.toString()
        }

        return listOf(
            Video(
                videoUrl = finalUrl,
                videoTitle = episode.name,
            ),
        )
    }

    override fun getFilterList() = AnimeFilterList(AnimeOrderBy.Popular(context))

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getPopularAnime"))
    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> =
        throw UnsupportedOperationException("Not used")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getSearchAnime"))
    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> =
        throw UnsupportedOperationException("Not used")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getLatestUpdates"))
    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> =
        throw UnsupportedOperationException("Not used")

    companion object {
        const val ID = 9876543211L
        const val HELP_URL = "https://mihon.app/docs/guides/local-source/"

        private val LATEST_THRESHOLD = 7.days.inWholeMilliseconds

        val SUPPORTED_VIDEO_EXTENSIONS = setOf(
            "mp4",
            "mkv",
            "avi",
            "mov",
            "flv",
            "webm",
            "ts",
            "m4v",
            "wmv",
            "3gp",
        )
    }
}
