package com.soap4tv.app.ui.screen.player

import android.app.Activity
import android.content.ContextWrapper
import android.util.Log
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
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
    // Track the user's language preference across episode switches. The ExoPlayer
    // instance (and its TrackSelectionOverride) is rebuilt on each episode, but these
    // strings persist so we can re-apply the selection in onTracksChanged.
    var preferredSubtitleLang by remember { mutableStateOf<String?>(null) }
    var preferredSubtitleDisabled by remember { mutableStateOf(false) }
    var preferredAudioLang by remember { mutableStateOf<String?>(null) }
    var pendingSeekMs by remember { mutableStateOf(0L) }
    var userAdjustedSeek by remember { mutableStateOf(false) }
    var showDebug by remember { mutableStateOf(false) }
    var debugStats by remember { mutableStateOf(DebugStats()) }
    var currentCueText by remember { mutableStateOf("") }
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
        // Keep auto-detection (HLS / MP4 / DASH) — some movies come through as direct MP4.
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        // Buffer sizing tuned for 2GB-RAM Android TVs (e.g. TCL P7K): large enough to ride
        // out short network dips, small enough to avoid system GC pressure at 4K bitrates.
        // Hard byte cap at ~64MB stops a 25Mbps stream from filling 150MB+ of RAM.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000
            )
            .setTargetBufferBytes(64 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        val player = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        // Seed the DefaultTrackSelector with the user's prior choices so Media3 picks
        // the right audio/subtitle track before playback starts — no visible flicker.
        run {
            var params = player.trackSelectionParameters.buildUpon()
            preferredSubtitleLang?.let { params = params.setPreferredTextLanguage(it) }
            preferredAudioLang?.let { params = params.setPreferredAudioLanguage(it) }
            if (preferredSubtitleDisabled) {
                params = params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            }
            player.trackSelectionParameters = params.build()
        }

        player.apply {
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

            addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                override fun onDroppedVideoFrames(
                    eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                    droppedFrames: Int,
                    elapsedMs: Long
                ) {
                    Log.w("Soap4tvPlayer", "dropped=$droppedFrames over ${elapsedMs}ms at ${eventTime.eventPlaybackPositionMs}ms")
                    debugStats = debugStats.copy(
                        totalDropped = debugStats.totalDropped + droppedFrames,
                        recentDropped = droppedFrames
                    )
                }
                override fun onVideoInputFormatChanged(
                    eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                    format: androidx.media3.common.Format,
                    decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
                ) {
                    Log.i("Soap4tvPlayer", "videoFormat ${format.width}x${format.height}@${format.frameRate}fps ${format.bitrate / 1000}kbps codec=${format.sampleMimeType}")
                    debugStats = debugStats.copy(
                        width = format.width,
                        height = format.height,
                        fps = format.frameRate,
                        bitrateKbps = (format.bitrate / 1000).coerceAtLeast(0),
                        codec = format.sampleMimeType ?: ""
                    )
                }
                override fun onBandwidthEstimate(
                    eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                    totalLoadTimeMs: Int,
                    totalBytesLoaded: Long,
                    bitrateEstimate: Long
                ) {
                    debugStats = debugStats.copy(bandwidthKbps = bitrateEstimate / 1000)
                }
            })

            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED && viewModel.hasNextEpisode) {
                        viewModel.playNextEpisode()
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e("Soap4tvPlayer", "error: ${error.errorCodeName} — ${error.message}", error)
                }
                // Capture cues in Compose state so we can render them in a small Box at
                // the bottom instead of the full-screen PlayerView SubtitleView. This keeps
                // the video surface in hardware overlay on low-end GPUs (Mali-G52 MP2).
                override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                    currentCueText = cueGroup.cues.joinToString("\n") { cue ->
                        cue.text?.toString().orEmpty()
                    }
                }
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    val names = mutableListOf<String>()
                    val audioLangs = mutableListOf<String?>()
                    for (group in tracks.groups) {
                        if (group.type == C.TRACK_TYPE_AUDIO && group.isSupported) {
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                val lang = format.language ?: "Audio ${names.size + 1}"
                                names.add(lang)
                                audioLangs.add(format.language)
                            }
                        }
                    }
                    audioTracks = names

                    val subNames = mutableListOf<String>()
                    val subLangs = mutableListOf<String?>()
                    for (group in tracks.groups) {
                        if (group.type == C.TRACK_TYPE_TEXT && group.isSupported) {
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                val label = format.label ?: format.language ?: "Sub ${subNames.size + 1}"
                                subNames.add(label)
                                subLangs.add(format.language ?: format.label)
                            }
                        }
                    }
                    subtitleTracks = subNames

                    // Re-apply user's prior subtitle choice on the new player instance.
                    if (preferredSubtitleDisabled) {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                        selectedSubtitleTrack = -1
                    } else if (preferredSubtitleLang != null) {
                        val idx = subLangs.indexOfFirst { it == preferredSubtitleLang }
                        if (idx >= 0) {
                            selectedSubtitleTrack = idx
                            var walked = 0
                            for (group in tracks.groups) {
                                if (group.type == C.TRACK_TYPE_TEXT && group.isSupported) {
                                    if (walked == idx) {
                                        player.trackSelectionParameters =
                                            player.trackSelectionParameters.buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                .setOverrideForType(
                                                    androidx.media3.common.TrackSelectionOverride(
                                                        group.mediaTrackGroup, 0
                                                    )
                                                )
                                                .build()
                                        break
                                    }
                                    walked++
                                }
                            }
                        }
                    }

                    // Same for audio — if user picked a language on a prior episode, keep it.
                    if (preferredAudioLang != null) {
                        val idx = audioLangs.indexOfFirst { it == preferredAudioLang }
                        if (idx >= 0) {
                            selectedAudioTrack = idx
                            var walked = 0
                            for (group in tracks.groups) {
                                if (group.type == C.TRACK_TYPE_AUDIO && group.isSupported) {
                                    if (walked == idx) {
                                        player.trackSelectionParameters =
                                            player.trackSelectionParameters.buildUpon()
                                                .setOverrideForType(
                                                    androidx.media3.common.TrackSelectionOverride(
                                                        group.mediaTrackGroup, 0
                                                    )
                                                )
                                                .build()
                                        break
                                    }
                                    walked++
                                }
                            }
                        }
                    }
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
            if (showDebug) {
                val bufferedMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0)
                debugStats = debugStats.copy(bufferedMs = bufferedMs)
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

    // Request a display mode that matches the content frame rate so the panel can
    // switch from 60 Hz to 24/48/50 Hz where supported.
    //
    // Split into two effects:
    //   1) LaunchedEffect applies the mode when rounded fps changes. No onDispose so
    //      float jitter (23.976 ↔ 23.9761) doesn't cause a reset→reapply churn that
    //      was burning ~50 dropped frames per re-trigger.
    //   2) DisposableEffect resets the mode once, when we leave the player screen.
    val currentActivity = remember(context) { findActivity(context) }
    val roundedFps = (debugStats.fps * 100).toInt()  // 23.976 → 2397
    LaunchedEffect(currentActivity, roundedFps) {
        val activity = currentActivity ?: return@LaunchedEffect
        val fps = debugStats.fps
        if (fps <= 0f) return@LaunchedEffect
        runCatching {
            val window = activity.window
            val modes = window.decorView.display?.supportedModes ?: emptyArray()
            val allHz = modes.map { "%.1f".format(it.refreshRate) }.distinct().sorted()

            // Only pick a mode whose refresh is a near-integer multiple of fps (e.g.
            // 24/48/72/120 for 23.976 source) — that gives a clean N:1 cadence.
            // Tested on TCL P7K: requesting 30 Hz for 23.98 fps made stutter visibly
            // worse (1.25 frame-hold pattern), so if no integer multiple exists we keep
            // the default 60 Hz where the panel at least does standard 3:2 pulldown.
            val best = modes
                .filter { mode ->
                    val n = mode.refreshRate / fps
                    val nearestInt = kotlin.math.round(n)
                    nearestInt >= 1 && kotlin.math.abs(n - nearestInt) < 0.05
                }
                .maxByOrNull { it.refreshRate }

            if (best != null) {
                val lp = window.attributes
                if (lp.preferredDisplayModeId != best.modeId) {
                    lp.preferredDisplayModeId = best.modeId
                    window.attributes = lp
                    Log.i("Soap4tvPlayer", "requested display mode ${best.physicalWidth}x${best.physicalHeight}@${best.refreshRate}Hz for ${fps}fps content")
                }
                debugStats = debugStats.copy(
                    displayModes = allHz.joinToString(","),
                    requestedModeHz = best.refreshRate
                )
            } else {
                // No integer-multiple mode available — leave the panel alone.
                Log.i("Soap4tvPlayer", "no integer-multiple mode for ${fps}fps in $allHz — keeping default")
                debugStats = debugStats.copy(
                    displayModes = allHz.joinToString(","),
                    requestedModeHz = 0f
                )
            }
        }
    }
    DisposableEffect(currentActivity) {
        onDispose {
            val activity = currentActivity ?: return@onDispose
            runCatching {
                val lp = activity.window.attributes
                lp.preferredDisplayModeId = 0
                activity.window.attributes = lp
            }
        }
    }

    // Pause playback when the activity goes to background (Home press on TV remote)
    // so audio doesn't keep playing. Save progress at the same time.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    exoPlayer?.let { p ->
                        viewModel.saveProgress(
                            positionMs = p.currentPosition,
                            durationMs = p.duration.coerceAtLeast(0)
                        )
                        p.playWhenReady = false
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Reset state when a new episode starts (autoplay)
    LaunchedEffect(playbackData) {
        showOverlay = false
        userAdjustedSeek = false
        positionMs = 0L
        pendingSeekMs = 0L
        currentCueText = ""
    }

    // Prefetch subtitle files in the background so the first time the user enables a
    // subtitle track, Media3's OkHttp layer gets warm TCP/TLS connections and the file
    // is already sitting in OS page cache. Avoids the ~1s stall on activation.
    LaunchedEffect(playbackData?.subtitles) {
        val subs = playbackData?.subtitles ?: return@LaunchedEffect
        val client = viewModel.okHttpClient
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            subs.forEach { sub ->
                runCatching {
                    val req = okhttp3.Request.Builder().url(sub.url).build()
                    client.newCall(req).execute().use { resp -> resp.body?.bytes() }
                }
            }
        }
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

    // Re-grab focus after episode switch (autoplay). `isLoading=true` during load
    // removes the focus-catcher Box; when exoPlayer becomes non-null again, focus
    // would otherwise fall back to the NavHost root and BACK would exit the player.
    LaunchedEffect(exoPlayer) {
        if (exoPlayer != null && !showOverlay) {
            delay(50)
            try { playerFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // BACK: if overlay visible — hide it; otherwise — exit to previous screen.
    // Using BackHandler (not onPreviewKeyEvent) so it works even when focus is lost
    // during episode switches or activity lifecycle churn.
    BackHandler {
        if (showOverlay) hideOverlay() else onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false

                // INFO / MENU toggles the debug stats overlay — works regardless of main overlay state.
                if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_INFO ||
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU
                ) {
                    showDebug = !showDebug
                    return@onPreviewKeyEvent true
                }

                if (!showOverlay) {
                    // Overlay hidden: handle all keys ourselves. BACK is intercepted
                    // by BackHandler (works regardless of focus), so it's not here.
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
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            showOverlayFn()
                            true
                        }
                        else -> false
                    }
                } else {
                    // Overlay visible: reset auto-hide on any key. BACK goes to BackHandler.
                    scheduleHideOverlay()
                    false
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
                            // Hide built-in subtitle view: we render cues via Compose in a
                            // small bottom box. Full-screen subtitle view broke hardware
                            // overlay for the video surface on weak GPUs.
                            subtitleView?.visibility = android.view.View.GONE
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Compose-side subtitle renderer — only draws the text area, keeps the
                // rest of the screen clear so the video surface stays in hardware overlay.
                // White text with a strong black shadow for readability on any background,
                // no solid box — keeps the overlay fully transparent.
                if (currentCueText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 48.dp, start = 48.dp, end = 48.dp)
                    ) {
                        Text(
                            text = currentCueText,
                            color = Color.White,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black,
                                    offset = androidx.compose.ui.geometry.Offset(0f, 0f),
                                    blurRadius = 8f
                                )
                            ),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

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
                                    val fmt = group.getTrackFormat(0)
                                    preferredAudioLang = fmt.language ?: fmt.label
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
                            preferredSubtitleDisabled = true
                            preferredSubtitleLang = null
                            exoPlayer.trackSelectionParameters =
                                exoPlayer.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                    .build()
                        } else {
                            preferredSubtitleDisabled = false
                            val tracks = exoPlayer.currentTracks
                            var trackIdx = 0
                            for (group in tracks.groups) {
                                if (group.type == C.TRACK_TYPE_TEXT && group.isSupported) {
                                    if (trackIdx == index) {
                                        val fmt = group.getTrackFormat(0)
                                        preferredSubtitleLang = fmt.language ?: fmt.label
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

                if (showDebug) {
                    DebugStatsOverlay(stats = debugStats, modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp))
                }
            }
        }
    }
}

private fun findActivity(ctx: android.content.Context): Activity? {
    var c = ctx
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

data class DebugStats(
    val width: Int = 0,
    val height: Int = 0,
    val fps: Float = 0f,
    val bitrateKbps: Int = 0,
    val codec: String = "",
    val bandwidthKbps: Long = 0L,
    val totalDropped: Long = 0L,
    val recentDropped: Int = 0,
    val bufferedMs: Long = 0L,
    val displayModes: String = "",
    val requestedModeHz: Float = 0f
)

@Composable
private fun DebugStatsOverlay(stats: DebugStats, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        val codecShort = stats.codec.substringAfterLast("/").ifBlank { "—" }
        val bufferedSec = stats.bufferedMs / 1000
        val text = buildString {
            appendLine("${stats.width}x${stats.height} @ ${"%.2f".format(stats.fps)} fps")
            appendLine("codec: $codecShort   stream: ${stats.bitrateKbps} kbps")
            appendLine("bandwidth: ${stats.bandwidthKbps} kbps")
            appendLine("buffered: ${bufferedSec}s")
            appendLine("dropped: total=${stats.totalDropped}, last=${stats.recentDropped}")
            appendLine("panel modes: ${stats.displayModes.ifBlank { "—" }} Hz")
            append("requested: ${"%.2f".format(stats.requestedModeHz)} Hz")
        }
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
