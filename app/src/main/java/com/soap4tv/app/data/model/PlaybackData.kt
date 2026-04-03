package com.soap4tv.app.data.model

data class PlaybackData(
    val streamUrl: String,
    val subtitles: List<SubtitleTrack>,
    val posterUrl: String,
    val title: String,
    val startFrom: Long  // seconds from server; convert to ms for ExoPlayer
)
