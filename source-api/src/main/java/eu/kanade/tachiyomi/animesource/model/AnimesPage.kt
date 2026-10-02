package eu.kanade.tachiyomi.animesource.model

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga

open class AnimesPage(open val animes: List<SAnime>, override val hasNextPage: Boolean) :
    MangasPage(animes, hasNextPage) {

    // SY -->
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnimesPage) return false

        if (mangas != other.mangas) return false
        if (hasNextPage != other.hasNextPage) return false

        return true
    }

    override fun hashCode(): Int {
        var result = mangas.hashCode()
        result = 31 * result + hasNextPage.hashCode()
        return result
    }
    // SY <--

    fun copy(animes: List<SAnime> = this.animes, hasNextPage: Boolean = this.hasNextPage): AnimesPage {
        return AnimesPage(animes, hasNextPage)
    }

    override fun toString(): String {
        return "AnimesPage(animes=$animes, hasNextPage=$hasNextPage)"
    }

    // KMK -->
    // Additional methods to mimic data class behavior
    override operator fun component1(): List<SAnime> = animes
    override operator fun component2(): Boolean = hasNextPage
    // KMK <--
}

val MangasPage.animes: List<SAnime>
    get() = mangas.filterIsInstance<SAnime>()
