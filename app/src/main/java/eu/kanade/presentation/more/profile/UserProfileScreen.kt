package eu.kanade.presentation.more.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.util.toDurationString
import eu.kanade.tachiyomi.data.profile.AchievementCategory
import eu.kanade.tachiyomi.data.profile.ProfilePresetAvatar
import eu.kanade.tachiyomi.data.profile.ProfilePresetBanner
import eu.kanade.tachiyomi.data.profile.ProfilePresets
import eu.kanade.tachiyomi.data.profile.YomotsuAchievement
import eu.kanade.tachiyomi.data.profile.YomotsuLevelManager
import eu.kanade.tachiyomi.data.profile.YomotsuTitle
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    navigateUp: () -> Unit,
    username: String,
    totalXp: Long,
    totalChaptersRead: Int,
    totalChapters: Int = 0,
    totalMangas: Int,
    totalEpisodesWatched: Int = 0,
    totalEpisodes: Int = 0,
    totalAnimes: Int = 0,
    totalDownloads: Int = 0,
    mangaDownloads: Int = 0,
    animeDownloads: Int = 0,
    completedMangas: Int = 0,
    completedAnimes: Int = 0,
    totalReadDurationMs: Long = 0L,
    totalWatchDurationMs: Long = 0L,
    unlockedAchievements: List<YomotsuAchievement>,
    lockedAchievements: List<YomotsuAchievement>,
    equippedTitle: YomotsuTitle,
    unlockedTitles: List<YomotsuTitle>,
    avatarUri: String?,
    avatarPreset: String = "preset_yomotsu",
    avatarType: String = "preset",
    avatarBorder: String = "border_auto",
    bannerUri: String?,
    bannerPreset: String = "banner_abyss",
    bannerType: String = "preset",
    onUsernameChanged: (String) -> Unit,
    onTitleSelected: (YomotsuTitle) -> Unit,
    onAvatarSelected: (String?) -> Unit,
    onAvatarPresetSelected: (String) -> Unit = {},
    onAvatarBorderSelected: (String) -> Unit = {},
    onBannerSelected: (String?) -> Unit,
    onBannerPresetSelected: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var showTitleDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showAvatarDialog by remember { mutableStateOf(false) }
    var showBorderDialog by remember { mutableStateOf(false) }
    var showBannerDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(username) }
    var selectedCategory by remember { mutableStateOf<AchievementCategory?>(null) }

    val currentLevel = YomotsuLevelManager.calculateLevelFromXp(totalXp)
    val currentLevelXp = YomotsuLevelManager.getXpRequiredForLevel(currentLevel)
    val nextLevelXp = YomotsuLevelManager.getXpRequiredForLevel(currentLevel + 1)

    val progress = if (nextLevelXp > currentLevelXp) {
        (totalXp - currentLevelXp).toFloat() / (nextLevelXp - currentLevelXp).toFloat()
    } else {
        1f
    }

    val bannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { onBannerSelected(it.toString()) }
    }
    val avatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { onAvatarSelected(it.toString()) }
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(stringResource(MR.strings.profile_change_hunter_name)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text(stringResource(MR.strings.profile_name_label)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) onUsernameChanged(newName.trim())
                    showNameDialog = false
                }) { Text(stringResource(MR.strings.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text(stringResource(MR.strings.action_cancel)) }
            }
        )
    }

    if (showTitleDialog) {
        AlertDialog(
            onDismissRequest = { showTitleDialog = false },
            title = { Text(stringResource(MR.strings.profile_choose_title)) },
            text = {
                LazyColumn {
                    items(unlockedTitles) { title ->
                        Text(
                            text = getLocalizedTitleName(title),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onTitleSelected(title)
                                    showTitleDialog = false
                                }
                                .padding(16.dp),
                            style = androidx.compose.ui.text.TextStyle(brush = Brush.horizontalGradient(colors = listOf(title.colorStart, title.colorEnd))),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTitleDialog = false }) { Text(stringResource(MR.strings.action_close)) }
            }
        )
    }

    // DIÁLOGO SELETOR DE AVATAR ESTILO CRUNCHYROLL
    if (showAvatarDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarDialog = false },
            title = { Text(stringResource(MR.strings.profile_dialog_choose_avatar)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            avatarLauncher.launch("image/*")
                            showAvatarDialog = false
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(MR.strings.profile_action_pick_from_gallery))
                    }

                    Button(
                        onClick = {
                            showAvatarDialog = false
                            showBorderDialog = true
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(MR.strings.profile_action_change_border))
                    }

                    Text(
                        text = stringResource(MR.strings.profile_preset_avatars),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                        contentPadding = PaddingValues(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ProfilePresets.PRESET_AVATARS) { preset ->
                            val isSelected = avatarType == "preset" && avatarPreset == preset.id
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onAvatarPresetSelected(preset.id)
                                        showAvatarDialog = false
                                    }
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(6.dp)
                            ) {
                                PresetAvatarDisplay(preset = preset, modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = preset.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = preset.group,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAvatarDialog = false }) {
                    Text(stringResource(MR.strings.action_close))
                }
            }
        )
    }

    // DIÁLOGO SELETOR DE BORDA / MOLDURA
    if (showBorderDialog) {
        AlertDialog(
            onDismissRequest = { showBorderDialog = false },
            title = { Text(stringResource(MR.strings.profile_dialog_choose_border)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(MR.strings.profile_dialog_choose_border_desc),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ProfilePresets.PRESET_BORDERS) { border ->
                            val isSelected = avatarBorder == border.id
                            val brush = if (border.id == "border_none") {
                                Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                            } else if (border.isDynamicRank) {
                                Brush.sweepGradient(ProfilePresets.getRankBorderColors(currentLevel))
                            } else {
                                Brush.sweepGradient(border.colors)
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onAvatarBorderSelected(border.id)
                                        showBorderDialog = false
                                    }
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1E1E1E))
                                            .border(
                                                width = if (border.id == "border_none") 1.dp else 3.5.dp,
                                                brush = if (border.id == "border_none") Brush.linearGradient(listOf(Color.Gray.copy(alpha = 0.4f), Color.Gray.copy(alpha = 0.4f))) else brush,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (border.id == "border_none") "—" else "★",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = border.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = border.description,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBorderDialog = false }) {
                    Text(stringResource(MR.strings.action_close))
                }
            }
        )
    }

    // DIÁLOGO SELETOR DE BANNER
    if (showBannerDialog) {
        AlertDialog(
            onDismissRequest = { showBannerDialog = false },
            title = { Text(stringResource(MR.strings.profile_dialog_choose_banner)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            bannerLauncher.launch("image/*")
                            showBannerDialog = false
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(MR.strings.profile_action_pick_from_gallery))
                    }

                    Text(
                        text = stringResource(MR.strings.profile_preset_banners),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                        contentPadding = PaddingValues(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ProfilePresets.PRESET_BANNERS) { preset ->
                            val isSelected = bannerType == "preset" && bannerPreset == preset.id
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(55.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onBannerPresetSelected(preset.id)
                                        showBannerDialog = false
                                    }
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Brush.horizontalGradient(preset.gradient)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = preset.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBannerDialog = false }) {
                    Text(stringResource(MR.strings.action_close))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(MR.strings.label_yomotsu_profile)) },
                navigationIcon = {
                    IconButton(onClick = navigateUp) { Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(MR.strings.action_bar_up_description)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // BANNER E AVATAR
            item {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    // BANNER
                    Box(modifier = Modifier.fillMaxWidth().height(220.dp).clickable { showBannerDialog = true }) {
                        if (bannerType == "custom" && bannerUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(if (bannerUri.startsWith("content://") || bannerUri.startsWith("file://")) Uri.parse(bannerUri) else File(bannerUri))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Banner",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val presetBanner = ProfilePresets.getPresetBanner(bannerPreset)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.horizontalGradient(presetBanner.gradient))
                            )
                        }
                        IconButton(
                            onClick = { showBannerDialog = true },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(MR.strings.profile_edit_banner), tint = Color.White.copy(alpha = 0.7f))
                        }
                    }

                    // AVATAR COM BORDA PERSONALIZADA
                    val borderBrush = ProfilePresets.getBorderBrush(avatarBorder, currentLevel)
                    val borderWidth = if (avatarBorder == "border_none") 0.dp else 4.dp
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .align(Alignment.BottomCenter)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(borderWidth, borderBrush, CircleShape)
                                .clickable { showAvatarDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarType == "custom" && avatarUri != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(if (avatarUri.startsWith("content://") || avatarUri.startsWith("file://")) Uri.parse(avatarUri) else File(avatarUri))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                val presetAvatar = ProfilePresets.getPresetAvatar(avatarPreset)
                                PresetAvatarDisplay(preset = presetAvatar, modifier = Modifier.fillMaxSize())
                            }
                        }

                        // Botão de atalho para editar a borda / moldura
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                .clickable { showBorderDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Shield,
                                contentDescription = stringResource(MR.strings.profile_action_change_border),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // NOME E TÍTULO
            item {
                Row(modifier = Modifier.fillMaxWidth().clickable { showNameDialog = true }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(username, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(MR.strings.profile_edit_name), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = getLocalizedTitleName(equippedTitle),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.fillMaxWidth().clickable { showTitleDialog = true }.padding(vertical = 4.dp),
                    textAlign = TextAlign.Center,
                    style = androidx.compose.ui.text.TextStyle(brush = Brush.horizontalGradient(colors = listOf(equippedTitle.colorStart, equippedTitle.colorEnd)))
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // BARRA DE XP
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(MR.strings.profile_level, currentLevel), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(MR.strings.profile_level, currentLevel + 1), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)),
                        color = equippedTitle.colorEnd,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("$totalXp / $nextLevelXp XP", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ESTATÍSTICAS COMPLETAS (MANGÁ, ANIME, OFFLINE E TEMPO)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Linha 1: Mangá vs Anime (Progresso de Leitura e Reprodução)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val mangaValue = if (totalChapters > 0) "$totalChaptersRead / $totalChapters" else totalChaptersRead.toString()
                        StatBox(
                            icon = Icons.Outlined.MenuBook,
                            value = mangaValue,
                            label = stringResource(MR.strings.profile_stat_read),
                            subtitle = "$totalMangas " + stringResource(MR.strings.profile_stat_mangas_in_library),
                            modifier = Modifier.weight(1f)
                        )

                        val animeValue = if (totalEpisodes > 0) "$totalEpisodesWatched / $totalEpisodes" else totalEpisodesWatched.toString()
                        StatBox(
                            icon = Icons.Outlined.PlayCircleOutline,
                            value = animeValue,
                            label = stringResource(MR.strings.profile_stat_episodes_watched),
                            subtitle = "$totalAnimes " + stringResource(MR.strings.profile_stat_animes_in_library),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Linha 2: Tempo Lido vs Tempo Assistido (Dias e Horas do Mihon)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatBox(
                            icon = Icons.Outlined.Schedule,
                            value = totalReadDurationMs.milliseconds.toDurationString(context, fallback = "0m"),
                            label = stringResource(MR.strings.profile_stat_time_read),
                            subtitle = stringResource(MR.strings.label_read_duration),
                            modifier = Modifier.weight(1f)
                        )
                        StatBox(
                            icon = Icons.Outlined.Timer,
                            value = totalWatchDurationMs.milliseconds.toDurationString(context, fallback = "0m"),
                            label = stringResource(MR.strings.profile_stat_time_watched),
                            subtitle = stringResource(MR.strings.profile_stat_time_watched_sub),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Linha 3: Armazenamento Offline vs Obras Concluídas
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatBox(
                            icon = Icons.Outlined.CloudDownload,
                            value = totalDownloads.toString(),
                            label = stringResource(MR.strings.label_downloaded),
                            subtitle = stringResource(MR.strings.profile_stat_offline_sub, mangaDownloads, animeDownloads),
                            modifier = Modifier.weight(1f)
                        )
                        val totalCompleted = completedMangas + completedAnimes
                        StatBox(
                            icon = Icons.Outlined.DoneAll,
                            value = totalCompleted.toString(),
                            label = stringResource(MR.strings.label_completed_titles),
                            subtitle = stringResource(MR.strings.profile_stat_completed_sub, completedMangas, completedAnimes),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // TÍTULO CONQUISTAS E FILTROS DE CATEGORIA
            item {
                val totalAchievements = unlockedAchievements.size + lockedAchievements.size
                Text(
                    text = stringResource(MR.strings.profile_trophy_room, unlockedAchievements.size, totalAchievements),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text(stringResource(MR.strings.profile_category_all)) },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategory == AchievementCategory.ANIME,
                            onClick = { selectedCategory = AchievementCategory.ANIME },
                            label = { Text("🍿 " + stringResource(MR.strings.profile_category_anime)) },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategory == AchievementCategory.READING,
                            onClick = { selectedCategory = AchievementCategory.READING },
                            label = { Text("📖 " + stringResource(MR.strings.profile_category_manga)) },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategory == AchievementCategory.COLLECTION,
                            onClick = { selectedCategory = AchievementCategory.COLLECTION },
                            label = { Text("📚 " + stringResource(MR.strings.profile_category_collection)) },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategory == AchievementCategory.DOWNLOADS,
                            onClick = { selectedCategory = AchievementCategory.DOWNLOADS },
                            label = { Text("📥 " + stringResource(MR.strings.profile_category_downloads)) },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // LISTA FILTRADA DE CONQUISTAS
            val displayUnlocked = if (selectedCategory == null) {
                unlockedAchievements
            } else {
                unlockedAchievements.filter { it.category == selectedCategory }
            }
            val displayLocked = if (selectedCategory == null) {
                lockedAchievements
            } else {
                lockedAchievements.filter { it.category == selectedCategory }
            }

            // CONQUISTAS DESBLOQUEADAS
            items(displayUnlocked) { achievement ->
                Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                    AchievementItem(achievement, isUnlocked = true)
                }
            }

            // CONQUISTAS BLOQUEADAS
            items(displayLocked) { achievement ->
                Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                    AchievementItem(achievement, isUnlocked = false)
                }
            }
        }
    }
}

@Composable
fun PresetAvatarDisplay(
    preset: ProfilePresetAvatar,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Brush.linearGradient(preset.backgroundGradient)),
        contentAlignment = Alignment.Center
    ) {
        if (preset.drawableRes != null) {
            Image(
                painter = painterResource(preset.drawableRes),
                contentDescription = preset.name,
                modifier = if (preset.isCharacter) Modifier.fillMaxSize() else Modifier.fillMaxSize().padding(14.dp),
                contentScale = if (preset.isCharacter) ContentScale.Crop else ContentScale.Fit
            )
        } else if (preset.icon != null) {
            Icon(
                imageVector = preset.icon,
                contentDescription = preset.name,
                tint = preset.iconTint,
                modifier = Modifier.fillMaxSize().padding(16.dp)
            )
        }
    }
}

@Composable
fun StatBox(
    icon: ImageVector,
    value: String,
    label: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(102.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun AchievementItem(achievement: YomotsuAchievement, isUnlocked: Boolean) {
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isUnlocked) 0.1f else 0.05f)
    val contentColor = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val icon = if (isUnlocked) achievement.category.icon else Icons.Outlined.Lock

    val localizedName = getLocalizedAchievementName(achievement)
    val localizedDesc = getLocalizedAchievementDesc(achievement)

    val modifier = Modifier.fillMaxWidth()

    if (isUnlocked && achievement.tier.isGradient) {
        // Conquistas Hardcore (Tier Rubi) com borda gradiente neon!
        val gradient = Brush.horizontalGradient(listOf(achievement.tier.color, achievement.tier.colorEnd))
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = androidx.compose.foundation.BorderStroke(2.dp, gradient)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = achievement.tier.color, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(localizedName, fontWeight = FontWeight.Bold, color = contentColor, fontSize = 16.sp)
                    Text(localizedDesc, fontSize = 12.sp, color = contentColor)
                }
            }
        }
    } else {
        // Conquistas normais
        val iconTint = if (isUnlocked) achievement.tier.color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = if (isUnlocked) androidx.compose.foundation.BorderStroke(1.dp, achievement.tier.color.copy(alpha = 0.5f)) else null
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(localizedName, fontWeight = FontWeight.Bold, color = contentColor, fontSize = 16.sp)
                    Text(localizedDesc, fontSize = 12.sp, color = contentColor)
                }
            }
        }
    }
}

@Composable
fun getLocalizedTitleName(title: YomotsuTitle): String {
    return when (title.unlockLevel) {
        1 -> stringResource(MR.strings.profile_title_1)
        10 -> stringResource(MR.strings.profile_title_10)
        20 -> stringResource(MR.strings.profile_title_20)
        30 -> stringResource(MR.strings.profile_title_30)
        40 -> stringResource(MR.strings.profile_title_40)
        50 -> stringResource(MR.strings.profile_title_50)
        60 -> stringResource(MR.strings.profile_title_60)
        70 -> stringResource(MR.strings.profile_title_70)
        80 -> stringResource(MR.strings.profile_title_80)
        90 -> stringResource(MR.strings.profile_title_90)
        100 -> stringResource(MR.strings.profile_title_100)
        110 -> stringResource(MR.strings.profile_title_110)
        120 -> stringResource(MR.strings.profile_title_120)
        130 -> stringResource(MR.strings.profile_title_130)
        140 -> stringResource(MR.strings.profile_title_140)
        150 -> stringResource(MR.strings.profile_title_150)
        160 -> stringResource(MR.strings.profile_title_160)
        170 -> stringResource(MR.strings.profile_title_170)
        180 -> stringResource(MR.strings.profile_title_180)
        190 -> stringResource(MR.strings.profile_title_190)
        200 -> stringResource(MR.strings.profile_title_200)
        else -> title.name
    }
}

@Composable
private fun getLocalizedAchievementName(achievement: YomotsuAchievement): String {
    val level = Regex("""\d+""").find(achievement.name)?.value?.toIntOrNull()
    return if (level != null) {
        when (achievement.category) {
            AchievementCategory.READING -> stringResource(MR.strings.achievement_reader_name, level)
            AchievementCategory.COLLECTION -> stringResource(MR.strings.achievement_collector_name, level)
            AchievementCategory.DOWNLOADS -> stringResource(MR.strings.achievement_archivist_name, level)
            AchievementCategory.ANIME -> {
                when {
                    achievement.id.startsWith("anime_watch") -> stringResource(MR.strings.achievement_anime_watcher_name, level)
                    achievement.id.startsWith("anime_lib") -> stringResource(MR.strings.achievement_anime_collector_name, level)
                    achievement.id.startsWith("anime_dl") -> stringResource(MR.strings.achievement_anime_archivist_name, level)
                    else -> achievement.name
                }
            }
            else -> achievement.name
        }
    } else {
        achievement.name
    }
}

@Composable
private fun getLocalizedAchievementDesc(achievement: YomotsuAchievement): String {
    val target = Regex("""\d+""").find(achievement.description)?.value?.toIntOrNull()
    return if (target != null) {
        when (achievement.category) {
            AchievementCategory.READING -> stringResource(MR.strings.achievement_reader_desc, target)
            AchievementCategory.COLLECTION -> stringResource(MR.strings.achievement_collector_desc, target)
            AchievementCategory.DOWNLOADS -> stringResource(MR.strings.achievement_archivist_desc, target)
            AchievementCategory.ANIME -> {
                when {
                    achievement.id.startsWith("anime_watch") -> stringResource(MR.strings.achievement_anime_watcher_desc, target)
                    achievement.id.startsWith("anime_lib") -> stringResource(MR.strings.achievement_anime_collector_desc, target)
                    achievement.id.startsWith("anime_dl") -> stringResource(MR.strings.achievement_anime_archivist_desc, target)
                    else -> achievement.description
                }
            }
            else -> achievement.description
        }
    } else {
        achievement.description
    }
}
