package eu.kanade.tachiyomi.ui.player.cast.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.player.cast.CastState
import eu.kanade.tachiyomi.ui.player.cast.CastUiData
import eu.kanade.tachiyomi.ui.player.cast.components.CastControlButton
import eu.kanade.tachiyomi.ui.player.cast.components.SmallSlider
import eu.kanade.tachiyomi.ui.player.controls.components.CurrentChapter
import eu.kanade.tachiyomi.ui.player.controls.components.VideoTimer
import tachiyomi.presentation.core.components.material.padding

@Composable
fun CastMainControls(
    castState: CastState,
    castUiData: CastUiData,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onSkipIntroClick: () -> Unit,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onBackSeek: () -> Unit,
    onForwardSeek: () -> Unit,
    onClickChapter: () -> Unit,
    onClickDuration: () -> Unit,
    onSeekBarChange: (Float) -> Unit,
    onClickPlaylist: () -> Unit,
    onClickSpeed: () -> Unit,
    onClickSubs: () -> Unit,
    onClickAudio: () -> Unit,
    onClickQuality: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        // Skip intro button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth().padding(end = MaterialTheme.padding.medium).height(42.dp),
        ) {
            castUiData.skipIntroText?.let {
                OutlinedButton(onClick = onSkipIntroClick) {
                    Text(it)
                }
            }
        }

        // Playback buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CastControlButton(
                icon = Icons.Default.SkipPrevious,
                onClick = onPrevious,
                iconSize = 36.dp,
                enabled = hasPrevious,
            )

            CastControlButton(
                icon = Icons.Default.FastRewind,
                onClick = onBackSeek,
                iconSize = 36.dp,
                enabled = true,
            )

            if (castState.buffering || castState.isLoading || castUiData.isLoadingEpisode) {
                Box(modifier = Modifier.padding(MaterialTheme.padding.medium)) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }
            } else {
                CastControlButton(
                    icon = if (castState.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    onClick = onPlayPause,
                    iconSize = 36.dp,
                    enabled = true,
                )
            }

            CastControlButton(
                icon = Icons.Default.FastForward,
                onClick = onForwardSeek,
                iconSize = 36.dp,
                enabled = true,
            )

            CastControlButton(
                icon = Icons.Default.SkipNext,
                onClick = onNext,
                iconSize = 36.dp,
                enabled = hasNext,
            )
        }

        // Seekbar and timers
        val durationFloat = castUiData.duration.toFloat().coerceAtLeast(1f)
        val seekPosition = if (castUiData.isSeeking) {
            castUiData.seekPosition
        } else {
            castState.position.toFloat().coerceIn(0f, durationFloat)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(40.dp),
        ) {
            VideoTimer(
                value = seekPosition,
                isInverted = false,
                onClick = { },
                modifier = Modifier.width(72.dp),
            )

            if (castUiData.showChapterIndicator && castUiData.currentChapter != null) {
                CurrentChapter(
                    chapter = castUiData.currentChapter,
                    onClick = onClickChapter,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            val durationValue = if (castUiData.invertDurationTimer) {
                seekPosition - durationFloat
            } else {
                durationFloat
            }

            VideoTimer(
                value = durationValue,
                isInverted = castUiData.invertDurationTimer,
                onClick = onClickDuration,
                modifier = Modifier.width(72.dp),
            )
        }

        Slider(
            value = seekPosition,
            onValueChange = onSeekBarChange,
            valueRange = 0f..durationFloat,
            modifier = Modifier.fillMaxWidth(),
        )

        // Volume control slider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.padding.medium),
        ) {
            CastControlButton(
                icon = Icons.Default.VolumeDown,
                onClick = { onVolumeChange((volume - 0.1f).coerceIn(0f, 1f)) },
                iconSize = 20.dp,
            )

            SmallSlider(
                value = volume,
                onValueChange = onVolumeChange,
                modifier = Modifier.weight(1f),
            )

            CastControlButton(
                icon = Icons.Default.VolumeUp,
                onClick = { onVolumeChange((volume + 0.1f).coerceIn(0f, 1f)) },
                iconSize = 20.dp,
            )
        }

        // Bottom action sheets buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CastControlButton(
                icon = Icons.Default.VideoLibrary,
                onClick = onClickPlaylist,
                title = "Playlist",
            )
            CastControlButton(
                icon = Icons.Default.Speed,
                onClick = onClickSpeed,
                title = "Speed",
            )
            CastControlButton(
                icon = Icons.Default.Subtitles,
                onClick = onClickSubs,
                title = "Subtitles",
            )
            CastControlButton(
                icon = Icons.Default.Audiotrack,
                onClick = onClickAudio,
                title = "Audio",
            )
            CastControlButton(
                icon = Icons.Default.HighQuality,
                onClick = onClickQuality,
                title = "Quality",
            )
        }
    }
}
