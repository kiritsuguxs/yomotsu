package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.service.SourceManager
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `concurrent finishing sources never lose updates or stay stuck in loading`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Default)

        val sources = (1L..24L).map { id ->
            FakeSource(
                id = id,
                name = "Source $id",
                lang = "en",
                onSearch = { _, query, _ ->
                    // Minimal stagger to force heavy simultaneous completion across worker threads
                    delay(id % 3)
                    MangasPage(
                        mangas = listOf(
                            SManga.create().apply {
                                url = "/manga/$id/$query"
                                title = "Title $id"
                            },
                        ),
                        hasNextPage = false,
                    )
                },
            )
        }

        val viewModel = createViewModel(sources = sources, ioDispatcher = Dispatchers.Default)
        viewModel.updateSearchQuery("solo")
        viewModel.search()

        withTimeout(10_000L) {
            viewModel.state.first { it.total == 24 && it.progress == 24 }
        }

        val items = viewModel.state.value.items
        items.shouldHaveSize(24)
        sources.forEach { source ->
            val result = items[source].shouldBeInstanceOf<SearchItemResult.Success>()
            result.result.size shouldBe 1
        }
    }

    @Test
    fun `new search is not starved by hung previous search and ignores stale results`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Default)

        val firstSearchEntered = CompletableDeferred<Unit>()
        val secondSearchCalls = AtomicInteger(0)

        val slowThenFastSources = (1L..8L).map { id ->
            FakeSource(
                id = id,
                name = "HungFirst $id",
                lang = "en",
                onSearch = { _, query, _ ->
                    if (query == "first") {
                        if (id == 1L) firstSearchEntered.complete(Unit)
                        awaitCancellation()
                    } else {
                        secondSearchCalls.incrementAndGet()
                        MangasPage(emptyList(), hasNextPage = false)
                    }
                },
            )
        }

        val viewModel = createViewModel(sources = slowThenFastSources, ioDispatcher = Dispatchers.Default)
        viewModel.updateSearchQuery("first")
        viewModel.search()

        withTimeout(5_000L) {
            firstSearchEntered.await()
        }

        viewModel.updateSearchQuery("second")
        viewModel.search()

        withTimeout(5_000L) {
            viewModel.state.first { it.total == 8 && it.progress == 8 }
        }

        secondSearchCalls.get() shouldBe 8
        viewModel.state.value.items.values.all { it is SearchItemResult.Success } shouldBe true
    }

    @Test
    fun `transient errors automatically retry and succeed`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val ioAttempts = AtomicInteger(0)
        val http503Attempts = AtomicInteger(0)

        val sources = listOf(
            FakeSource(
                id = 1L,
                name = "TransientIO",
                lang = "en",
                onSearch = { _, _, _ ->
                    if (ioAttempts.incrementAndGet() == 1) {
                        throw IOException("Cloudflare challenge timed out")
                    }
                    MangasPage(
                        mangas = listOf(
                            SManga.create().apply {
                                url = "/ok-io"
                                title = "Recovered IO"
                            },
                        ),
                        hasNextPage = false,
                    )
                },
            ),
            FakeSource(
                id = 2L,
                name = "Transient503",
                lang = "en",
                onSearch = { _, _, _ ->
                    if (http503Attempts.incrementAndGet() <= 2) {
                        throw HttpException(503)
                    }
                    MangasPage(
                        mangas = listOf(
                            SManga.create().apply {
                                url = "/ok-503"
                                title = "Recovered 503"
                            },
                        ),
                        hasNextPage = false,
                    )
                },
            ),
        )

        val viewModel = createViewModel(sources = sources, ioDispatcher = testDispatcher)
        viewModel.updateSearchQuery("leveling")
        viewModel.search()

        advanceUntilIdle()

        ioAttempts.get() shouldBe 2
        http503Attempts.get() shouldBe 3
        viewModel.state.value.progress shouldBe 2
        viewModel.state.value.items.values.all { it is SearchItemResult.Success } shouldBe true
    }

    @Test
    fun `retrySource and re-submitting same query retry failed sources while preserving successes`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val shouldFailFirst = AtomicInteger(1)
        val shouldFailSecond = AtomicInteger(1)

        val healthySource = FakeSource(
            id = 1L,
            name = "Healthy",
            lang = "en",
            onSearch = { _, _, _ ->
                MangasPage(
                    mangas = listOf(
                        SManga.create().apply {
                            url = "/healthy"
                            title = "Healthy"
                        },
                    ),
                    hasNextPage = false,
                )
            },
        )
        val flakyNonTransientSource = FakeSource(
            id = 2L,
            name = "FlakyTapRetry",
            lang = "en",
            onSearch = { _, _, _ ->
                if (shouldFailFirst.getAndDecrement() > 0) {
                    throw HttpException(404)
                }
                MangasPage(emptyList(), hasNextPage = false)
            },
        )
        val secondFailedSource = FakeSource(
            id = 3L,
            name = "FlakyReSearch",
            lang = "en",
            onSearch = { _, _, _ ->
                if (shouldFailSecond.getAndDecrement() > 0) {
                    throw HttpException(403)
                }
                MangasPage(emptyList(), hasNextPage = false)
            },
        )

        val viewModel = createViewModel(
            sources = listOf(healthySource, flakyNonTransientSource, secondFailedSource),
            ioDispatcher = testDispatcher,
        )
        viewModel.updateSearchQuery("omniscient")
        viewModel.search()
        advanceUntilIdle()

        viewModel.state.value.items[healthySource].shouldBeInstanceOf<SearchItemResult.Success>()
        viewModel.state.value.items[flakyNonTransientSource].shouldBeInstanceOf<SearchItemResult.Error>()
        viewModel.state.value.items[secondFailedSource].shouldBeInstanceOf<SearchItemResult.Error>()

        viewModel.retrySource(flakyNonTransientSource)
        advanceUntilIdle()
        viewModel.state.value.items[flakyNonTransientSource].shouldBeInstanceOf<SearchItemResult.Success>()

        // Pressing search again on the same query retries remaining Error sources without resetting Success items
        viewModel.search()
        advanceUntilIdle()
        viewModel.state.value.items[healthySource].shouldBeInstanceOf<SearchItemResult.Success>()
        viewModel.state.value.items[flakyNonTransientSource].shouldBeInstanceOf<SearchItemResult.Success>()
        viewModel.state.value.items[secondFailedSource].shouldBeInstanceOf<SearchItemResult.Success>()
    }

    private fun createViewModel(
        sources: List<Source>,
        ioDispatcher: CoroutineDispatcher,
    ): SearchViewModel {
        val enabledLangsPref = mockPref(setOf("en"))
        val emptySetPref = mockPref(emptySet<String>())
        val filterStatePref = mockPref(false)

        val sourcePreferences = mockk<SourcePreferences> {
            every { enabledLanguages } returns enabledLangsPref
            every { disabledSources } returns emptySetPref
            every { pinnedSources } returns emptySetPref
            every { globalSearchFilterState } returns filterStatePref
        }
        val sourceManager = mockk<SourceManager> {
            every { getAll() } returns sources
        }
        val extensionManager = mockk<ExtensionManager> {
            every { installedExtensionsFlow } returns MutableStateFlow(emptyList())
        }
        val networkToLocalManga = mockk<NetworkToLocalManga> {
            coEvery { invoke(any<List<tachiyomi.domain.manga.model.Manga>>()) } answers { firstArg() }
        }
        val getManga = mockk<GetManga> {
            every { subscribe(any(), any()) } returns flowOf(null)
        }

        return object : SearchViewModel(
            initialState = State(),
            sourcePreferences = sourcePreferences,
            sourceManager = sourceManager,
            extensionManager = extensionManager,
            networkToLocalManga = networkToLocalManga,
            getManga = getManga,
            preferences = sourcePreferences,
            ioDispatcher = ioDispatcher,
        ) {}
    }

    private fun <T> mockPref(value: T): Preference<T> {
        return mockk {
            every { get() } returns value
            every { changes() } returns flowOf(value)
        }
    }

    private class FakeSource(
        override val id: Long,
        override val name: String,
        override val lang: String,
        private val onSearch: suspend (Int, String, FilterList) -> MangasPage,
    ) : Source {
        override val supportsLatest: Boolean = true
        override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), false)
        override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)
        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
            onSearch(page, query, filters)
        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate = SMangaUpdate(manga, chapters)
        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
    }
}
