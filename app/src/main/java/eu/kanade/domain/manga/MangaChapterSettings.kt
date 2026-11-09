package eu.kanade.domain.manga

import eu.kanade.domain.merge.GroupChapterSettings
import eu.kanade.domain.merge.withOwnerChapterFlags
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository

class MangaChapterSettings(mangaRepository: MangaRepository) : GroupChapterSettings<Manga>(
    flagsOf = { it.chapterFlags },
    withFlags = { copy(chapterFlags = it) },
    load = { mangaRepository.getMangaById(it) },
    write = { ids, flags -> mangaRepository.updateAll(ids.map { MangaUpdate(it, chapterFlags = flags) }) },
)

fun Manga.withGroupChapterFlags(members: Collection<Manga>): Manga =
    withOwnerChapterFlags(members, { it.chapterFlags }) { copy(chapterFlags = it) }

fun MergedChapterProvider.Group?.chapterSettingsOf(opened: Manga): Manga =
    opened.withGroupChapterFlags(this?.mangaById?.values.orEmpty())
