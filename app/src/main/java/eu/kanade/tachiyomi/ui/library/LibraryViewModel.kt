package eu.kanade.tachiyomi.ui.library

import androidx.compose.runtime.Immutable
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.viewModelScope
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.core.preference.asState
import eu.kanade.core.util.fastFilterNot
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.merge.GroupMarks
import eu.kanade.domain.merge.dedupeByMergeGroup
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.chapter.getNextUnread
import eu.kanade.tachiyomi.util.removeCovers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import mihon.core.common.utils.mutate
import mihon.core.viewmodel.StateViewModel
import mihon.domain.library.model.search.QueryNode
import mihon.feature.library.matches
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.toCollationKey
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.interactor.GetBookmarkedChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.applyFilter
import tachiyomi.domain.merge.MergedChapterUnitRepository
import tachiyomi.domain.merge.MergedGroupCounts
import tachiyomi.domain.merge.model.ContentType
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracksPerManga
import tachiyomi.domain.track.model.Track
import tachiyomi.source.local.isLocal
import kotlinx.coroutines.flow.first
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.CollationKey
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class LibraryViewModel : StateViewModel<LibraryViewModel.State>(State()) {

    private val getLibraryManga: GetLibraryManga by injectLazy()
    private val getCategories: GetCategories by injectLazy()
    private val getTracksPerManga: GetTracksPerManga by injectLazy()
    private val getNextChapters: GetNextChapters by injectLazy()
    private val getChaptersByMangaId: GetChaptersByMangaId by injectLazy()
    private val getBookmarkedChaptersByMangaId: GetBookmarkedChaptersByMangaId by injectLazy()
    private val setReadStatus: SetReadStatus by injectLazy()
    private val updateManga: UpdateManga by injectLazy()
    private val setMangaCategories: SetMangaCategories by injectLazy()
    private val preferences: BasePreferences by injectLazy()
    private val libraryPreferences: LibraryPreferences by injectLazy()
    private val coverCache: CoverCache by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val downloadManager: DownloadManager by injectLazy()
    private val animeDownloadManager: eu.kanade.tachiyomi.data.animedownload.AnimeDownloadManager by injectLazy()
    private val downloadCache: DownloadCache by injectLazy()
    private val animeDownloadCache: eu.kanade.tachiyomi.data.animedownload.AnimeDownloadCache by injectLazy()
    private val trackerManager: TrackerManager by injectLazy()
    private val mangaMergeManager: eu.kanade.domain.manga.MangaMergeManager by injectLazy()
    private val mergedChapterProvider: eu.kanade.domain.manga.MergedChapterProvider by injectLazy()
    private val mangaGroupCategories: eu.kanade.domain.manga.MangaGroupCategories by injectLazy()
    private val mergedChapterUnitRepository: MergedChapterUnitRepository by injectLazy()
    private val getManga: GetManga by injectLazy()
    private val uiPreferences: eu.kanade.domain.ui.UiPreferences by injectLazy()

    init {
        viewModelScope.launchIO {
            uiPreferences.lastUsedLibraryMedia.changes().collectLatest { media ->
                val lastUsedIndex = when (media) {
                    "Anime" -> libraryPreferences.lastUsedCategoryAnime.get()
                    "Novel" -> libraryPreferences.lastUsedCategoryNovel.get()
                    else -> libraryPreferences.lastUsedCategory.get()
                }
                mutableState.update { state -> state.copy(activeCategoryIndex = lastUsedIndex) }
            }
        }
        viewModelScope.launchIO {
            // Wait on IO for SourceManager extension discovery so media-type filtering never misclassifies entries on cold start
            sourceManager.isInitialized.first { it }

            combine(
                state.map { it.searchQuery }.distinctUntilChanged().debounce(0.25.seconds),
                combine(getCategories.subscribe(), uiPreferences.lastUsedLibraryMedia.changes()) { categories, media ->
                    categories.filter { category ->
                        if (category.isSystemCategory) return@filter true
                        val type = category.mediaType

                        when (media) {
                            "Anime" -> type == tachiyomi.domain.category.model.Category.MediaType.ANIME
                            "Novel" -> type == tachiyomi.domain.category.model.Category.MediaType.NOVEL
                            else -> type == tachiyomi.domain.category.model.Category.MediaType.MANGA
                        }
                    }
                },
                getFavoritesFlow(uiPreferences.lastUsedLibraryMedia.changes()),
                combine(getTracksPerManga.subscribe(), getTrackingFiltersFlow(), ::Pair),
                combine(
                    getLibraryItemPreferencesFlow(),
                    mangaMergeManager.membershipChanges(),
                    libraryPreferences.seriesMergingEnabled.changes(),
                    mergedChapterUnitRepository.getGroupCountsAsFlow(ContentType.MANGA),
                ) { prefs, memberships, enabled, groupCounts -> MergeFlowInputs(prefs, memberships, enabled, groupCounts) },
            ) { searchQuery, categories, favorites, (tracksMap, trackingFilters), mergeInputs ->
                val (itemPreferences, memberships, mergingEnabled, groupCounts) = mergeInputs
                val showSystemCategory = favorites.isEmpty() || favorites.any { it.libraryManga.categories.contains(0) }
                val mergedAwareFavorites = if (mergingEnabled && memberships.isNotEmpty()) {
                    val itemsByGroup = favorites.filter { it.id in memberships }.groupBy { memberships[it.id]!! }
                    favorites.map { item ->
                        val groupId = memberships[item.id] ?: return@map item
                        val groupMembers = itemsByGroup[groupId] ?: return@map item
                        if (groupMembers.size <= 1) return@map item
                        val counts = groupCounts[groupId]
                        val maxUnread = groupMembers.maxOf { it.unreadCount }
                        val maxReadCount = groupMembers.maxOf { it.libraryManga.readCount }
                        val aggTotal = counts?.total ?: maxOf(groupMembers.maxOf { it.libraryManga.totalChapters }, maxReadCount + maxUnread)
                        val aggUnread = counts?.unread ?: maxUnread
                        val aggRead = counts?.read ?: (aggTotal - aggUnread)
                        val aggBookmarks = counts?.bookmarked ?: groupMembers.maxOf { it.libraryManga.bookmarkCount }
                        val totalDl = groupMembers.sumOf { it.downloadCount }
                        val mergedCategories = groupMembers.flatMap { it.libraryManga.categories }.distinct()
                        item.copy(
                            libraryManga = item.libraryManga.copy(
                                categories = mergedCategories,
                                totalChapters = aggTotal,
                                readCount = aggRead,
                                bookmarkCount = aggBookmarks,
                                latestUpload = groupMembers.maxOf { it.libraryManga.latestUpload },
                                chapterFetchedAt = groupMembers.maxOf { it.libraryManga.chapterFetchedAt },
                                lastRead = groupMembers.maxOf { it.libraryManga.lastRead },
                            ),
                            downloadCount = totalDl,
                            unreadCount = aggUnread,
                            badges = item.badges.copy(
                                downloadCount = if (itemPreferences.downloadBadge) totalDl else 0,
                                unreadCount = if (itemPreferences.unreadBadge) aggUnread else 0,
                            ),
                        )
                    }
                } else {
                    favorites
                }
                val mergedTracksMap = if (mergingEnabled && memberships.isNotEmpty() && tracksMap.isNotEmpty()) {
                    val itemsByGroup = favorites.filter { it.id in memberships }.groupBy { memberships[it.id]!! }
                    tracksMap.toMutableMap().apply {
                        itemsByGroup.values.forEach { groupMembers ->
                            if (groupMembers.size <= 1) return@forEach
                            val combinedTracks = groupMembers
                                .flatMap { tracksMap[it.id].orEmpty() }
                                .distinctBy { it.trackerId to it.remoteId }
                            if (combinedTracks.isNotEmpty()) {
                                groupMembers.forEach { member -> put(member.id, combinedTracks) }
                            }
                        }
                    }
                } else {
                    tracksMap
                }
                val filteredFavorites = mergedAwareFavorites
                    .applyFilters(mergedTracksMap, trackingFilters, itemPreferences)
                    .let { libraryItems ->
                        if (searchQuery.isNullOrEmpty()) {
                            libraryItems
                        } else {
                            val queryNode = QueryNode.from(searchQuery)
                            libraryItems.filter { queryNode.matches(it) }
                        }
                    }
                    .let { items ->
                        if (mergingEnabled) {
                            items.dedupeByMergeGroup(memberships) { it.id }
                        } else {
                            items
                        }
                    }

                LibraryData(
                    isInitialized = true,
                    showSystemCategory = showSystemCategory,
                    categories = categories,
                    favorites = filteredFavorites,
                    tracksMap = mergedTracksMap,
                    loggedInTrackerIds = trackingFilters.keys,
                ) to memberships
            }
                .distinctUntilChanged()
                .collectLatest { (libraryData, memberships) ->
                    mutableState.update { state ->
                        state.copy(
                            libraryData = libraryData,
                            memberships = memberships,
                        )
                    }
                }
        }

        viewModelScope.launchIO {
            state
                .dropWhile { !it.libraryData.isInitialized }
                .map { it.libraryData }
                .distinctUntilChanged()
                .map { data ->
                    data.favorites
                        .applyGrouping(data.categories, data.showSystemCategory)
                        .applySort(data.favoritesById, data.tracksMap, data.loggedInTrackerIds)
                }
                .collectLatest {
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            groupedFavorites = it,
                        )
                    }
                }
        }

        combine(
            libraryPreferences.categoryTabs.changes(),
            libraryPreferences.categoryNumberOfItems.changes(),
            libraryPreferences.showContinueReadingButton.changes(),
        ) { a, b, c -> arrayOf(a, b, c) }
            .onEach { (showCategoryTabs, showMangaCount, showMangaContinueButton) ->
                mutableState.update { state ->
                    state.copy(
                        showCategoryTabs = showCategoryTabs,
                        showMangaCount = showMangaCount,
                        showMangaContinueButton = showMangaContinueButton,
                    )
                }
            }
            .launchIn(viewModelScope)

        combine(
            getLibraryItemPreferencesFlow(),
            getTrackingFiltersFlow(),
        ) { prefs, trackFilters ->
            listOf(
                prefs.filterDownloaded,
                prefs.filterUnread,
                prefs.filterStarted,
                prefs.filterBookmarked,
                prefs.filterCompleted,
                prefs.filterIntervalCustom,
                *trackFilters.values.toTypedArray(),
            )
                .any { it != TriState.DISABLED }
        }
            .distinctUntilChanged()
            .onEach {
                mutableState.update { state ->
                    state.copy(hasActiveFilters = it)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun List<LibraryItem>.applyFilters(
        trackMap: Map<Long, List<Track>>,
        trackingFilter: Map<Long, TriState>,
        preferences: ItemPreferences,
    ): List<LibraryItem> {
        val downloadedOnly = preferences.globalFilterDownloaded
        val skipOutsideReleasePeriod = preferences.skipOutsideReleasePeriod
        val filterDownloaded = if (downloadedOnly) TriState.ENABLED_IS else preferences.filterDownloaded
        val filterUnread = preferences.filterUnread
        val filterStarted = preferences.filterStarted
        val filterBookmarked = preferences.filterBookmarked
        val filterCompleted = preferences.filterCompleted
        val filterIntervalCustom = preferences.filterIntervalCustom

        val isNotLoggedInAnyTrack = trackingFilter.isEmpty()

        val excludedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_NOT) it.key else null }
        val includedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_IS) it.key else null }
        val trackFiltersIsIgnored = includedTracks.isEmpty() && excludedTracks.isEmpty()

        val filterFnDownloaded: (LibraryItem) -> Boolean = {
            applyFilter(filterDownloaded) { it.isLocal || it.downloadCount > 0 }
        }

        val filterFnUnread: (LibraryItem) -> Boolean = {
            applyFilter(filterUnread) { it.libraryManga.unreadCount > 0 }
        }

        val filterFnStarted: (LibraryItem) -> Boolean = {
            applyFilter(filterStarted) { it.libraryManga.hasStarted }
        }

        val filterFnBookmarked: (LibraryItem) -> Boolean = {
            applyFilter(filterBookmarked) { it.libraryManga.hasBookmarks }
        }

        val filterFnCompleted: (LibraryItem) -> Boolean = {
            applyFilter(filterCompleted) { it.libraryManga.manga.status.toInt() == SManga.COMPLETED }
        }

        val filterFnIntervalCustom: (LibraryItem) -> Boolean = {
            if (skipOutsideReleasePeriod) {
                applyFilter(filterIntervalCustom) { it.libraryManga.manga.fetchInterval < 0 }
            } else {
                true
            }
        }

        val filterFnTracking: (LibraryItem) -> Boolean = tracking@{ item ->
            if (isNotLoggedInAnyTrack || trackFiltersIsIgnored) return@tracking true

            val mangaTracks = trackMap[item.id].orEmpty().map { it.trackerId }

            val isExcluded = excludedTracks.isNotEmpty() && mangaTracks.fastAny { it in excludedTracks }
            val isIncluded = includedTracks.isEmpty() || mangaTracks.fastAny { it in includedTracks }

            !isExcluded && isIncluded
        }

        return fastFilter {
            filterFnDownloaded(it) &&
                filterFnUnread(it) &&
                filterFnStarted(it) &&
                filterFnBookmarked(it) &&
                filterFnCompleted(it) &&
                filterFnIntervalCustom(it) &&
                filterFnTracking(it)
        }
    }

    private fun List<LibraryItem>.applyGrouping(
        categories: List<Category>,
        showSystemCategory: Boolean,
    ): Map<Category, List</* LibraryItem */ Long>> {
        val groupCache = mutableMapOf</* Category */ Long, MutableList</* LibraryItem */ Long>>()
        forEach { item ->
            item.libraryManga.categories.forEach { categoryId ->
                groupCache.getOrPut(categoryId) { mutableListOf() }.add(item.id)
            }
        }
        return categories.filter { showSystemCategory || !it.isSystemCategory }
            .associateWith { groupCache[it.id]?.toList().orEmpty() }
    }

    private fun Map<Category, List</* LibraryItem */ Long>>.applySort(
        favoritesById: Map<Long, LibraryItem>,
        trackMap: Map<Long, List<Track>>,
        loggedInTrackerIds: Set<Long>,
    ): Map<Category, List</* LibraryItem */ Long>> {
        val collationKeys = HashMap<Long, CollationKey>(favoritesById.size)
        fun collationKeyFor(item: LibraryItem): CollationKey =
            collationKeys.getOrPut(item.id) { item.libraryManga.manga.title.lowercase().toCollationKey() }

        val sortAlphabetically: (LibraryItem, LibraryItem) -> Int = { manga1, manga2 ->
            collationKeyFor(manga1).compareTo(collationKeyFor(manga2))
        }

        val defaultTrackerScoreSortValue = -1.0
        val trackerScores by lazy {
            val trackerMap = trackerManager.getAll(loggedInTrackerIds).associateBy { e -> e.id }
            trackMap.mapValues { entry ->
                when {
                    entry.value.isEmpty() -> null
                    else ->
                        entry.value
                            .mapNotNull { trackerMap[it.trackerId]?.get10PointScore(it) }
                            .average()
                }
            }
        }

        fun LibrarySort.comparator(): Comparator<LibraryItem> = Comparator { manga1, manga2 ->
            when (this.type) {
                LibrarySort.Type.Alphabetical -> {
                    sortAlphabetically(manga1, manga2)
                }
                LibrarySort.Type.LastRead -> {
                    manga1.libraryManga.lastRead.compareTo(manga2.libraryManga.lastRead)
                }
                LibrarySort.Type.LastUpdate -> {
                    manga1.libraryManga.manga.lastUpdate.compareTo(manga2.libraryManga.manga.lastUpdate)
                }
                LibrarySort.Type.UnreadCount -> when {
                    // Ensure unread content comes first
                    manga1.libraryManga.unreadCount == manga2.libraryManga.unreadCount -> 0
                    manga1.libraryManga.unreadCount == 0L -> if (this.isAscending) 1 else -1
                    manga2.libraryManga.unreadCount == 0L -> if (this.isAscending) -1 else 1
                    else -> manga1.libraryManga.unreadCount.compareTo(manga2.libraryManga.unreadCount)
                }
                LibrarySort.Type.TotalChapters -> {
                    manga1.libraryManga.totalChapters.compareTo(manga2.libraryManga.totalChapters)
                }
                LibrarySort.Type.LatestChapter -> {
                    manga1.libraryManga.latestUpload.compareTo(manga2.libraryManga.latestUpload)
                }
                LibrarySort.Type.ChapterFetchDate -> {
                    manga1.libraryManga.chapterFetchedAt.compareTo(manga2.libraryManga.chapterFetchedAt)
                }
                LibrarySort.Type.DateAdded -> {
                    manga1.libraryManga.manga.dateAdded.compareTo(manga2.libraryManga.manga.dateAdded)
                }
                LibrarySort.Type.TrackerMean -> {
                    val item1Score = trackerScores[manga1.id] ?: defaultTrackerScoreSortValue
                    val item2Score = trackerScores[manga2.id] ?: defaultTrackerScoreSortValue
                    item1Score.compareTo(item2Score)
                }
                LibrarySort.Type.Random -> {
                    error("Why Are We Still Here? Just To Suffer?")
                }
            }
        }

        return mapValues { (key, value) ->
            if (key.sort.type == LibrarySort.Type.Random) {
                return@mapValues value.shuffled(Random(libraryPreferences.randomSortSeed.get()))
            }

            val manga = value.mapNotNull { favoritesById[it] }

            val comparator = key.sort.comparator()
                .let { if (key.sort.isAscending) it else it.reversed() }
                .thenComparator(sortAlphabetically)

            manga.sortedWith(comparator).map { it.id }
        }
    }

    private fun getLibraryItemPreferencesFlow(): Flow<ItemPreferences> {
        return combine(
            libraryPreferences.downloadBadge.changes(),
            libraryPreferences.unreadBadge.changes(),
            libraryPreferences.localBadge.changes(),
            libraryPreferences.languageBadge.changes(),
            libraryPreferences.autoUpdateMangaRestrictions.changes(),

            preferences.downloadedOnly.changes(),
            libraryPreferences.filterDownloaded.changes(),
            libraryPreferences.filterUnread.changes(),
            libraryPreferences.filterStarted.changes(),
            libraryPreferences.filterBookmarked.changes(),
            libraryPreferences.filterCompleted.changes(),
            libraryPreferences.filterIntervalCustom.changes(),
        ) {
            ItemPreferences(
                downloadBadge = it[0] as Boolean,
                unreadBadge = it[1] as Boolean,
                localBadge = it[2] as Boolean,
                languageBadge = it[3] as Boolean,
                skipOutsideReleasePeriod = LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD in (it[4] as Set<*>),
                globalFilterDownloaded = it[5] as Boolean,
                filterDownloaded = it[6] as TriState,
                filterUnread = it[7] as TriState,
                filterStarted = it[8] as TriState,
                filterBookmarked = it[9] as TriState,
                filterCompleted = it[10] as TriState,
                filterIntervalCustom = it[11] as TriState,
            )
        }
    }

    private class CachedSourceMeta(
        val source: eu.kanade.tachiyomi.source.Source,
        val lowerName: String,
        val lang: String,
        val mediaBucket: String,
    )

    private fun getMediaDownloadCount(
        manga: Manga,
        source: eu.kanade.tachiyomi.source.Source = sourceManager.getOrStub(manga.source),
    ): Int {
        return when {
            source is eu.kanade.tachiyomi.source.INovelSource ->
                eu.kanade.tachiyomi.extension.novel.download.NovelDownloadManager.getDownloadCount(manga.id)
            source is eu.kanade.tachiyomi.animesource.AnimeSource ->
                animeDownloadManager.getDownloadCount(manga)
            else ->
                downloadManager.getDownloadCount(manga)
        }
    }

    private fun getFavoritesFlow(mediaFlow: Flow<String>): Flow<List<LibraryItem>> {
        val downloadChanges = combine(
            downloadCache.changes,
            animeDownloadCache.changes,
            eu.kanade.tachiyomi.extension.novel.download.NovelDownloadManager.downloadedChapters,
        ) { _, _, _ -> Unit }

        return combine(
            getLibraryManga.subscribe(),
            mediaFlow,
            sourceManager.sources,
            getLibraryItemPreferencesFlow(),
            downloadChanges,
        ) { libraryManga, media, _, preferences, _ ->
            val targetBucket = when (media) {
                "Anime" -> "Anime"
                "Novel" -> "Novel"
                else -> "Manga"
            }
            val needsDownloadCount = preferences.downloadBadge ||
                preferences.filterDownloaded != TriState.DISABLED ||
                preferences.globalFilterDownloaded
            val sourceMetaCache = HashMap<Long, CachedSourceMeta>(32)

            libraryManga.mapNotNull { manga ->
                val meta = sourceMetaCache.getOrPut(manga.manga.source) {
                    val src = sourceManager.getOrStub(manga.manga.source)
                    val bucket = when {
                        src is eu.kanade.tachiyomi.animesource.AnimeSource -> "Anime"
                        src is eu.kanade.tachiyomi.novelsource.NovelSource || src is eu.kanade.tachiyomi.source.INovelSource -> "Novel"
                        else -> "Manga"
                    }
                    CachedSourceMeta(
                        source = src,
                        lowerName = src.name.lowercase(),
                        lang = src.lang,
                        mediaBucket = bucket,
                    )
                }
                if (meta.mediaBucket != targetBucket) return@mapNotNull null

                val dlCount = if (needsDownloadCount) getMediaDownloadCount(manga.manga, meta.source) else 0
                val isLocalManga = manga.manga.isLocal()
                LibraryItem(
                    libraryManga = manga,
                    downloadCount = dlCount,
                    unreadCount = manga.unreadCount,
                    isLocal = isLocalManga,
                    sourceName = meta.lowerName,
                    sourceLanguage = meta.lang,
                    badges = LibraryItem.Badges(
                        downloadCount = if (preferences.downloadBadge) dlCount else 0,
                        unreadCount = if (preferences.unreadBadge) manga.unreadCount else 0,
                        isLocal = if (preferences.localBadge) isLocalManga else false,
                        sourceLanguage = if (preferences.languageBadge) meta.lang else "",
                    ),
                )
            }
        }
    }

    /**
     * Flow of tracking filter preferences
     *
     * @return map of track id with the filter value
     */
    private fun getTrackingFiltersFlow(): Flow<Map<Long, TriState>> {
        return trackerManager.loggedInTrackersFlow().flatMapLatest { loggedInTrackers ->
            if (loggedInTrackers.isEmpty()) {
                flowOf(emptyMap())
            } else {
                val filterFlows = loggedInTrackers.map { tracker ->
                    libraryPreferences.filterTracking(tracker.id.toInt()).changes().map { tracker.id to it }
                }
                combine(filterFlows) { it.toMap() }
            }
        }
    }

    /**
     * Returns the common categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    private suspend fun getCommonCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        return mangas
            .map { getCategories.await(it.id).toSet() }
            .reduce { set1, set2 -> set1.intersect(set2) }
    }

    suspend fun getNextUnreadChapter(manga: Manga): Chapter? {
        val group = mergedChapterProvider.load(manga)
        if (!group.isMerged) {
            return group.chapters.getNextUnread(manga, downloadManager)
        }
        val marks = GroupMarks.of(
            pooled = group.pooledChapters,
            shown = group.chapters,
            stitch = group.stitch,
            id = { it.id },
            read = { it.read },
            bookmark = { it.bookmark },
        )
        val markedChapters = group.chapters.map { ch ->
            val effectiveRead = marks.isRead(ch.id, ch.read)
            if (effectiveRead == ch.read) ch else ch.copy(read = effectiveRead)
        }
        return markedChapters.getNextUnread(manga, downloadManager, group.mangaById)
    }

    /**
     * Returns the mix (non-common) categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    private suspend fun getMixCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        val mangaCategories = mangas.map { getCategories.await(it.id).toSet() }
        val common = mangaCategories.reduce { set1, set2 -> set1.intersect(set2) }
        return mangaCategories.flatten().distinct().subtract(common)
    }

    /**
     * Queues the amount specified of unread chapters from the list of selected manga
     */
    fun performDownloadAction(action: DownloadAction) {
        when (action) {
            DownloadAction.NEXT_1_CHAPTER -> downloadNextChapters(1)
            DownloadAction.NEXT_5_CHAPTERS -> downloadNextChapters(5)
            DownloadAction.NEXT_10_CHAPTERS -> downloadNextChapters(10)
            DownloadAction.NEXT_25_CHAPTERS -> downloadNextChapters(25)
            DownloadAction.UNREAD_CHAPTERS -> downloadNextChapters(null)
            DownloadAction.BOOKMARKED_CHAPTERS -> downloadBookmarkedChapters()
        }
        clearSelection()
    }

    private fun downloadNextChapters(amount: Int?) {
        val mangas = state.value.selectedManga
        viewModelScope.launchNonCancellable {
            mangas.forEach { manga ->
                val group = mergedChapterProvider.load(manga)
                if (!group.isMerged) {
                    val chapters = getNextChapters.await(manga.id)
                        .filterNotQueuedOrDownloaded(manga, emptyMap())
                        .let { if (amount != null) it.take(amount) else it }

                    enqueueDownloadsForMedia(manga, chapters)
                } else {
                    val marks = GroupMarks.of(
                        pooled = group.pooledChapters,
                        shown = group.chapters,
                        stitch = group.stitch,
                        id = { it.id },
                        read = { it.read },
                        bookmark = { it.bookmark },
                    )
                    val isNovel = sourceManager.get(manga.source) is eu.kanade.tachiyomi.source.INovelSource
                    val sortedChapters = if (isNovel) {
                        group.chapters.sortedBy { it.sourceOrder }
                    } else {
                        group.chapters.sortedWith(getChapterSort(manga, sortDescending = false))
                    }
                    val toDownload = sortedChapters
                        .filterNot { marks.isRead(it.id, it.read) }
                        .filterNotQueuedOrDownloaded(manga, group.mangaById)
                        .let { if (amount != null) it.take(amount) else it }
                    toDownload.groupBy { it.mangaId }.forEach { (mId, mChapters) ->
                        enqueueDownloadsForMedia(group.mangaById[mId] ?: manga, mChapters)
                    }
                }
            }
        }
    }

    private fun downloadBookmarkedChapters() {
        val mangas = state.value.selectedManga
        viewModelScope.launchNonCancellable {
            mangas.forEach { manga ->
                val group = mergedChapterProvider.load(manga)
                if (!group.isMerged) {
                    val chapters = getBookmarkedChaptersByMangaId.await(manga.id)
                        .filterNotQueuedOrDownloaded(manga, emptyMap())
                    enqueueDownloadsForMedia(manga, chapters)
                } else {
                    val marks = GroupMarks.of(
                        pooled = group.pooledChapters,
                        shown = group.chapters,
                        stitch = group.stitch,
                        id = { it.id },
                        read = { it.read },
                        bookmark = { it.bookmark },
                    )
                    val toDownload = group.chapters
                        .filter { marks.isBookmarked(it.id, it.bookmark) }
                        .filterNotQueuedOrDownloaded(manga, group.mangaById)
                    toDownload.groupBy { it.mangaId }.forEach { (mId, mChapters) ->
                        enqueueDownloadsForMedia(group.mangaById[mId] ?: manga, mChapters)
                    }
                }
            }
        }
    }

    private fun enqueueDownloadsForMedia(manga: Manga, chapters: List<Chapter>) {
        if (chapters.isEmpty()) return
        val source = sourceManager.getOrStub(manga.source)
        when {
            source is eu.kanade.tachiyomi.source.INovelSource ->
                eu.kanade.tachiyomi.extension.novel.download.NovelDownloadManager.downloadChapters(manga, chapters)
            source is eu.kanade.tachiyomi.animesource.AnimeSource ->
                animeDownloadManager.downloadChapters(manga, chapters)
            else ->
                downloadManager.downloadChapters(manga, chapters)
        }
    }

    private fun List<Chapter>.filterNotQueuedOrDownloaded(
        fallbackManga: Manga,
        mangaById: Map<Long, Manga>,
    ): List<Chapter> {
        if (isEmpty()) return emptyList()
        val queuedMangaDownloads by lazy { downloadManager.getQueuedDownloadsByChapterId() }
        val novelDownloading by lazy { eu.kanade.tachiyomi.extension.novel.download.NovelDownloadManager.downloadingChapters.value }
        val skipIds = HashSet<Long>()
        groupBy { it.mangaId }.forEach { (mId, mChapters) ->
            val targetManga = mangaById[mId] ?: fallbackManga
            val source = sourceManager.getOrStub(targetManga.source)
            when {
                source is eu.kanade.tachiyomi.source.INovelSource -> {
                    mChapters.forEach {
                        if (it.id in novelDownloading ||
                            eu.kanade.tachiyomi.extension.novel.download.NovelDownloadManager.isChapterDownloaded(targetManga.id, it.id)
                        ) {
                            skipIds.add(it.id)
                        }
                    }
                }
                source is eu.kanade.tachiyomi.animesource.AnimeSource -> {
                    mChapters.forEach {
                        if (animeDownloadManager.getQueuedDownloadOrNull(it.id) != null ||
                            animeDownloadManager.isChapterDownloaded(it.name, it.scanlator, targetManga.title, targetManga.source)
                        ) {
                            skipIds.add(it.id)
                        }
                    }
                }
                else -> {
                    val downloadedIds = downloadManager.getDownloadedChapterIds(mChapters, targetManga)
                    mChapters.forEach {
                        if (it.id in queuedMangaDownloads || it.id in downloadedIds) {
                            skipIds.add(it.id)
                        }
                    }
                }
            }
        }
        return fastFilterNot { it.id in skipIds }
    }

    private suspend fun expandSelectionWithMergedMembers(mangas: List<Manga>): List<Manga> {
        if (!libraryPreferences.seriesMergingEnabled.get()) return mangas
        val seen = LinkedHashMap<Long, Manga>(mangas.size)
        mangas.forEach { manga ->
            seen[manga.id] = manga
            val memberIds = mangaMergeManager.computeRelatedIds(manga.id)
            if (memberIds.size > 1) {
                memberIds.forEach { memberId ->
                    if (memberId !in seen) {
                        getManga.await(memberId)?.let { seen[memberId] = it }
                    }
                }
            }
        }
        return seen.values.toList()
    }

    /**
     * Marks mangas' chapters read status.
     */
    fun markReadSelection(read: Boolean) {
        val selection = state.value.selectedManga
        viewModelScope.launchNonCancellable {
            expandSelectionWithMergedMembers(selection).forEach { manga ->
                setReadStatus.await(
                    manga = manga,
                    read = read,
                )
            }
        }
        clearSelection()
    }

    /**
     * Remove the selected manga.
     *
     * @param mangas the list of manga to delete.
     * @param deleteFromLibrary whether to delete manga from library.
     * @param deleteChapters whether to delete downloaded chapters.
     */
    fun removeMangas(mangas: List<Manga>, deleteFromLibrary: Boolean, deleteChapters: Boolean) {
        viewModelScope.launchNonCancellable {
            val targetMangas = expandSelectionWithMergedMembers(mangas)
            if (deleteFromLibrary) {
                if (libraryPreferences.seriesMergingEnabled.get()) {
                    mangaMergeManager.unmerge(targetMangas.map { it.id })
                }
                val toDelete = targetMangas.map {
                    it.removeCovers(coverCache)
                    MangaUpdate(
                        favorite = false,
                        id = it.id,
                    )
                }
                updateManga.awaitAll(toDelete)
            }

            if (deleteChapters) {
                targetMangas.forEach { manga ->
                    val rawSource = sourceManager.get(manga.source)
                    if (rawSource is eu.kanade.tachiyomi.source.INovelSource) {
                        eu.kanade.tachiyomi.extension.novel.download.NovelDownloadManager.deleteManga(manga.id)
                    } else {
                        val source = rawSource as? HttpSource
                        if (source != null) {
                            if (source is eu.kanade.tachiyomi.animesource.AnimeSource) {
                                animeDownloadManager.deleteManga(manga, source)
                            } else {
                                downloadManager.deleteManga(manga, source)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Bulk update categories of manga using old and new common categories.
     *
     * @param mangaList the list of manga to move.
     * @param addCategories the categories to add for all mangas.
     * @param removeCategories the categories to remove in all mangas.
     */
    fun setMangaCategories(mangaList: List<Manga>, addCategories: List<Long>, removeCategories: List<Long>) {
        viewModelScope.launchNonCancellable {
            val mergingEnabled = libraryPreferences.seriesMergingEnabled.get()
            mangaList.forEach { manga ->
                if (mergingEnabled) {
                    mangaGroupCategories.change(manga.id, addCategories, removeCategories, mangaMergeManager)
                } else {
                    val categoryIds = getCategories.await(manga.id)
                        .map { it.id }
                        .subtract(removeCategories.toSet())
                        .plus(addCategories)
                        .toList()

                    setMangaCategories.await(manga.id, categoryIds)
                }
            }
        }
    }

    fun getDisplayMode(): PreferenceMutableState<LibraryDisplayMode> {
        return libraryPreferences.displayMode.asState(viewModelScope)
    }

    fun getColumnsForOrientation(isLandscape: Boolean): PreferenceMutableState<Int> {
        return (if (isLandscape) libraryPreferences.landscapeColumns else libraryPreferences.portraitColumns)
            .asState(viewModelScope)
    }

    fun getRandomLibraryItemForCurrentCategory(): LibraryItem? {
        val state = state.value
        return state.getItemsForCategoryId(state.activeCategory?.id).randomOrNull()
    }

    fun showSettingsDialog() {
        mutableState.update { it.copy(dialog = Dialog.SettingsSheet) }
    }

    private var lastSelectionCategory: Long? = null

    fun clearSelection() {
        lastSelectionCategory = null
        mutableState.update { it.copy(selection = setOf()) }
    }

    fun mergeSelection() {
        val ids = state.value.selection.toList()
        if (ids.size < 2) return
        val selected = state.value.selectedManga
        val first = selected.firstOrNull() ?: return
        val src = sourceManager.get(first.source)
        val contentType = when {
            src is eu.kanade.tachiyomi.animesource.AnimeSource -> tachiyomi.domain.merge.model.ContentType.ANIME
            src is eu.kanade.tachiyomi.novelsource.NovelSource || src is eu.kanade.tachiyomi.source.INovelSource -> tachiyomi.domain.merge.model.ContentType.NOVEL
            else -> tachiyomi.domain.merge.model.ContentType.MANGA
        }
        viewModelScope.launchIO {
            mangaMergeManager.merge(contentType, ids)
            clearSelection()
        }
    }

    fun unmergeSelection() {
        val ids = state.value.selection.toList()
        if (ids.isEmpty()) return
        val selected = state.value.selectedManga
        val first = selected.firstOrNull() ?: return
        val src = sourceManager.get(first.source)
        val contentType = when {
            src is eu.kanade.tachiyomi.animesource.AnimeSource -> tachiyomi.domain.merge.model.ContentType.ANIME
            src is eu.kanade.tachiyomi.novelsource.NovelSource || src is eu.kanade.tachiyomi.source.INovelSource -> tachiyomi.domain.merge.model.ContentType.NOVEL
            else -> tachiyomi.domain.merge.model.ContentType.MANGA
        }
        viewModelScope.launchIO {
            mangaMergeManager.unmerge(contentType, ids)
            clearSelection()
        }
    }

    fun toggleSelection(category: Category, manga: LibraryManga) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { set ->
                if (!set.remove(manga.id)) set.add(manga.id)
            }
            lastSelectionCategory = category.id.takeIf { newSelection.isNotEmpty() }
            state.copy(selection = newSelection)
        }
    }

    /**
     * Selects all mangas between and including the given manga and the last pressed manga from the
     * same category as the given manga
     */
    fun toggleRangeSelection(category: Category, manga: LibraryManga) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val lastSelected = list.lastOrNull()
                if (lastSelectionCategory != category.id) {
                    list.add(manga.id)
                    return@mutate
                }

                val items = state.getItemsForCategoryId(category.id).fastMap { it.id }
                val lastMangaIndex = items.indexOf(lastSelected)
                val curMangaIndex = items.indexOf(manga.id)

                val selectionRange = when {
                    lastMangaIndex < curMangaIndex -> lastMangaIndex..curMangaIndex
                    curMangaIndex < lastMangaIndex -> curMangaIndex..lastMangaIndex
                    // We shouldn't reach this point
                    else -> return@mutate
                }
                selectionRange.mapNotNull { items[it] }.let(list::addAll)
            }
            lastSelectionCategory = category.id
            state.copy(selection = newSelection)
        }
    }

    fun selectAll() {
        lastSelectionCategory = null
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                state.getItemsForCategoryId(state.activeCategory?.id).map { it.id }.let(list::addAll)
            }
            state.copy(selection = newSelection)
        }
    }

    fun invertSelection() {
        lastSelectionCategory = null
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val itemIds = state.getItemsForCategoryId(state.activeCategory?.id).fastMap { it.id }
                val (toRemove, toAdd) = itemIds.partition { it in list }
                list.removeAll(toRemove)
                list.addAll(toAdd)
            }
            state.copy(selection = newSelection)
        }
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun updateActiveCategoryIndex(index: Int) {
        val newIndex = mutableState.updateAndGet { state ->
            state.copy(activeCategoryIndex = index)
        }
            .coercedActiveCategoryIndex

        when (uiPreferences.lastUsedLibraryMedia.get()) {
            "Anime" -> libraryPreferences.lastUsedCategoryAnime.set(newIndex)
            "Novel" -> libraryPreferences.lastUsedCategoryNovel.set(newIndex)
            else -> libraryPreferences.lastUsedCategory.set(newIndex)
        }
    }

    fun openChangeCategoryDialog() {
        viewModelScope.launchIO {
            // Create a copy of selected manga
            val mangaList = state.value.selectedManga

            // Hide the default category because it has a different behavior than the ones from db.
            val categories = state.value.displayedCategories.filter { it.id != 0L }

            // Get indexes of the common categories to preselect.
            val common = getCommonCategories(mangaList)
            // Get indexes of the mix categories to preselect.
            val mix = getMixCategories(mangaList)
            val preselected = categories
                .map {
                    when (it) {
                        in common -> CheckboxState.State.Checked(it)
                        in mix -> CheckboxState.TriState.Exclude(it)
                        else -> CheckboxState.State.None(it)
                    }
                }

            mutableState.update { it.copy(dialog = Dialog.ChangeCategory(mangaList, preselected)) }
        }
    }

    fun openDeleteMangaDialog() {
        mutableState.update { it.copy(dialog = Dialog.DeleteManga(state.value.selectedManga)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed interface Dialog {
        data object SettingsSheet : Dialog
        data class ChangeCategory(
            val manga: List<Manga>,
            val initialSelection: List<CheckboxState<Category>>,
        ) : Dialog

        data class DeleteManga(val manga: List<Manga>) : Dialog
    }

    private data class MergeFlowInputs(
        val itemPreferences: ItemPreferences,
        val memberships: Map<Long, Long>,
        val mergingEnabled: Boolean,
        val groupCounts: Map<Long, MergedGroupCounts>,
    )

    @Immutable
    private data class ItemPreferences(
        val downloadBadge: Boolean,
        val unreadBadge: Boolean,
        val localBadge: Boolean,
        val languageBadge: Boolean,
        val skipOutsideReleasePeriod: Boolean,

        val globalFilterDownloaded: Boolean,
        val filterDownloaded: TriState,
        val filterUnread: TriState,
        val filterStarted: TriState,
        val filterBookmarked: TriState,
        val filterCompleted: TriState,
        val filterIntervalCustom: TriState,
    )

    @Immutable
    data class LibraryData(
        val isInitialized: Boolean = false,
        val showSystemCategory: Boolean = false,
        val categories: List<Category> = emptyList(),
        val favorites: List<LibraryItem> = emptyList(),
        val tracksMap: Map</* Manga */ Long, List<Track>> = emptyMap(),
        val loggedInTrackerIds: Set<Long> = emptySet(),
    ) {
        val favoritesById by lazy { favorites.associateBy { it.id } }
    }

    @Immutable
    data class State(
        val isInitialized: Boolean = false,
        val isLoading: Boolean = true,
        val searchQuery: String? = null,
        val selection: Set</* Manga */ Long> = setOf(),
        val hasActiveFilters: Boolean = false,
        val showCategoryTabs: Boolean = false,
        val showMangaCount: Boolean = false,
        val showMangaContinueButton: Boolean = false,
        val dialog: Dialog? = null,
        val libraryData: LibraryData = LibraryData(),
        val memberships: Map<Long, Long> = emptyMap(),
        private val activeCategoryIndex: Int = 0,
        private val groupedFavorites: Map<Category, List</* LibraryItem */ Long>> = emptyMap(),
    ) {
        val displayedCategories: List<Category> = groupedFavorites.keys.toList()

        val coercedActiveCategoryIndex = activeCategoryIndex.coerceIn(
            minimumValue = 0,
            maximumValue = displayedCategories.lastIndex.coerceAtLeast(0),
        )

        val activeCategory: Category? = displayedCategories.getOrNull(coercedActiveCategoryIndex)

        val isLibraryEmpty = libraryData.favorites.isEmpty()

        val selectionMode = selection.isNotEmpty()

        val canMergeSelection: Boolean
            get() {
                if (selection.size < 2) return false
                val mangas = selectedManga
                if (mangas.size < 2) return false
                val sourceManager = Injekt.get<SourceManager>()
                val firstSrc = sourceManager.get(mangas.first().source)
                val firstType = when {
                    firstSrc is eu.kanade.tachiyomi.animesource.AnimeSource -> 2
                    firstSrc is eu.kanade.tachiyomi.novelsource.NovelSource || firstSrc is eu.kanade.tachiyomi.source.INovelSource -> 1
                    else -> 0
                }
                return mangas.all { m ->
                    val src = sourceManager.get(m.source)
                    val type = when {
                        src is eu.kanade.tachiyomi.animesource.AnimeSource -> 2
                        src is eu.kanade.tachiyomi.novelsource.NovelSource || src is eu.kanade.tachiyomi.source.INovelSource -> 1
                        else -> 0
                    }
                    type == firstType
                }
            }

        val selectionContainsMerged: Boolean get() = selection.any { it in memberships }

        val selectedManga by lazy { selection.mapNotNull { libraryData.favoritesById[it]?.libraryManga?.manga } }

        fun getItemsForCategoryId(categoryId: Long?): List<LibraryItem> {
            if (categoryId == null) return emptyList()
            val category = displayedCategories.find { it.id == categoryId } ?: return emptyList()
            return getItemsForCategory(category)
        }

        fun getItemsForCategory(category: Category): List<LibraryItem> {
            return groupedFavorites[category].orEmpty().mapNotNull { libraryData.favoritesById[it] }
        }

        fun getItemCountForCategory(category: Category): Int? {
            return if (showMangaCount || !searchQuery.isNullOrEmpty()) groupedFavorites[category]?.size else null
        }

        fun getToolbarTitle(
            defaultTitle: String,
            defaultCategoryTitle: String,
            page: Int,
        ): LibraryToolbarTitle {
            val category = displayedCategories.getOrNull(page) ?: return LibraryToolbarTitle(defaultTitle)
            val categoryName = category.let {
                if (it.isSystemCategory) defaultCategoryTitle else it.name
            }
            val title = if (showCategoryTabs) defaultTitle else categoryName
            val count = when {
                !showMangaCount -> null
                !showCategoryTabs -> getItemCountForCategory(category)
                // Whole library count
                else -> libraryData.favorites.size
            }
            return LibraryToolbarTitle(title, count)
        }
    }
}
