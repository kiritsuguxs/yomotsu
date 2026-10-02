package eu.kanade.domain.extension.interactor

import eu.kanade.domain.extension.model.Extensions
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class GetAnimeExtensionsByType(
    private val preferences: SourcePreferences,
    private val extensionManager: ExtensionManager,
) {

    fun subscribe(): Flow<Extensions> {
        val showNsfwSources = preferences.showNsfwSource.get()

        return combine(
            preferences.enabledAnimeLanguages.changes(),
            extensionManager.installedExtensionsFlow,
            extensionManager.untrustedExtensionsFlow,
            extensionManager.availableExtensionsFlow,
        ) { enabledLanguages, _installed, _untrusted, _available ->
            val (updates, installed) = _installed
                .filter { (showNsfwSources || !it.isNsfw) && isAnimeExtension(it) }
                .sortedWith(
                    compareBy<Extension.Installed> { !it.isObsolete }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
                )
                .partition { it.hasUpdate }

            val untrusted = _untrusted.filter { isAnimeExtension(it) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            val available = _available
                .filter { extension ->
                    _installed.none { it.pkgName == extension.pkgName } &&
                        _untrusted.none { it.pkgName == extension.pkgName } &&
                        (showNsfwSources || !extension.isNsfw) &&
                        isAnimeExtension(extension)
                }
                .flatMap { ext ->
                    val filteredSources = ext.sources.filter { it.lang in enabledLanguages }
                    if (filteredSources.isEmpty()) {
                        listOf(ext)
                    } else {
                        filteredSources.map {
                            ext.copy(
                                name = if (ext.sources.size > 1) it.name else ext.name,
                                lang = it.lang,
                                pkgName = if (ext.sources.size > 1) "${ext.pkgName}-${it.id}" else ext.pkgName,
                                sources = listOf(it),
                            )
                        }
                    }
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            Extensions(updates, installed, available, untrusted)
        }
    }

    private fun isAnimeExtension(extension: Extension): Boolean {
        if (extension.isAnime) return true
        if (extension.pkgName.contains("animeextension") || extension.pkgName.startsWith("eu.kanade.tachiyomi.animeextension")) return true
        if (extension is Extension.Installed && extension.sources.any { it is eu.kanade.tachiyomi.animesource.AnimeSource || it.javaClass.name.contains("anime", ignoreCase = true) }) return true
        return false
    }
}
