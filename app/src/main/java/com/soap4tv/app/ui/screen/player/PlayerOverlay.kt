package com.soap4tv.app.ui.screen.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soap4tv.app.ui.theme.*

@Composable
fun PlayerOverlay(
    isVisible: Boolean,
    playPauseFocusRequester: FocusRequester,
    title: String,
    isPlaying: Boolean,
    pendingSeekMs: Long,
    durationMs: Long,
    subtitleTracks: List<String>,
    audioTracks: List<String>,
    selectedAudioTrack: Int,
    selectedSubtitleTrack: Int,
    onPlayPause: () -> Unit,
    onRestart: () -> Unit,
    onSkipToEnd: () -> Unit,
    onAdjustMinute: (Int) -> Unit,
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
                .background(Color.Black.copy(alpha = 0.75f))
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
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                    icon = Icons.Default.Replay,
                    description = "Restart",
                    onClick = onRestart
                )
                OverlayButton(
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    description = if (isPlaying) "Pause" else "Play",
                    onClick = onPlayPause,
                    size = 64.dp,
                    focusRequester = playPauseFocusRequester
                )
                OverlayButton(
                    icon = Icons.Default.SkipNext,
                    description = "Next",
                    onClick = onSkipToEnd
                )
            }

            // Bottom controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 24.dp)
            ) {
                // Seekable time + duration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SeekableTime(
                        positionMs = pendingSeekMs,
                        onAdjustMinute = onAdjustMinute
                    )
                    Text(
                        text = formatTime(durationMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Progress bar
                if (durationMs > 0) {
                    LinearProgressIndicator(
                        progress = { (pendingSeekMs.toFloat() / durationMs).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = Accent,
                        trackColor = Color.White.copy(alpha = 0.5f)
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

                    if (subtitleTracks.isNotEmpty()) {
                        Spacer(Modifier.width(16.dp))
                        subtitleTracks.forEachIndexed { index, track ->
                            TrackChip(
                                text = track,
                                isSelected = index == selectedSubtitleTrack,
                                onClick = { onSelectSubtitle(index) }
                            )
                        }
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
    size: Dp = 48.dp,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    var modifier = Modifier
        .size(size)
        .onFocusChanged { isFocused = it.isFocused }
        .background(
            if (isFocused) Accent.copy(alpha = 0.3f) else Color.Transparent,
            RoundedCornerShape(size / 2)
        )
    if (focusRequester != null) {
        modifier = modifier.focusRequester(focusRequester)
    }
    IconButton(
        onClick = onClick,
        modifier = modifier
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
                    else -> Color.White.copy(alpha = 0.18f)
                }
            )
            .border(
                width = if (isFocused && !isSelected) 1.dp else 0.dp,
                color = Accent,
                shape = RoundedCornerShape(16.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) Color.White else Color.White
        )
    }
}

@Composable
private fun SeekableTime(
    positionMs: Long,
    onAdjustMinute: (Int) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) SurfaceVariant else Color.Transparent
            )
            .border(
                width = if (isFocused) 1.dp else 0.dp,
                color = Accent,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onAdjustMinute(-1)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        onAdjustMinute(1)
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isFocused) {
                Text("◀ ", style = MaterialTheme.typography.labelLarge, color = Accent)
            }
            Text(
                text = formatTime(positionMs),
                style = MaterialTheme.typography.labelLarge,
                color = if (isFocused) Accent else Color.White
            )
            if (isFocused) {
                Text(" ▶", style = MaterialTheme.typography.labelLarge, color = Accent)
            }
        }
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
