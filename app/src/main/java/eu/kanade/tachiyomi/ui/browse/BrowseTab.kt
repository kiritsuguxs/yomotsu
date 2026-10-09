package eu.kanade.tachiyomi.ui.browse

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.util.collectAsState

import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.ListItem
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Category
import eu.kanade.presentation.components.AppBar
import androidx.compose.runtime.mutableStateOf

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.anime.AnimeExtensionsViewModel
import eu.kanade.tachiyomi.ui.browse.anime.animeExtensionsTab
import eu.kanade.tachiyomi.ui.browse.anime.animeSourcesTab
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsViewModel
import eu.kanade.tachiyomi.ui.browse.extension.extensionsTab
import eu.kanade.tachiyomi.ui.browse.migration.sources.migrateSourceTab
import eu.kanade.tachiyomi.ui.browse.novel.novelSourcesTab
import eu.kanade.tachiyomi.ui.browse.novel.novelsTab
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.browse.source.sourcesTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

data object BrowseTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_browse_enter)
            return TabOptions(
                index = 3u,
                title = stringResource(MR.strings.browse),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(GlobalSearchScreen())
    }

    private val switchToExtensionTabChannel = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    fun showExtension() {
        switchToExtensionTabChannel.trySend(Unit)
    }

    @Composable
    override fun Content() {
        val uiPreferences = androidx.compose.runtime.remember { Injekt.get<eu.kanade.domain.ui.UiPreferences>() }
        val selectedMedia by uiPreferences.lastUsedBrowseMedia.collectAsState()
        var showMediaSheet by androidx.compose.runtime.remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState()

        val context = LocalContext.current

        val extensionsViewModel = viewModel<ExtensionsViewModel>()
        val animeExtensionsViewModel = viewModel<AnimeExtensionsViewModel>()
        val animeExtensionsState by animeExtensionsViewModel.state.collectAsState()
        val extensionsState by extensionsViewModel.state.collectAsState()

        val novelsViewModel = viewModel<eu.kanade.tachiyomi.ui.browse.novel.NovelsViewModel>()

        val activeSourcesTab = when (selectedMedia) {
            "Anime" -> animeSourcesTab()
            "Novel" -> novelSourcesTab()
            else -> sourcesTab()
        }
        val activeExtensionsTab = when (selectedMedia) {
            "Anime" -> animeExtensionsTab(animeExtensionsViewModel)
            "Novel" -> novelsTab(novelsViewModel)
            else -> extensionsTab(extensionsViewModel)
        }

        val tabs = listOf(
            activeSourcesTab,
            activeExtensionsTab,
            migrateSourceTab(selectedMedia),
        )

        val state = rememberPagerState { tabs.size }

        val novelSearchQuery by novelsViewModel.searchQuery.collectAsState()

        TabbedScreen(
            title = when (selectedMedia) {
                "Anime" -> stringResource(MR.strings.browse_media_anime)
                "Novel" -> stringResource(MR.strings.browse_media_novel)
                else -> stringResource(MR.strings.browse_media_manga)
            },
            tabs = tabs,
            state = state,
            searchQuery = when (state.currentPage) {
                1 -> when (selectedMedia) {
                    "Anime" -> animeExtensionsState.searchQuery
                    "Novel" -> novelSearchQuery
                    else -> extensionsState.searchQuery
                }
                else -> null
            },
            onChangeSearchQuery = { query ->
                when (state.currentPage) {
                    1 -> when (selectedMedia) {
                        "Anime" -> animeExtensionsViewModel.search(query)
                        "Novel" -> novelsViewModel.search(query)
                        else -> extensionsViewModel.search(query)
                    }
                }
            },
            navigationIcon = {
                androidx.compose.material3.IconButton(onClick = { showMediaSheet = true }) {
                    androidx.compose.material3.Icon(
                        imageVector = when (selectedMedia) {
                            "Anime" -> Icons.Outlined.Tv
                            "Novel" -> Icons.Outlined.LibraryBooks
                            else -> Icons.Outlined.MenuBook
                        },
                        contentDescription = "Selecionar Mídia"
                    )
                }
            }
        )

        LaunchedEffect(Unit) {
            switchToExtensionTabChannel.receiveAsFlow()
                .collectLatest { state.scrollToPage(1) }
        }

        if (showMediaSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMediaSheet = false },
                sheetState = sheetState
            ) {
                androidx.compose.foundation.layout.Column(Modifier.padding(bottom = 32.dp)) {
                    ListItem(
                        headlineContent = { Text("📖 ${stringResource(MR.strings.browse_media_manga)}") },
                        modifier = Modifier.clickable { uiPreferences.lastUsedBrowseMedia.set("Manga"); showMediaSheet = false }
                    )
                    ListItem(
                        headlineContent = { Text("🎬 ${stringResource(MR.strings.browse_media_anime)}") },
                        modifier = Modifier.clickable { uiPreferences.lastUsedBrowseMedia.set("Anime"); showMediaSheet = false }
                    )
                    ListItem(
                        headlineContent = { Text("📚 ${stringResource(MR.strings.browse_media_novel)}") },
                        modifier = Modifier.clickable { uiPreferences.lastUsedBrowseMedia.set("Novel"); showMediaSheet = false }
                    )
                }
            }
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}
