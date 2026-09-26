package eu.kanade.tachiyomi.ui.browse.anime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.ExtensionFilterScreen
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen

class AnimeExtensionFilterScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = viewModel<AnimeExtensionFilterViewModel>()
        val state by viewModel.state.collectAsState()

        if (state is AnimeExtensionFilterState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as AnimeExtensionFilterState.Success

        ExtensionFilterContent(
            navigateUp = navigator::pop,
            languages = successState.languages,
            enabledLanguages = successState.enabledLanguages,
            onClickToggle = viewModel::toggle,
        )

        LaunchedEffect(Unit) {
            viewModel.events.collectLatest {
                when (it) {
                    AnimeExtensionFilterEvent.FailedFetchingLanguages -> {
                        context.stringResource(MR.strings.internal_error)
                    }
                }
            }
        }
    }
}
