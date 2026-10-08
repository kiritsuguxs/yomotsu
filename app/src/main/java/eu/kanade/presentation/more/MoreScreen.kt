package eu.kanade.presentation.more

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.more.profile.PresetAvatarDisplay
import eu.kanade.presentation.more.profile.getLocalizedTitleName
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.profile.ProfilePresets
import eu.kanade.tachiyomi.ui.more.DownloadQueueState
import eu.kanade.tachiyomi.ui.more.HunterProfileSummary
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import java.io.File

@Composable
fun MoreScreen(
    profileSummaryProvider: () -> HunterProfileSummary? = { null },
    downloadQueueStateProvider: () -> DownloadQueueState,
    downloadedOnly: Boolean,
    onDownloadedOnlyChange: (Boolean) -> Unit,
    incognitoMode: Boolean,
    onIncognitoModeChange: (Boolean) -> Unit,
    onClickDownloadQueue: () -> Unit,
    animeDownloadQueueStateProvider: () -> DownloadQueueState,
    onClickAnimeDownloadQueue: () -> Unit,
    onClickCategories: () -> Unit,
    onClickStats: () -> Unit,
    onClickDataAndStorage: () -> Unit,
    onClickSettings: () -> Unit,
    onClickAbout: () -> Unit,
) {
    Scaffold { contentPadding ->
        ScrollbarLazyColumn(contentPadding = contentPadding) {
            item {
                LogoHeader(
                    iconPadding = PaddingValues(top = 24.dp, bottom = 12.dp),
                )
            }
            item {
                val profileSummary = profileSummaryProvider()
                if (profileSummary != null) {
                    MiniHunterProfileCard(
                        summary = profileSummary,
                        onClick = onClickStats,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }
            item {
                SwitchPreferenceWidget(
                    title = stringResource(MR.strings.label_downloaded_only),
                    subtitle = stringResource(MR.strings.downloaded_only_summary),
                    icon = Icons.Outlined.CloudOff,
                    checked = downloadedOnly,
                    onCheckedChanged = onDownloadedOnlyChange,
                )
            }
            item {
                SwitchPreferenceWidget(
                    title = stringResource(MR.strings.pref_incognito_mode),
                    subtitle = stringResource(MR.strings.pref_incognito_mode_summary),
                    icon = ImageVector.vectorResource(R.drawable.ic_glasses_24dp),
                    checked = incognitoMode,
                    onCheckedChanged = onIncognitoModeChange,
                )
            }



            item {
                val downloadQueueState = downloadQueueStateProvider()
                TextPreferenceWidget(
                    title = stringResource(MR.strings.label_download_queue),
                    subtitle = when (downloadQueueState) {
                        DownloadQueueState.Stopped -> null
                        is DownloadQueueState.Paused -> {
                            val pending = downloadQueueState.pending
                            if (pending == 0) {
                                stringResource(MR.strings.paused)
                            } else {
                                "${stringResource(MR.strings.paused)} • ${
                                    pluralStringResource(
                                        MR.plurals.download_queue_summary,
                                        count = pending,
                                        pending,
                                    )
                                }"
                            }
                        }
                        is DownloadQueueState.Downloading -> {
                            val pending = downloadQueueState.pending
                            pluralStringResource(MR.plurals.download_queue_summary, count = pending, pending)
                        }
                    },
                    icon = Icons.Outlined.GetApp,
                    onPreferenceClick = onClickDownloadQueue,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(MR.strings.categories),
                    icon = Icons.AutoMirrored.Outlined.Label,
                    onPreferenceClick = onClickCategories,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(MR.strings.label_yomotsu_profile),
                    icon = Icons.Outlined.QueryStats,
                    onPreferenceClick = onClickStats,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(MR.strings.label_data_storage),
                    icon = Icons.Outlined.Storage,
                    onPreferenceClick = onClickDataAndStorage,
                )
            }



            item {
                TextPreferenceWidget(
                    title = stringResource(MR.strings.label_settings),
                    icon = Icons.Outlined.Settings,
                    onPreferenceClick = onClickSettings,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(MR.strings.pref_category_about),
                    icon = Icons.Outlined.Info,
                    onPreferenceClick = onClickAbout,
                )
            }
        }
    }
}

@Composable
private fun MiniHunterProfileCard(
    summary: HunterProfileSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val borderBrush = ProfilePresets.getBorderBrush(summary.avatarBorder, summary.level)
    val borderWidth = if (summary.avatarBorder == "border_none") 0.dp else 2.5.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar com Borda
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(borderWidth, borderBrush, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (summary.avatarType == "custom" && summary.avatarUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(if (summary.avatarUri.startsWith("content://") || summary.avatarUri.startsWith("file://")) Uri.parse(summary.avatarUri) else File(summary.avatarUri))
                            .crossfade(true)
                            .build(),
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val presetAvatar = ProfilePresets.getPresetAvatar(summary.avatarPreset)
                    PresetAvatarDisplay(preset = presetAvatar, modifier = Modifier.fillMaxSize())
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Informações do Caçador & Progresso
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = summary.username,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // Nível Badge
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Nv. ${summary.level}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Título com gradiente
                Text(
                    text = getLocalizedTitleName(summary.title),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = androidx.compose.ui.text.TextStyle(
                        brush = Brush.horizontalGradient(
                            listOf(summary.title.colorStart, summary.title.colorEnd)
                        )
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Barra de XP
                LinearProgressIndicator(
                    progress = { summary.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50)),
                    color = summary.title.colorEnd,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                Icons.AutoMirrored.Outlined.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
