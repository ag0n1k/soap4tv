package com.soap4tv.app.data.model

data class PlaybackData(
    val streamUrl: String,
    val subtitles: List<SubtitleTrack>,
    val posterUrl: String,
    val title: String,
    val startFrom: Long,  // seconds from server; convert to ms for ExoPlayer
    // Numeric id of the playable item this snapshot describes. Lets the player's
    // dispose-time saveProgress credit progress / watched-marks to the OLD episode
    // even after the ViewModel has been mutated for autoplay's next episode.
    // Series: positive eid. Movies: -movieId.
    val episodeId: Int = 0
)
