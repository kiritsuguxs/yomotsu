package eu.kanade.tachiyomi.ui.browse.anime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.ExtensionScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.animeExtensionsTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val viewModel = viewModel<AnimeExtensionsViewModel>()
    val state by viewModel.state.collectAsState()

    return TabContent(
        titleRes = MR.strings.label_anime_extensions,
        searchEnabled = true,
        actions = listOf(
            AppBar.OverflowAction(
                title = stringResource(MR.strings.action_filter),
                onClick = { navigator.push(AnimeExtensionFilterScreen()) },
            ),
        ),
        content = { contentPadding, _ ->
            ExtensionScreen(
                state = state,
                contentPadding = contentPadding,
                searchQuery = state.searchQuery,
                onLongClickItem = { },
                onClickItemCancel = viewModel::cancelInstallUpdateExtension,
                onOpenWebView = { extension ->
                    extension.sources.getOrNull(0)?.let {
                        navigator.push(
                            WebViewScreen(
                                url = it.baseUrl,
                                initialTitle = it.name,
                                sourceId = it.id,
                            ),
                        )
                    }
                },
                onInstallExtension = viewModel::installExtension,
                onUninstallExtension = viewModel::uninstallExtension,
                onUpdateExtension = viewModel::updateExtension,
                onOpenExtension = { },
                onClickUpdateAll = viewModel::updateAllExtensions,
                onTrustExtension = viewModel::trustExtension,
                onRefresh = viewModel::findAvailableExtensions,
            )
        },
    )
}
