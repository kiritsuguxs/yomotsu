import re

with open('app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt', 'r') as f:
    content = f.read()

replacement = '''    private fun openChapter(context: Context, chapter: Chapter, source: Source?) {
        if (source is eu.kanade.tachiyomi.source.INovelSource) {
            context.startActivity(eu.kanade.tachiyomi.ui.reader.NovelReaderActivity.newIntent(context, chapter.mangaId, chapter.id))
        } else if (source is eu.kanade.tachiyomi.animesource.AnimeSource) {
            eu.kanade.tachiyomi.ui.main.MainActivity.startPlayerActivity(context, chapter.mangaId, chapter.id, extPlayer = false)
        } else {
            context.startActivity(ReaderActivity.newIntent(context, chapter.mangaId, chapter.id))
        }
    }'''

content = re.sub(
    r'    private fun openChapter\(context: Context, chapter: Chapter, source: Source\?\) \{.*?    \}',
    replacement,
    content,
    flags=re.DOTALL
)

with open('app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt', 'w') as f:
    f.write(content)
