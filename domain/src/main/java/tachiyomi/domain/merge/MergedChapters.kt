package tachiyomi.domain.merge

class MergedChapters<T>(
    val chapters: List<T>,
    val units: List<ChapterUnit>,
)

class ChapterUnit(val chapterId: Long, val unit: Int, val copyOrder: Int)

fun <T> unstitchedChapters(chapters: List<T>, id: (T) -> Long) = MergedChapters(
    chapters,
    chapters.mapIndexed { position, chapter -> ChapterUnit(id(chapter), position, 0) },
)
