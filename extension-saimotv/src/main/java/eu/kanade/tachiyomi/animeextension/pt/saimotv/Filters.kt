package eu.kanade.tachiyomi.animeextension.pt.saimotv

import eu.kanade.tachiyomi.animesource.model.AnimeFilter

open class ContentTypeFilter(name: String, val values: Array<Pair<String, String>>) :
    AnimeFilter.Select<String>(name, values.map { it.first }.toTypedArray()) {
    fun toPath(): String = values[state].second
}

class TipoFilter : ContentTypeFilter(
    "Tipo de Conteúdo",
    arrayOf(
        "Filmes" to "filmes",
        "Séries" to "series",
        "Animes" to "animes",
        "Doramas" to "doramas",
    )
)

open class GenreFilter(name: String, val values: Array<Pair<String, String>>) :
    AnimeFilter.Select<String>(name, values.map { it.first }.toTypedArray()) {
    fun toSlug(): String = values[state].second
}

class GeneroFilter : GenreFilter(
    "Gênero",
    arrayOf(
        "Todos" to "",
        "Ação" to "acao",
        "Animação" to "animacao",
        "Aventura" to "aventura",
        "Comédia" to "comedia",
        "Crime" to "crime",
        "Documentário" to "documentario",
        "Drama" to "drama",
        "Família" to "familia",
        "Fantasia" to "fantasia",
        "Ficção científica" to "ficcao-cientifica",
        "História" to "historia",
        "Mistério" to "misterio",
        "Romance" to "romance",
        "Suspense" to "suspense",
        "Terror" to "terror",
    )
)
