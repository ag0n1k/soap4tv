package com.soap4tv.app.ui.screen.player

import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.soap4tv.app.ui.components.LoadingIndicator
import com.soap4tv.app.ui.theme.OnSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val playbackData = viewModel.playbackData
    var showOverlay by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var audioTracks by remember { mutableStateOf<List<String>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedAudioTrack by remember { mutableStateOf(0) }
    var selectedSubtitleTrack by remember { mutableStateOf(0) }
    var pendingSeekMs by remember { mutableStateOf(0L) }
    var userAdjustedSeek by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var hideOverlayJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val playerFocusRequester = remember { FocusRequester() }
    val overlayFocusRequester = remember { FocusRequester() }

    fun hideOverlay() {
        hideOverlayJob?.cancel()
        showOverlay = false
    }

    fun scheduleHideOverlay() {
        hideOverlayJob?.cancel()
        hideOverlayJob = scope.launch {
            delay(5_000)
            showOverlay = false
        }
    }

    fun showOverlayFn() {
        pendingSeekMs = positionMs
        userAdjustedSeek = false
        showOverlay = true
        scheduleHideOverlay()
    }

    val exoPlayer = remember(playbackData) {
        if (playbackData == null) return@remember null

        val dataSourceFactory = OkHttpDataSource.Factory(viewModel.okHttpClient)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
            val subtitleConfigs = playbackData.subtitles.map { sub ->
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                    .setLanguage(sub.language)
                    .setLabel(sub.label)
                    .build()
            }

            val mediaItem = MediaItem.Builder()
                .setUri(playbackData.streamUrl)
                .setSubtitleConfigurations(subtitleConfigs)
                .build()

            setMediaItem(mediaItem)
            if (playbackData.startFrom > 0) {
                seekTo(playbackData.startFrom * 1000)
            }
            prepare()
            playWhenReady = true

            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED && viewModel.hasNextEpisode) {
                        viewModel.playNextEpisode()
                    }
                }
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    val names = mutableListOf<String>()
                    for (group in tracks.groups) {
                        if (group.type == C.TRACK_TYPE_AUDIO && group.isSupported) {
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                val lang = format.language ?: "Audio ${names.size + 1}"
                                names.add(lang)
                            }
                        }
                    }
                    audioTracks = names

                    val subNames = mutableListOf<String>()
                    for (group in tracks.groups) {
                        if (group.type == C.TRACK_TYPE_TEXT && group.isSupported) {
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                val label = format.label ?: format.language ?: "Sub ${subNames.size + 1}"
                                subNames.add(label)
                            }
                        }
                    }
                    subtitleTracks = subNames
                }
            })
        }
    }

    // Position tracking (every 1s) + progress save (every 30s) driven by a single coroutine.
    // Tied to exoPlayer key — cancels + restarts cleanly on episode switch.
    LaunchedEffect(exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        var ticks = 0
        while (true) {
            delay(1_000)
            if (player.isPlaying) {
                positionMs = player.currentPosition
                durationMs = player.duration.coerceAtLeast(0)
                if (!userAdjustedSeek) {
                    pendingSeekMs = positionMs
                }
                ticks++
                if (ticks >= 30) {
                    ticks = 0
                    viewModel.saveProgress(
                        positionMs = player.currentPosition,
                        durationMs = player.duration.coerceAtLeast(0)
                    )
                }
            }
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            if (exoPlayer != null) {
                viewModel.saveProgress(
                    positionMs = exoPlayer.currentPosition,
                    durationMs = exoPlayer.duration.coerceAtLeast(0)
                )
                exoPlayer.release()
            }
        }
    }

    // Reset state when a new episode starts (autoplay)
    LaunchedEffect(playbackData) {
        showOverlay = false
        userAdjustedSeek = false
        positionMs = 0L
        pendingSeekMs = 0L
    }

    // Manage focus when overlay visibility changes
    LaunchedEffect(showOverlay) {
        if (showOverlay) {
            delay(100) // Wait for AnimatedVisibility to compose elements
            try { overlayFocusRequester.requestFocus() } catch (_: Exception) {}
        } else {
            try { playerFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false

                if (!showOverlay) {
                    // Overlay hidden: handle all keys ourselves
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER -> {
                            exoPlayer?.let { p ->
                                if (p.isPlaying) p.pause() else p.play()
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            exoPlayer?.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0))
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val dur = exoPlayer?.duration ?: Long.MAX_VALUE
                            exoPlayer?.seekTo(
                                (exoPlayer.currentPosition + 10_000).coerceAtMost(dur)
                            )
                            true
                        }
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_ESCAPE -> {
                            showOverlayFn()
                            true
                        }
                        else -> false
                    }
                } else {
                    // Overlay visible: reset auto-hide on any key, handle back
                    scheduleHideOverlay()
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_ESCAPE -> {
                            onBack()
                            true
                        }
                        else -> false
                    }
                }
            }
    ) {
        when {
            viewModel.isLoading -> LoadingIndicator(modifier = Modifier.fillMaxSize())
            viewModel.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = viewModel.error!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurface
                    )
                }
            }
            exoPlayer != null -> {
                // Invisible focus catcher — receives key events when overlay is hidden
                Box(
                    modifier = Modifier
                        .size(1.dp)
                        .focusRequester(playerFocusRequester)
                        .focusable()
                )

                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            isFocusable = false
                            isFocusableInTouchMode = false
                            keepScreenOn = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                PlayerOverlay(
                    isVisible = showOverlay,
                    playPauseFocusRequester = overlayFocusRequester,
                    title = playbackData?.title ?: "",
                    isPlaying = isPlaying,
                    pendingSeekMs = pendingSeekMs,
                    durationMs = durationMs,
                    subtitleTracks = subtitleTracks,
                    audioTracks = audioTracks,
                    selectedAudioTrack = selectedAudioTrack,
                    selectedSubtitleTrack = selectedSubtitleTrack,
                    onPlayPause = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            // Apply pending seek if changed
                            if (pendingSeekMs != exoPlayer.currentPosition) {
                                exoPlayer.seekTo(pendingSeekMs)
                            }
                            exoPlayer.play()
                            hideOverlay()
                        }
                    },
                    onRestart = {
                        exoPlayer.seekTo(0)
                        pendingSeekMs = 0
                        userAdjustedSeek = false
                        exoPlayer.play()
                        hideOverlay()
                    },
                    onSkipToEnd = {
                        if (viewModel.hasNextEpisode) {
                            viewModel.playNextEpisode()
                        } else {
                            exoPlayer.seekTo(exoPlayer.duration)
                        }
                    },
                    onAdjustMinute = { minutes ->
                        val dur = exoPlayer.duration.coerceAtLeast(0)
                        pendingSeekMs = (pendingSeekMs + minutes * 60_000L).coerceIn(0, dur)
                        userAdjustedSeek = true
                    },
                    onSelectAudio = { index ->
                        selectedAudioTrack = index
                        val tracks = exoPlayer.currentTracks
                        var trackIdx = 0
                        for (group in tracks.groups) {
                            if (group.type == C.TRACK_TYPE_AUDIO && group.isSupported) {
                                if (trackIdx == index) {
                                    exoPlayer.trackSelectionParameters =
                                        exoPlayer.trackSelectionParameters.buildUpon()
                                            .setOverrideForType(
                                                androidx.media3.common.TrackSelectionOverride(
                                                    group.mediaTrackGroup, 0
                                                )
                                            )
                                            .build()
                                    break
                                }
                                trackIdx++
                            }
                        }
                        // overlay stays visible
                    },
                    onSelectSubtitle = { index ->
                        selectedSubtitleTrack = index
                        if (index < 0) {
                            exoPlayer.trackSelectionParameters =
                                exoPlayer.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                    .build()
                        } else {
                            val tracks = exoPlayer.currentTracks
                            var trackIdx = 0
                            for (group in tracks.groups) {
                                if (group.type == C.TRACK_TYPE_TEXT && group.isSupported) {
                                    if (trackIdx == index) {
                                        exoPlayer.trackSelectionParameters =
                                            exoPlayer.trackSelectionParameters.buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                .setOverrideForType(
                                                    androidx.media3.common.TrackSelectionOverride(
                                                        group.mediaTrackGroup, 0
                                                    )
                                                )
                                                .build()
                                        break
                                    }
                                    trackIdx++
                                }
                            }
                        }
                        // overlay stays visible
                    },
                    onBack = onBack,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
