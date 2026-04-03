package com.soap4tv.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_progress")
data class WatchProgressEntity(
    @PrimaryKey
    val episodeId: Int,         // eid for series, movie_id for movies (negated: -movieId)
    val contentType: String,    // "series" | "movie"
    val contentId: String,      // series slug or movie id as string
    val seasonNumber: Int,      // 0 for movies
    val episodeNumber: Int,     // 0 for movies
    val title: String,          // episode/movie title for display
    val seriesTitle: String,    // series title (same as title for movies)
    val coverUrl: String,
    val positionMs: Long,       // playback position in milliseconds
    val durationMs: Long,       // total duration in milliseconds (0 if unknown)
    val lastWatched: Long,      // System.currentTimeMillis()
    val isCompleted: Boolean    // true if position > 90% of duration
) {
    val progressPercent: Int
        get() = if (durationMs > 0) ((positionMs * 100) / durationMs).toInt().coerceIn(0, 100) else 0
}
