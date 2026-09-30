import re

with open('app/src/main/java/eu/kanade/presentation/more/MoreScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('onClickDownloadQueue: () -> Unit,', '''onClickDownloadQueue: () -> Unit,
    animeDownloadQueueStateProvider: () -> DownloadQueueState,
    onClickAnimeDownloadQueue: () -> Unit,''')

# Add the UI item for Anime Downloads right after the regular downloads
ui_replacement = '''
            TextPreferenceWidget(
                title = stringResource(MR.strings.label_download_queue),
                subtitle = getDownloadQueueSubtitle(downloadQueueStateProvider()),
                icon = Icons.Outlined.GetApp,
                onPreferenceClick = onClickDownloadQueue,
            )
            TextPreferenceWidget(
                title = stringResource(tachiyomi.i18n.aniyomi.AYMR.strings.label_anime_download_queue),
                subtitle = getDownloadQueueSubtitle(animeDownloadQueueStateProvider()),
                icon = Icons.Outlined.GetApp,
                onPreferenceClick = onClickAnimeDownloadQueue,
            )
'''
content = re.sub(r'            TextPreferenceWidget\(\s*title = stringResource\(MR.strings.label_download_queue\),\s*subtitle = getDownloadQueueSubtitle\(downloadQueueStateProvider\(\)\),\s*icon = Icons.Outlined.GetApp,\s*onPreferenceClick = onClickDownloadQueue,\s*\)', ui_replacement, content)

with open('app/src/main/java/eu/kanade/presentation/more/MoreScreen.kt', 'w') as f:
    f.write(content)
