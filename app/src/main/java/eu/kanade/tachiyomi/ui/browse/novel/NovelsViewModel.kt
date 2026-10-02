package eu.kanade.tachiyomi.ui.browse.novel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.extension.novel.model.NovelPlugin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelsViewModel(
    private val manager: NovelExtensionManager = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
) : ViewModel() {

    private val _searchQuery = MutableStateFlow<String?>(null)
    val searchQuery = _searchQuery.asStateFlow()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                _searchQuery,
                manager.installedExtensions,
                manager.availableExtensions,
                preferences.enabledNovelLanguages.changes(),
                manager.isRefreshing,
            ) { query, installed, available, enabledLanguages, isRefreshing ->
                val filteredInstalled = installed.filter {
                    query.isNullOrBlank() || it.plugin.name.contains(query, ignoreCase = true)
                }
                val filteredAvailable = available.filter {
                    (query.isNullOrBlank() || it.plugin.name.contains(query, ignoreCase = true)) &&
                        matchesLanguage(it.plugin.lang, enabledLanguages)
                }
                val updates = filteredInstalled.filter { it.hasUpdate }
                val nonUpdates = filteredInstalled.filter { !it.hasUpdate }
                State(
                    isLoading = false,
                    isRefreshing = isRefreshing,
                    isEmpty = filteredInstalled.isEmpty() && filteredAvailable.isEmpty(),
                    updates = updates,
                    installed = nonUpdates,
                    available = filteredAvailable,
                )
            }.collect { newState ->
                _state.value = newState
            }
        }
    }

    private fun matchesLanguage(pluginLang: String, enabledLanguages: Set<String>): Boolean {
        val cleanEnabled = if ("all" in enabledLanguages) enabledLanguages - "all" else enabledLanguages
        if (cleanEnabled.isEmpty()) return false

        val normalized = pluginLang.trim().lowercase()
        val langCode = when {
            normalized.contains("portugu") || normalized == "pt" || normalized == "pt-br" -> "pt"
            normalized.contains("english") || normalized == "en" -> "en"
            normalized.contains("español") || normalized.contains("espanol") || normalized == "es" -> "es"
            normalized.contains("franç") || normalized.contains("franc") || normalized == "fr" -> "fr"
            normalized.contains("indonesia") || normalized == "id" -> "id"
            normalized.contains("polski") || normalized == "pl" -> "pl"
            normalized.contains("việt") || normalized.contains("viet") || normalized == "vi" -> "vi"
            normalized.contains("türk") || normalized.contains("turk") || normalized == "tr" -> "tr"
            normalized.contains("русский") || normalized == "ru" -> "ru"
            normalized.contains("укра") || normalized == "uk" -> "uk"
            normalized.contains("ไทย") || normalized == "th" -> "th"
            normalized.contains("عرب") || normalized == "ar" -> "ar"
            normalized.contains("中文") || normalized == "zh" -> "zh"
            normalized.contains("日本") || normalized == "ja" -> "ja"
            normalized.contains("한국") || normalized.contains("조선") || normalized == "ko" -> "ko"
            normalized.contains("multi") -> "multi"
            else -> normalized
        }

        if (langCode == "multi") {
            return cleanEnabled.any { it.equals("multi", ignoreCase = true) || it.equals("all", ignoreCase = true) }
        }

        return langCode in cleanEnabled ||
            cleanEnabled.any { it.equals(langCode, ignoreCase = true) } ||
            cleanEnabled.any { it.equals(pluginLang, ignoreCase = true) } ||
            cleanEnabled.any { it.equals(normalized, ignoreCase = true) } ||
            (langCode == "pt" && cleanEnabled.any { it.startsWith("pt", ignoreCase = true) || it.contains("portugu", ignoreCase = true) }) ||
            (langCode == "en" && cleanEnabled.any { it.equals("en", ignoreCase = true) || it.contains("english", ignoreCase = true) }) ||
            (langCode == "es" && cleanEnabled.any { it.equals("es", ignoreCase = true) || it.contains("español", ignoreCase = true) || it.contains("espanol", ignoreCase = true) || it.contains("spanish", ignoreCase = true) }) ||
            (langCode == "zh" && cleanEnabled.any { it.startsWith("zh", ignoreCase = true) || it.contains("chinese", ignoreCase = true) }) ||
            (langCode == "pl" && cleanEnabled.any { it.equals("pl", ignoreCase = true) || it.contains("polski", ignoreCase = true) || it.contains("polish", ignoreCase = true) })
    }

    fun search(query: String?) {
        _searchQuery.value = query
    }

    fun refresh() {
        manager.refreshAvailablePlugins()
    }

    fun installExtension(plugin: NovelPlugin) {
        manager.installPlugin(plugin) {
            // Callback when install finishes
        }
    }

    fun updateExtension(plugin: NovelPlugin) {
        manager.installPlugin(plugin) {
            // Callback when update finishes
        }
    }

    fun uninstallExtension(pluginId: String) {
        manager.uninstallPlugin(pluginId)
    }

    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val isEmpty: Boolean = true,
        val updates: List<NovelExtension.Installed> = emptyList(),
        val installed: List<NovelExtension.Installed> = emptyList(),
        val available: List<NovelExtension.Available> = emptyList(),
    )
}
