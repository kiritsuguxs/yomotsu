package eu.kanade.tachiyomi.animesource.model

import androidx.compose.runtime.Stable
import eu.kanade.tachiyomi.source.model.FilterList

@Stable
data class AnimeFilterList(override val list: List<AnimeFilter<*>>) : FilterList(list) {

    constructor(vararg fs: AnimeFilter<*>) : this(if (fs.isNotEmpty()) fs.asList() else emptyList())

    override fun equals(other: Any?): Boolean {
        return false
    }

    override fun hashCode(): Int {
        return list.hashCode()
    }
}
