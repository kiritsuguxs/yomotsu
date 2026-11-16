package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mihon.core.viewmodel.StateViewModel
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.preference.toggle
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

abstract class SearchViewModel(
    initialState: State = State(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StateViewModel<SearchViewModel.State>(initialState) {

    private val searchGeneration = AtomicLong(0L)
    private var activeSearchSemaphore = Semaphore(MAX_CONCURRENT_SEARCHES)
    private var searchJob: Job? = null

    private val enabledLanguages = sourcePreferences.enabledLanguages.get()
    private val disabledSources = sourcePreferences.disabledSources.get()
    protected val pinnedSources = sourcePreferences.pinnedSources.get()

    private var lastQuery: String? = null
    private var lastSourceFilter: SourceFilter? = null

    protected var extensionFilter: String? = null

    open val sortComparator = { map: Map<Source, SearchItemResult> ->
        compareBy<Source>(
            { (map[it] as? SearchItemResult.Success)?.isEmpty ?: true },
            { "${it.id}" !in pinnedSources },
            { "${it.name.lowercase()} (${it.lang})" },
        )
    }

    init {
        viewModelScope.launch {
            preferences.globalSearchFilterState.changes().collectLatest { state ->
                mutableState.update { it.copy(onlyShowHasResults = state) }
            }
        }
    }

    @Composable
    fun getManga(initialManga: Manga): androidx.compose.runtime.State<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { manga ->
                    value = manga
                }
        }
    }

    open fun getEnabledSources(): List<Source> {
        return sourceManager.getAll()
            .filter { it !is eu.kanade.tachiyomi.source.INovelSource && it !is eu.kanade.tachiyomi.animesource.AnimeSource }
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
            .sortedWith(
                compareBy(
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }

    private fun getSelectedSources(): List<Source> {
        val enabledSources = getEnabledSources()

        val filter = extensionFilter
        if (filter.isNullOrEmpty()) {
            return enabledSources
        }

        return extensionManager.installedExtensionsFlow.value
            .filter { it.pkgName == filter }
            .flatMap { it.sources }
            .filter { it in enabledSources }
    }

    fun updateSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setSourceFilter(filter: SourceFilter) {
        mutableState.update { it.copy(sourceFilter = filter) }
        search()
    }

    fun toggleFilterResults() {
        preferences.globalSearchFilterState.toggle()
    }

    fun search() {
        val query = state.value.searchQuery
        val sourceFilter = state.value.sourceFilter

        if (query.isNullOrBlank()) return

        val sameQuery = this.lastQuery == query
        if (sameQuery && this.lastSourceFilter == sourceFilter) {
            if (searchJob?.isActive == true) return

            val retryTargets = state.value.items
                .filterValues { it is SearchItemResult.Error || it is SearchItemResult.Loading }
                .keys
                .toList()
            if (retryTargets.isEmpty()) return

            val generation = searchGeneration.get()
            val semaphore = activeSearchSemaphore
            mutableState.update { current ->
                val updatedItems = current.items.mapValues { (source, result) ->
                    if (source in retryTargets) SearchItemResult.Loading else result
                }
                current.copy(items = updatedItems.toSortedMap(sortComparator(updatedItems)))
            }
            searchJob = launchSourceSearches(
                generation = generation,
                sources = retryTargets,
                query = query,
                semaphore = semaphore,
            )
            return
        }

        this.lastQuery = query
        this.lastSourceFilter = sourceFilter

        searchJob?.cancel()
        val generation = searchGeneration.incrementAndGet()
        val semaphore = Semaphore(MAX_CONCURRENT_SEARCHES)
        activeSearchSemaphore = semaphore

        val sources = getSelectedSources()
        val existingResults = state.value.items
        val initialItems = if (sameQuery) {
            sources.associateWith {
                (existingResults[it] as? SearchItemResult.Success) ?: SearchItemResult.Loading
            }
        } else {
            sources.associateWith { SearchItemResult.Loading }
        }
        updateItems(initialItems)

        val sourcesToSearch = initialItems
            .filterValues { it is SearchItemResult.Loading }
            .keys
            .toList()
        if (sourcesToSearch.isEmpty()) return

        searchJob = launchSourceSearches(
            generation = generation,
            sources = sourcesToSearch,
            query = query,
            semaphore = semaphore,
        )
    }

    fun retrySource(source: Source) {
        val query = state.value.searchQuery
        if (query.isNullOrBlank()) return
        if (state.value.items[source] !is SearchItemResult.Error) return

        val generation = searchGeneration.get()
        val semaphore = activeSearchSemaphore
        updateItemIfCurrent(generation, source, SearchItemResult.Loading)

        viewModelScope.launch(ioDispatcher) {
            executeSourceSearch(
                generation = generation,
                source = source,
                query = query,
                semaphore = semaphore,
            )
        }
    }

    private fun launchSourceSearches(
        generation: Long,
        sources: List<Source>,
        query: String,
        semaphore: Semaphore,
    ): Job {
        return viewModelScope.launch(ioDispatcher) {
            sources.map { source ->
                async {
                    executeSourceSearch(
                        generation = generation,
                        source = source,
                        query = query,
                        semaphore = semaphore,
                    )
                }
            }.awaitAll()
        }
    }

    private suspend fun executeSourceSearch(
        generation: Long,
        source: Source,
        query: String,
        semaphore: Semaphore,
    ) {
        var attempt = 0
        while (true) {
            if (!currentCoroutineContext().isActive || searchGeneration.get() != generation) return

            val attemptStartMs = System.currentTimeMillis()
            var shouldRetry = false
            try {
                val titles = semaphore.withPermit {
                    if (!currentCoroutineContext().isActive || searchGeneration.get() != generation) return

                    val page = withTimeout(PER_SOURCE_TIMEOUT_MS) {
                        withContext(ioDispatcher) {
                            if (source is eu.kanade.tachiyomi.animesource.AnimeCatalogueSource) {
                                source.getSearchAnime(1, query, source.getFilterList())
                            } else {
                                source.getSearchManga(1, query, source.getFilterList())
                            }
                        }
                    }

                    val mapped = page.mangas
                        .map { it.toDomainManga(source.id) }
                        .distinctBy { it.url }

                    if (mapped.isEmpty()) {
                        emptyList()
                    } else {
                        networkToLocalManga(mapped)
                    }
                }

                if (currentCoroutineContext().isActive && searchGeneration.get() == generation) {
                    updateItemIfCurrent(generation, source, SearchItemResult.Success(titles))
                }
                return
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    if (currentCoroutineContext().isActive && searchGeneration.get() == generation) {
                        updateItemIfCurrent(generation, source, SearchItemResult.Error(e))
                    }
                    return
                }
                throw e
            } catch (e: Throwable) {
                if (!currentCoroutineContext().isActive || searchGeneration.get() != generation) return

                val elapsedMs = System.currentTimeMillis() - attemptStartMs
                val maxRetriesForError = if (elapsedMs < FAST_FAILURE_THRESHOLD_MS) {
                    MAX_FAST_TRANSIENT_RETRIES
                } else {
                    MAX_SLOW_TRANSIENT_RETRIES
                }
                if (attempt < maxRetriesForError && isTransientError(e)) {
                    shouldRetry = true
                } else {
                    updateItemIfCurrent(generation, source, SearchItemResult.Error(e))
                    return
                }
            }

            if (shouldRetry) {
                attempt++
                delay(BASE_RETRY_DELAY_MS * attempt)
            }
        }
    }

    private fun isTransientError(error: Throwable): Boolean {
        if (error is CancellationException) return false
        if (error is UnsupportedOperationException) return false
        var current: Throwable? = error
        while (current != null) {
            if (current is IOException) return true
            if (current is HttpException) {
                return current.code == 408 || current.code == 429 || current.code in 500..599
            }
            val msg = current.message
            if (msg != null && (
                    msg.contains("timed out", ignoreCase = true) ||
                        msg.contains("timeout", ignoreCase = true) ||
                        msg.contains("cloudflare", ignoreCase = true) ||
                        msg.contains("429", ignoreCase = true)
                    )
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun updateItems(items: Map<Source, SearchItemResult>) {
        mutableState.update {
            it.copy(
                items = items
                    .toSortedMap(sortComparator(items)),
            )
        }
    }

    private fun updateItemIfCurrent(generation: Long, source: Source, result: SearchItemResult) {
        mutableState.update { current ->
            if (searchGeneration.get() != generation) return@update current
            val updatedItems = current.items + (source to result)
            current.copy(
                items = updatedItems.toSortedMap(sortComparator(updatedItems)),
            )
        }
    }

    fun setMigrateDialog(currentId: Long, target: Manga) {
        viewModelScope.launchIO {
            val current = getManga.await(currentId) ?: return@launchIO
            mutableState.update { it.copy(dialog = Dialog.Migrate(target, current)) }
        }
    }

    fun clearDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    @Immutable
    data class State(
        val from: Manga? = null,
        val searchQuery: String? = null,
        val sourceFilter: SourceFilter = SourceFilter.PinnedOnly,
        val onlyShowHasResults: Boolean = false,
        val items: Map<Source, SearchItemResult> = mapOf(),
        val dialog: Dialog? = null,
    ) {
        val progress: Int = items.count { it.value !is SearchItemResult.Loading }
        val total: Int = items.size
        val filteredItems = items.filter { (_, result) -> result.isVisible(onlyShowHasResults) }
    }

    sealed interface Dialog {
        data class Migrate(val target: Manga, val current: Manga) : Dialog
    }

    companion object {
        private const val MAX_CONCURRENT_SEARCHES = 6
        private const val PER_SOURCE_TIMEOUT_MS = 135_000L
        private const val FAST_FAILURE_THRESHOLD_MS = 35_000L
        private const val MAX_FAST_TRANSIENT_RETRIES = 2
        private const val MAX_SLOW_TRANSIENT_RETRIES = 1
        private const val BASE_RETRY_DELAY_MS = 1_500L
    }
}

enum class SourceFilter {
    All,
    PinnedOnly,
}

sealed interface SearchItemResult {
    data object Loading : SearchItemResult

    data class Error(
        val throwable: Throwable,
    ) : SearchItemResult

    data class Success(
        val result: List<Manga>,
    ) : SearchItemResult {
        val isEmpty: Boolean
            get() = result.isEmpty()
    }

    fun isVisible(onlyShowHasResults: Boolean): Boolean {
        return !onlyShowHasResults || (this is Success && !this.isEmpty)
    }
}
