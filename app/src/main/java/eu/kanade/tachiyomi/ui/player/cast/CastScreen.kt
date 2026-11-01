package eu.kanade.tachiyomi.ui.player.cast

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import eu.kanade.tachiyomi.ui.player.cast.controls.CastInfoBox
import eu.kanade.tachiyomi.ui.player.cast.controls.CastMainControls
import eu.kanade.tachiyomi.ui.player.cast.controls.CastTopControls
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding

@Composable
fun CastScreen(
    anime: Anime?,
    episode: Episode?,
    castState: CastState,
    castUiData: CastUiData,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onBack: () -> Unit,
    onStopCasting: () -> Unit,
    onSkipIntroClicked: () -> Unit,
    onClickChapter: () -> Unit,
    onClickDuration: () -> Unit,
    onSeekBarChange: (Float) -> Unit,
    onClickPlaylist: () -> Unit,
    onClickSpeed: () -> Unit,
    onClickSubs: () -> Unit,
    onClickAudio: () -> Unit,
    onClickQuality: () -> Unit,
    onCastManagerEvent: (CastManagerEvent) -> Unit,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    ConstraintLayout(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = MaterialTheme.padding.medium),
    ) {
        val (topControls, infoBox, controls) = createRefs()

        CastTopControls(
            deviceName = castState.deviceName ?: stringResource(AYMR.strings.player_cast_unknown_device),
            onBack = onBack,
            onStopCasting = onStopCasting,
            modifier = Modifier.constrainAs(topControls) {
                start.linkTo(parent.start)
                end.linkTo(parent.end)
                top.linkTo(parent.top)
            },
        )

        CastInfoBox(
            anime = anime,
            episodeTitle = episode?.name,
            episodeNumber = episode?.episode_number?.toDouble(),
            modifier = Modifier.constrainAs(infoBox) {
                if (isLandscape) {
                    start.linkTo(parent.start)
                    top.linkTo(topControls.bottom, MaterialTheme.padding.small)
                    bottom.linkTo(parent.bottom, MaterialTheme.padding.small)
                    width = Dimension.percent(0.4f)
                    height = Dimension.fillToConstraints
                } else {
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    top.linkTo(topControls.bottom, MaterialTheme.padding.medium)
                    bottom.linkTo(controls.top, MaterialTheme.padding.medium)
                    height = Dimension.fillToConstraints
                }
            },
        )

        CastMainControls(
            castState = castState,
            castUiData = castUiData,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            onSkipIntroClick = onSkipIntroClicked,
            volume = castState.volume.toFloat(),
            onVolumeChange = { onCastManagerEvent(CastManagerEvent.VolumeChange(it)) },
            onPlayPause = { onCastManagerEvent(CastManagerEvent.PlayPause) },
            onNext = { onCastManagerEvent(CastManagerEvent.Next(true)) },
            onPrevious = { onCastManagerEvent(CastManagerEvent.Next(false)) },
            onBackSeek = { onCastManagerEvent(CastManagerEvent.DoubleTapSeek(forwards = false)) },
            onForwardSeek = { onCastManagerEvent(CastManagerEvent.DoubleTapSeek(forwards = true)) },
            onClickChapter = onClickChapter,
            onClickDuration = onClickDuration,
            onSeekBarChange = onSeekBarChange,
            onClickPlaylist = onClickPlaylist,
            onClickSpeed = onClickSpeed,
            onClickSubs = onClickSubs,
            onClickAudio = onClickAudio,
            onClickQuality = onClickQuality,
            modifier = Modifier.constrainAs(controls) {
                if (isLandscape) {
                    start.linkTo(infoBox.end)
                    end.linkTo(parent.end)
                    top.linkTo(topControls.bottom, MaterialTheme.padding.small)
                    bottom.linkTo(parent.bottom)
                    width = Dimension.fillToConstraints
                    height = Dimension.fillToConstraints
                } else {
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    bottom.linkTo(parent.bottom, MaterialTheme.padding.medium)
                    width = Dimension.fillToConstraints
                }
            },
        )
    }
}
