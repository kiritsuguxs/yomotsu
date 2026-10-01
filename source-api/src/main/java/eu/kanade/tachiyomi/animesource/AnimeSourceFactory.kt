package eu.kanade.tachiyomi.animesource

/**
 * A factory for creating sources at runtime.
 */
interface AnimeSourceFactory : eu.kanade.tachiyomi.source.SourceFactory {
    /**
     * Create a new copy of the sources
     * @return The created sources
     */
    override fun createSources(): List<AnimeSource>
}
