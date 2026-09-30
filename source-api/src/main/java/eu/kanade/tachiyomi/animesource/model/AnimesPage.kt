package eu.kanade.tachiyomi.animesource.model

import eu.kanade.tachiyomi.source.model.MangasPage

typealias AnimesPage = MangasPage

val MangasPage.animes: List<SAnime>
    get() = mangas.filterIsInstance<SAnime>()
