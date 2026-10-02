package tachiyomi.data.source

import androidx.paging.PagingState
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.repository.SourcePagingSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SourceSearchPagingSource(
    source: Source,
    private val query: String,
    private val filters: FilterList,
) : BaseSourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return kotlinx.coroutines.withTimeout(30_000L) {
            if (source is eu.kanade.tachiyomi.animesource.AnimeCatalogueSource) {
                val animeFilters = filters as? eu.kanade.tachiyomi.animesource.model.AnimeFilterList
                    ?: source.getFilterList()
                source.getSearchAnime(currentPage, query, animeFilters)
            } else {
                source.getSearchManga(currentPage, query, filters)
            }
        }
    }
}

class SourcePopularPagingSource(source: Source) : BaseSourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return kotlinx.coroutines.withTimeout(30_000L) {
            if (source is eu.kanade.tachiyomi.animesource.AnimeCatalogueSource) {
                source.getPopularAnime(currentPage)
            } else {
                source.getPopularManga(currentPage)
            }
        }
    }
}

class SourceLatestPagingSource(source: Source) : BaseSourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        if (!source.supportsLatest) {
            throw UnsupportedOperationException("Esta fonte não suporta atualizações recentes")
        }
        return kotlinx.coroutines.withTimeout(30_000L) {
            if (source is eu.kanade.tachiyomi.animesource.AnimeCatalogueSource) {
                source.getLatestAnime(currentPage)
            } else {
                source.getLatestUpdates(currentPage)
            }
        }
    }
}

abstract class BaseSourcePagingSource(
    protected val source: Source,
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
) : SourcePagingSource() {

    private val seenManga = hashSetOf<String>()

    abstract suspend fun requestNextPage(currentPage: Int): MangasPage

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, Manga> {
        val page = params.key ?: 1

        return try {
            val mangasPage = withIOContext {
                requestNextPage(page.toInt())
                    .takeIf { it.mangas.isNotEmpty() }
                    ?: throw NoResultsException()
            }

            val manga = mangasPage.mangas
                .map { it.toDomainManga(source.id) }
                .filter { seenManga.add(it.url) }
                .let { networkToLocalManga(it) }

            LoadResult.Page(
                data = manga,
                prevKey = null,
                nextKey = if (mangasPage.hasNextPage) page + 1 else null,
            )
        } catch (e: Throwable) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Long, Manga>): Long? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey ?: anchorPage?.nextKey
        }
    }
}

class NoResultsException : Exception()
