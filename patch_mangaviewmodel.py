import re

with open('app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace('import eu.kanade.tachiyomi.extension.novel.download.NovelDownloadManager', '''import eu.kanade.tachiyomi.extension.novel.download.NovelDownloadManager
import eu.kanade.tachiyomi.data.animedownload.AnimeDownloadManager''')

content = content.replace('private val downloadManager: DownloadManager = Injekt.get(),', '''private val downloadManager: DownloadManager = Injekt.get(),
    private val animeDownloadManager: AnimeDownloadManager = Injekt.get(),''')

# 1. Update download check logic (around line 620)
# val isNovelDownloaded = NovelDownloadManager.isChapterDownloaded(manga.id, chapter.id)
download_check_repl = '''val activeDownload = if (source is eu.kanade.tachiyomi.animesource.AnimeSource) {
                animeDownloadManager.getQueuedDownloadOrNull(chapter.id)
            } else {
                downloadManager.getQueuedDownloadOrNull(chapter.id)
            }
            val isNovelDownloaded = NovelDownloadManager.isChapterDownloaded(manga.id, chapter.id)
            val isNovelDownloading = NovelDownloadManager.isChapterDownloading(chapter.id)
            val isAnimeDownloaded = if (source is eu.kanade.tachiyomi.animesource.AnimeSource) animeDownloadManager.isChapterDownloaded(chapter.name, chapter.scanlator, manga.title, source) else false
            val downloaded = if (isLocal || isNovelDownloaded || isAnimeDownloaded) {
                true
            } else if (source is eu.kanade.tachiyomi.animesource.AnimeSource) {
                false // already checked isAnimeDownloaded
            } else {
                downloadManager.isChapterDownloaded(
                    chapter.name,
                    chapter.scanlator,
                    manga.title,
                    manga.source,
                )
            }'''
content = re.sub(
    r'val activeDownload = if \(isLocal\) \{.*?\} else \{.*?downloadManager\.isChapterDownloaded\([^)]+\).*?\}',
    download_check_repl,
    content,
    flags=re.DOTALL
)

# 2. Download Chapters (around line 746 and 963)
# if (successState.source is eu.kanade.tachiyomi.source.INovelSource) {
dl_chapters_repl = '''if (successState.source is eu.kanade.tachiyomi.source.INovelSource) {
                NovelDownloadManager.downloadChapters(successState.manga, chapters)
            } else if (successState.source is eu.kanade.tachiyomi.animesource.AnimeSource) {
                if (startNow) {
                    val chapterId = chapters.single().id
                    animeDownloadManager.startDownloadNow(chapterId)
                } else {
                    animeDownloadManager.downloadChapters(successState.manga, chapters)
                }
            } else {'''
content = content.replace('''if (successState.source is eu.kanade.tachiyomi.source.INovelSource) {
                NovelDownloadManager.downloadChapters(successState.manga, chapters)
            } else {''', dl_chapters_repl)

# 3. Delete Chapters
del_chapters_repl = '''if (state.source is eu.kanade.tachiyomi.source.INovelSource) {
                        chapters.forEach { NovelDownloadManager.deleteChapter(state.manga.id, it.id) }
                    } else if (state.source is eu.kanade.tachiyomi.animesource.AnimeSource) {
                        animeDownloadManager.deleteChapters(
                            chapters,
                            state.manga,
                            state.source,
                        )
                    } else {'''
content = content.replace('''if (state.source is eu.kanade.tachiyomi.source.INovelSource) {
                        chapters.forEach { NovelDownloadManager.deleteChapter(state.manga.id, it.id) }
                    } else {''', del_chapters_repl)

with open('app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaViewModel.kt', 'w') as f:
    f.write(content)
