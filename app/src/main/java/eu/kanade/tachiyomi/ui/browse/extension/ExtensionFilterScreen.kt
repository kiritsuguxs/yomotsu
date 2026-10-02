package eu.kanade.tachiyomi.ui.browse.extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.CreationExtras
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.ExtensionFilterScreen
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen

data class ExtensionFilterScreen(
    val type: ExtensionFilterType = ExtensionFilterType.MANGA,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = viewModel<ExtensionFilterViewModel>(
            factory = ExtensionFilterViewModel.Factory,
            extras = CreationExtras {
                set(ExtensionFilterViewModel.TYPE_KEY, type)
            },
        )
        val state by viewModel.state.collectAsState()

        if (state is ExtensionFilterState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as ExtensionFilterState.Success

        val titleRes = when (type) {
            ExtensionFilterType.MANGA -> MR.strings.label_extensions
            ExtensionFilterType.ANIME -> MR.strings.label_animeextensions
            ExtensionFilterType.NOVEL -> MR.strings.label_novels
        }

        ExtensionFilterScreen(
            navigateUp = navigator::pop,
            state = successState,
            onClickToggle = viewModel::toggle,
            titleRes = titleRes,
        )

        LaunchedEffect(Unit) {
            viewModel.events.collectLatest {
                when (it) {
                    ExtensionFilterEvent.FailedFetchingLanguages -> {
                        context.stringResource(MR.strings.internal_error)
                    }
                }
            }
        }
    }
}
