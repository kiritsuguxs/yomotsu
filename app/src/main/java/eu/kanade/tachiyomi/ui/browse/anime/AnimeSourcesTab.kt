package eu.kanade.tachiyomi.ui.browse.anime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.browse.SourceOptionsDialog
import eu.kanade.presentation.browse.SourcesScreen
import eu.kanade.presentation.components.TabContent
import tachiyomi.i18n.MR

@Composable
fun Screen.animeSourcesTab(): TabContent {
    val viewModel = viewModel<AnimeSourcesViewModel>()
    val state by viewModel.state.collectAsState()

    return TabContent(
        titleRes = MR.strings.label_anime_sources,
        content = { contentPadding, _ ->
            SourcesScreen(
                state = state,
                contentPadding = contentPadding,
                onClickItem = { _, _ -> },
                onClickPin = viewModel::togglePin,
                onLongClickItem = viewModel::showSourceDialog,
            )

            state.dialog?.let { dialog ->
                val source = dialog.source
                SourceOptionsDialog(
                    source = source,
                    onClickPin = {
                        viewModel.togglePin(source)
                        viewModel.closeDialog()
                    },
                    onClickDisable = {
                        viewModel.toggleSource(source)
                        viewModel.closeDialog()
                    },
                    onDismiss = viewModel::closeDialog,
                )
            }
        },
    )
}
