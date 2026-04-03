package com.soap4tv.app.data.model

data class Series(
    val id: Int,                    // from id="soap-{id}"
    val name: String,               // span.name
    val slug: String,               // href: /soap/{slug}/
    val coverUrl: String,           // img.lazy attr("original-src")
    val year: Int,                  // data:year
    val likes: Int,                 // data:likes
    val imdbRating: Double,         // data:imdb
    val genres: List<String>,       // data:genre split by ","
    val isComplete: Boolean,        // data:complete == "1"
    val isUhd: Boolean,             // data:uhd isNotEmpty
    val isWatching: Boolean,        // data:watching isNotEmpty
    val hasNewEpisodes: Boolean     // data:new isNotEmpty
)
