package com.soap4tv.app.data.model

data class Season(
    val number: Int,      // season number extracted from URL or text
    val coverUrl: String, // poster image URL
    val url: String       // full path: /soap/{slug}/{number}/
)
