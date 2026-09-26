package eu.kanade.tachiyomi.ui.browse.anime

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun ExtensionFilterContent(
    navigateUp: () -> Unit,
    languages: List<String>,
    enabledLanguages: Set<String>,
    onClickToggle: (String) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.label_anime_extensions),
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        if (languages.isEmpty()) {
            EmptyScreen(
                stringRes = MR.strings.empty_screen,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }
        ExtensionFilterList(
            contentPadding = contentPadding,
            languages = languages,
            enabledLanguages = enabledLanguages,
            onClickLang = onClickToggle,
        )
    }
}

@Composable
private fun ExtensionFilterList(
    contentPadding: PaddingValues,
    languages: List<String>,
    enabledLanguages: Set<String>,
    onClickLang: (String) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        contentPadding = contentPadding,
    ) {
        items(languages) { language ->
            SwitchPreferenceWidget(
                modifier = Modifier.animateItem(),
                title = LocaleHelper.getSourceDisplayName(language, context),
                checked = language in enabledLanguages,
                onCheckedChanged = { onClickLang(language) },
            )
        }
    }
}
