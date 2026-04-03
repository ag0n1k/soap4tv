package com.soap4tv.app.ui.screen.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.soap4tv.app.ui.theme.*

@Composable
fun PlayerOverlay(
    isVisible: Boolean,
    title: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    hasSubtitles: Boolean,
    audioTracks: List<String>,
    selectedAudioTrack: Int,
    selectedSubtitleTrack: Int,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
        ) {
            // Back button top-left
            var backFocused by remember { mutableStateOf(false) }
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .onFocusChanged { backFocused = it.isFocused }
                    .background(
                        if (backFocused) SurfaceVariant else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = if (backFocused) Accent else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Title top-center
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp, start = 80.dp, end = 80.dp)
            )

            // Center controls
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OverlayButton(
                    icon = Icons.Default.SkipPrevious,
                    description = "Rewind 10s",
                    onClick = onSeekBack
                )
                OverlayButton(
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    description = if (isPlaying) "Pause" else "Play",
                    onClick = onPlayPause,
                    size = 64.dp
                )
                OverlayButton(
                    icon = Icons.Default.SkipNext,
                    description = "Forward 10s",
                    onClick = onSeekForward
                )
            }

            // Bottom controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 24.dp)
            ) {
                // Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(positionMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                    Text(
                        text = formatTime(durationMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Progress bar
                if (durationMs > 0) {
                    LinearProgressIndicator(
                        progress = { (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = Accent,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Audio + subtitle track selectors
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (audioTracks.isNotEmpty()) {
                        audioTracks.forEachIndexed { index, track ->
                            TrackChip(
                                text = track,
                                isSelected = index == selectedAudioTrack,
                                onClick = { onSelectAudio(index) }
                            )
                        }
                    }

                    if (hasSubtitles) {
                        Spacer(Modifier.width(16.dp))
                        TrackChip(
                            text = "Без субтитров",
                            isSelected = selectedSubtitleTrack < 0,
                            onClick = { onSelectSubtitle(-1) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 48.dp
) {
    var isFocused by remember { mutableStateOf(false) }
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .onFocusChanged { isFocused = it.isFocused }
            .background(
                if (isFocused) Accent.copy(alpha = 0.3f) else Color.Transparent,
                RoundedCornerShape(size / 2)
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (isFocused) Accent else Color.White,
            modifier = Modifier.size(size * 0.6f)
        )
    }
}

@Composable
private fun TrackChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    isSelected -> Accent
                    isFocused -> SurfaceVariant
                    else -> Color.White.copy(alpha = 0.15f)
                }
            )
            .border(
                width = if (isFocused && !isSelected) 1.dp else 0.dp,
                color = Accent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f)
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
