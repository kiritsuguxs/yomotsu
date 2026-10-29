package eu.kanade.tachiyomi.extension.pt.mangalivre

import eu.kanade.tachiyomi.source.model.Filter

open class TagFilter(name: String, private val tagValues: Array<Pair<String, String>>) :
    Filter.Select<String>(name, tagValues.map { it.first }.toTypedArray()) {
    fun toSlug(): String = tagValues[state].second
}

class OrderByFilter : TagFilter(
    "Ordem",
    arrayOf(
        "Mais Visualizados" to "popular",
        "Lançamentos" to "release",
        "Última Atualização" to "updated",
        "Melhor Avaliação" to "rating",
        "A-Z" to "title",
    )
)

class OrderDirectionFilter : TagFilter(
    "Direção",
    arrayOf(
        "↑ Decrescente" to "desc",
        "↓ Crescente" to "asc",
    )
)
