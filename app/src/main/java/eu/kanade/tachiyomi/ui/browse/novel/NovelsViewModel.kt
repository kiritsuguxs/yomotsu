package eu.kanade.tachiyomi.ui.browse.novel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.extension.novel.model.NovelPlugin
import eu.kanade.tachiyomi.util.system.LocaleHelper
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
        if (enabledLanguages.isEmpty()) return false

        val pluginCode = LocaleHelper.mapNovelLangToCode(pluginLang)
        val normalizedRaw = pluginLang.trim().removePrefix("\u200e").removePrefix("\u200f")

        return enabledLanguages.any { enabled ->
            val enabledCode = LocaleHelper.mapNovelLangToCode(enabled)
            enabledCode == pluginCode ||
                (pluginCode.startsWith("pt") && enabledCode.startsWith("pt")) ||
                (pluginCode.startsWith("zh") && enabledCode.startsWith("zh")) ||
                enabled.equals(pluginCode, ignoreCase = true) ||
                enabled.equals(normalizedRaw, ignoreCase = true) ||
                (pluginCode == "all" && (enabled == "all" || enabled.equals("multi", ignoreCase = true)))
        }
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
