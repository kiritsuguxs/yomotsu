package eu.kanade.tachiyomi.extension.pt.baozinho

import eu.kanade.tachiyomi.source.model.Filter

open class TagFilter(name: String, private val tagValues: Array<Pair<String, String>>) :
    Filter.Select<String>(name, tagValues.map { it.first }.toTypedArray()) {
    fun toSlug(): String = tagValues[state].second
}

class GenreFilter : TagFilter(
    "Gênero / Tag",
    arrayOf(
        "Todos" to "",
        "Ação" to "acao",
        "Artes Marciais" to "artes-marciais",
        "Aventura" to "aventura",
        "Comédia" to "comedia",
        "Cultivo" to "cultivo",
        "Drama" to "drama",
        "Fantasia" to "fantasia",
        "Ficção Científica" to "ficcao-cientifica",
        "Isekai" to "isekai",
        "Murim" to "murim",
        "Reencarnação" to "reencarnacao",
        "Seinen" to "seinen",
        "Sistema de Jogo" to "sistema-de-jogo",
    )
)
