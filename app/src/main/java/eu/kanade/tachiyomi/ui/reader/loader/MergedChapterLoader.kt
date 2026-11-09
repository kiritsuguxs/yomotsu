package eu.kanade.tachiyomi.ui.reader.loader

import android.content.Context
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

/**
 * Merge-aware loader. Holds one [ChapterLoader] per merged source, keyed by the chapter's
 * own manga id, so a single reader session can load pages across the whole merge group.
 */
class MergedChapterLoader(
    private val context: Context,
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
    private val canLoadAhead: () -> Boolean,
    private val mangaById: Map<Long, Manga>,
    private val sourceManager: SourceManager,
) {

    private val loaders = HashMap<Long, ChapterLoader>()

    suspend fun loadChapter(chapter: ReaderChapter) {
        val mangaId = chapter.chapter.manga_id ?: return
        loaderFor(mangaId).loadChapter(chapter)
    }

    private fun loaderFor(mangaId: Long): ChapterLoader = loaders.getOrPut(mangaId) {
        val manga = mangaById.getValue(mangaId)
        ChapterLoader(
            context = context,
            downloadManager = downloadManager,
            downloadProvider = downloadProvider,
            manga = manga,
            source = sourceManager.getOrStub(manga.source),
            canLoadAhead = canLoadAhead,
        )
    }
}
