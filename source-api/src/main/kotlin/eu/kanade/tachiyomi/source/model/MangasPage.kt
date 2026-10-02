package eu.kanade.tachiyomi.source.model

open class MangasPage(open val mangas: List<SManga>, open val hasNextPage: Boolean) {

    @Deprecated("MangasPage is now a regular class")
    open operator fun component1(): List<SManga> = mangas

    @Deprecated("MangasPage is now a regular class")
    open operator fun component2(): Boolean = hasNextPage

    @Deprecated("MangasPage is now a regular class")
    fun copy(
        mangas: List<SManga> = this.mangas,
        hasNextPage: Boolean = this.hasNextPage,
    ): MangasPage = MangasPage(
        mangas = mangas,
        hasNextPage = hasNextPage,
    )
}
