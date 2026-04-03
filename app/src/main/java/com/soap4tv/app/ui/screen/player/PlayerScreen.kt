package com.soap4tv.app.ui.screen.player

import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
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
    var selectedAudioTrack by remember { mutableStateOf(0) }
    var selectedSubtitleTrack by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    var hideOverlayJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Auto-hide overlay after 3s of inactivity
    fun scheduleHideOverlay() {
        hideOverlayJob?.cancel()
        hideOverlayJob = scope.launch {
            delay(3_000)
            showOverlay = false
        }
    }

    fun showOverlayAndScheduleHide() {
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
                }
            })
        }
    }

    // Position tracking + progress save
    LaunchedEffect(exoPlayer) {
        if (exoPlayer == null) return@LaunchedEffect
        while (true) {
            delay(1_000)
            if (exoPlayer.isPlaying) {
                positionMs = exoPlayer.currentPosition
                durationMs = exoPlayer.duration.coerceAtLeast(0)
            }
        }
    }

    // Save progress every 30s
    LaunchedEffect(exoPlayer) {
        if (exoPlayer == null) return@LaunchedEffect
        while (true) {
            delay(30_000)
            viewModel.saveProgress(
                positionMs = exoPlayer.currentPosition,
                durationMs = exoPlayer.duration.coerceAtLeast(0)
            )
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

    // Show overlay on start
    LaunchedEffect(Unit) {
        scheduleHideOverlay()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> {
                        if (showOverlay) {
                            exoPlayer?.let { p ->
                                if (p.isPlaying) p.pause() else p.play()
                            }
                        }
                        showOverlayAndScheduleHide()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (showOverlay) {
                            exoPlayer?.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0))
                        }
                        showOverlayAndScheduleHide()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (showOverlay) {
                            val dur = exoPlayer?.duration ?: Long.MAX_VALUE
                            exoPlayer?.seekTo(
                                (exoPlayer.currentPosition + 10_000).coerceAtMost(dur)
                            )
                        }
                        showOverlayAndScheduleHide()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        showOverlayAndScheduleHide()
                        false // let focus handling continue
                    }
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE -> {
                        if (showOverlay) {
                            onBack()
                        } else {
                            showOverlayAndScheduleHide()
                        }
                        true
                    }
                    else -> false
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
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                PlayerOverlay(
                    isVisible = showOverlay,
                    title = playbackData?.title ?: "",
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    hasSubtitles = (playbackData?.subtitles?.isNotEmpty() == true),
                    audioTracks = audioTracks,
                    selectedAudioTrack = selectedAudioTrack,
                    selectedSubtitleTrack = selectedSubtitleTrack,
                    onPlayPause = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        showOverlayAndScheduleHide()
                    },
                    onSeekBack = {
                        exoPlayer.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0))
                        showOverlayAndScheduleHide()
                    },
                    onSeekForward = {
                        val dur = exoPlayer.duration.coerceAtLeast(0)
                        exoPlayer.seekTo((exoPlayer.currentPosition + 10_000).coerceAtMost(dur))
                        showOverlayAndScheduleHide()
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
                        showOverlayAndScheduleHide()
                    },
                    onSelectSubtitle = { index ->
                        selectedSubtitleTrack = index
                        val params = exoPlayer.trackSelectionParameters.buildUpon()
                        if (index < 0) {
                            params.setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        }
                        exoPlayer.trackSelectionParameters = params.build()
                        showOverlayAndScheduleHide()
                    },
                    onBack = onBack,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
