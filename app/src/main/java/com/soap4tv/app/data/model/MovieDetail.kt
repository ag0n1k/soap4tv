package com.soap4tv.app.data.model

data class MovieDetail(
    val id: Int,
    val title: String,
    val year: Int,
    val duration: String,
    val countries: String,
    val qualities: String,
    val genres: String,
    val description: String,
    val hlsUrl: String,            // parsed from JS: file: "..."
    val subtitles: List<SubtitleTrack>,
    val posterUrl: String,         // from JS: poster: "..."
    val infoRows: Map<String, String>, // all info-grid rows
    val watchCount: Int,
    val userRating: Float,
    val isBookmarked: Boolean,
    val isWatched: Boolean
)
