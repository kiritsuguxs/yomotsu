package eu.kanade.tachiyomi.ui.browse.extension

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.core.viewmodel.StateViewModel
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

enum class ExtensionFilterType {
    MANGA,
    ANIME,
    NOVEL,
}

class ExtensionFilterViewModel(
    val type: ExtensionFilterType = ExtensionFilterType.MANGA,
    private val preferences: SourcePreferences = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val novelExtensionManager: NovelExtensionManager = Injekt.get(),
) : StateViewModel<ExtensionFilterState>(ExtensionFilterState.Loading) {

    companion object {
        val TYPE_KEY = CreationExtras.Key<ExtensionFilterType>()

        val Factory = viewModelFactory {
            initializer {
                ExtensionFilterViewModel(
                    type = get(TYPE_KEY) ?: ExtensionFilterType.MANGA,
                )
            }
        }
    }

    private val _events: Channel<ExtensionFilterEvent> = Channel()
    val events: Flow<ExtensionFilterEvent> = _events.receiveAsFlow()

    init {
        val (langPref, availableLanguagesFlow) = when (type) {
            ExtensionFilterType.MANGA -> {
                preferences.enabledLanguages to extensionManager.availableExtensionsFlow.map { list ->
                    list.filter { !it.isAnime && !it.pkgName.contains("animeextension") }
                        .flatMap { it.sources.map { s -> s.lang } }
                        .distinct()
                }
            }
            ExtensionFilterType.ANIME -> {
                preferences.enabledAnimeLanguages to extensionManager.availableExtensionsFlow.map { list ->
                    list.filter { it.isAnime || it.pkgName.contains("animeextension") }
                        .flatMap { it.sources.map { s -> s.lang } }
                        .distinct()
                }
            }
            ExtensionFilterType.NOVEL -> {
                preferences.enabledNovelLanguages to novelExtensionManager.availableExtensions.map { list ->
                    list.map { it.plugin.lang }.distinct()
                }
            }
        }

        viewModelScope.launch {
            combine(
                availableLanguagesFlow,
                langPref.changes(),
            ) { availableLangs, enabledLanguages ->
                val sortedLangs = availableLangs.sortedWith(
                    compareBy<String> { it !in enabledLanguages }.then(LocaleHelper.comparator),
                )
                sortedLangs to enabledLanguages
            }
                .catch { throwable ->
                    logcat(LogPriority.ERROR, throwable)
                    _events.send(ExtensionFilterEvent.FailedFetchingLanguages)
                }
                .collectLatest { (extensionLanguages, enabledLanguages) ->
                    mutableState.update {
                        ExtensionFilterState.Success(
                            languages = extensionLanguages,
                            enabledLanguages = enabledLanguages,
                        )
                    }
                }
        }
    }

    fun toggle(language: String) {
        val pref = when (type) {
            ExtensionFilterType.MANGA -> preferences.enabledLanguages
            ExtensionFilterType.ANIME -> preferences.enabledAnimeLanguages
            ExtensionFilterType.NOVEL -> preferences.enabledNovelLanguages
        }
        val isEnabled = language in pref.get()
        pref.getAndSet { enabled ->
            if (isEnabled) enabled.minus(language) else enabled.plus(language)
        }
    }
}

sealed interface ExtensionFilterEvent {
    data object FailedFetchingLanguages : ExtensionFilterEvent
}

sealed interface ExtensionFilterState {

    @Immutable
    data object Loading : ExtensionFilterState

    @Immutable
    data class Success(
        val languages: List<String>,
        val enabledLanguages: Set<String> = setOf(),
    ) : ExtensionFilterState {

        val isEmpty: Boolean
            get() = languages.isEmpty()
    }
}
