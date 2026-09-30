import re
import os

def patch_file(path, replacements):
    with open(path, 'r') as f:
        content = f.read()
    for old, new in replacements:
        content = content.replace(old, new)
    with open(path, 'w') as f:
        f.write(content)

# AnimeExtensionsTab.kt
patch_file('app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/AnimeExtensionsTab.kt', [
    ('package eu.kanade.tachiyomi.ui.browse.extension', 'package eu.kanade.tachiyomi.ui.browse.anime'),
    ('fun extensionsTab(', 'fun animeExtensionsTab('),
    ('extensionsViewModel: ExtensionsViewModel', 'extensionsViewModel: AnimeExtensionsViewModel'),
    ('MR.strings.label_extensions', 'MR.strings.label_animeextensions'),
    ('eu.kanade.tachiyomi.ui.browse.extension.details', 'eu.kanade.tachiyomi.ui.browse.extension.details'),
])

# AnimeExtensionsViewModel.kt
patch_file('app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/AnimeExtensionsViewModel.kt', [
    ('package eu.kanade.tachiyomi.ui.browse.extension', 'package eu.kanade.tachiyomi.ui.browse.anime'),
    ('class ExtensionsViewModel', 'class AnimeExtensionsViewModel'),
    ('private val getExtensions: GetExtensionsByType', 'private val getExtensions: eu.kanade.domain.extension.interactor.GetAnimeExtensionsByType'),
])

# BrowseTab.kt
with open('app/src/main/java/eu/kanade/tachiyomi/ui/browse/BrowseTab.kt', 'r') as f:
    browse_content = f.read()

browse_content = browse_content.replace('import eu.kanade.tachiyomi.ui.browse.extension.extensionsTab', 'import eu.kanade.tachiyomi.ui.browse.extension.extensionsTab\nimport eu.kanade.tachiyomi.ui.browse.anime.AnimeExtensionsViewModel\nimport eu.kanade.tachiyomi.ui.browse.anime.animeExtensionsTab')
browse_content = browse_content.replace('val extensionsViewModel = viewModel<ExtensionsViewModel>()', 'val extensionsViewModel = viewModel<ExtensionsViewModel>()\n        val animeExtensionsViewModel = viewModel<AnimeExtensionsViewModel>()\n        val animeExtensionsState by animeExtensionsViewModel.state.collectAsState()')
browse_content = browse_content.replace('val tabs = listOf(', 'val tabs = listOf(\n            animeExtensionsTab(animeExtensionsViewModel),')
browse_content = browse_content.replace('1 -> extensionsState.searchQuery', '1 -> animeExtensionsState.searchQuery\n                2 -> extensionsState.searchQuery')
browse_content = browse_content.replace('1 -> extensionsViewModel.search(query)', '1 -> animeExtensionsViewModel.search(query)\n                    2 -> extensionsViewModel.search(query)')

with open('app/src/main/java/eu/kanade/tachiyomi/ui/browse/BrowseTab.kt', 'w') as f:
    f.write(browse_content)
