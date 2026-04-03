package com.soap4tv.app.data.model

data class SubtitleTrack(
    val label: String,  // e.g. "Русский", "English"
    val url: String,    // full URL or path to .srt file
    val language: String // ISO code: "ru", "en"
)
