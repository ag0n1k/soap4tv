package com.soap4tv.app.data.model

data class Episode(
    val id: Int,               // from collapse target #ep-{id}
    val number: Int,           // data:episode
    val titleRu: String,       // .episode-title
    val titleEn: String,       // .episode-title-en
    val translate: String,     // data:translate ("rus", "eng", etc.)
    val translateLabel: String, // .translate-badge text ("Paramount", "Lostfilm", etc.)
    val quality: Int,          // data:quality: 1=SD, 2=HD, 3=FHD
    val qualityLabel: String,  // .quality-badge text ("SD", "HD", "FHD")
    val isWatched: Boolean,    // .episode-watched div has class "yes"
    val airDate: String,       // meta "Первый показ"
    val stars: String,         // meta "Звёзды"
    val writers: String,       // meta "Сценаристы"
    val description: String,   // .spoiler-text
    // Authenticated-only play fields (null for unauthenticated)
    val eid: String?,          // data:eid on play button (same as id but as String)
    val sid: String?,          // data:sid on play button (series ID)
    val hash: String?,         // data:hash on play button
    val canPlay: Boolean       // play button exists
)
