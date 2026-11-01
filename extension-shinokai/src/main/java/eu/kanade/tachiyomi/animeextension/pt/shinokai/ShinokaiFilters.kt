package eu.kanade.tachiyomi.animeextension.pt.shinokai

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object ShinokaiFilters {
    open class SelectFilter(
        name: String,
        private val vals: Array<Pair<String, String>>,
        state: Int = 0,
    ) : AnimeFilter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
        val selected: String
            get() = vals[state].second
    }

    class AudioFilter : SelectFilter(
        "Tipo de Áudio",
        arrayOf(
            "Todos" to "",
            "Dublado" to "dub",
            "Legendado" to "sub",
        ),
    )

    class GenreFilter : SelectFilter(
        "Gênero",
        arrayOf(
            "Todos" to "",
            "Ação" to "acao",
            "Aventura" to "aventura",
            "Comédia" to "comedia",
            "Drama" to "drama",
            "Fantasia" to "fantasia",
            "Ficção Científica" to "ficcao-cientifica",
            "Isekai" to "isekai",
            "Mistério" to "misterio",
            "Romance" to "romance",
            "Shounen" to "shounen",
            "Slice of Life" to "slice-of-life",
            "Sobrenatural" to "sobrenatural",
            "Suspense" to "suspense",
        ),
    )

    class OrderFilter : SelectFilter(
        "Ordenar Por",
        arrayOf(
            "Mais Populares" to "popular",
            "Lançamentos Recentes" to "recent",
            "Melhor Avaliados" to "rating",
            "Nome (A-Z)" to "name_asc",
        ),
    )

    fun getFilterList(): AnimeFilterList = AnimeFilterList(
        AudioFilter(),
        GenreFilter(),
        AnimeFilter.Separator(),
        OrderFilter(),
    )
}
