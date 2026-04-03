package com.soap4tv.app.data.model

data class Movie(
    val id: Int,            // from id="movie-{id}"
    val name: String,       // span.name
    val coverUrl: String,   // img src="/assets/covers/movies/{id}_small.jpg"
    val year: Int,          // data:year or span.year
    val likes: Int,         // data:likes
    val imdbRating: Double, // data:imdb
    val isWatching: Boolean // data:watching != "0"
)
