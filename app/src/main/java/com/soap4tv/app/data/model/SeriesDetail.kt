package com.soap4tv.app.data.model

data class SeriesDetail(
    val id: Int,
    val slug: String,
    val title: String,          // English title
    val titleRu: String,        // Russian title
    val description: String,
    val status: String,         // "закрыт" or "активен"
    val year: Int,
    val duration: String,       // "3 дня 23 часа и 40 минут (43 минуты)"
    val country: String,
    val network: String,
    val genres: String,
    val actors: String,
    val soapRating: String,
    val imdbRating: String,
    val kinopoiskRating: String,
    val coverUrl: String,       // first season cover or series cover
    val seasons: List<Season>,
    val watchCount: Int,
    val isWatching: Boolean
)
