package com.soap4tv.app.data.model

data class ContinueWatchingItem(
    val slug: String,           // series slug from href
    val title: String,          // series title
    val episode: String,        // display string: "s01e07"
    val season: Int,            // season number from href
    val episodeNum: Int,        // episode number from ?continue=N
    val screenshotUrl: String   // episode screenshot URL
)
