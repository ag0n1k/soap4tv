package com.soap4tv.app.data.network

import kotlinx.coroutines.flow.MutableStateFlow

data class PlayerHttpSnapshot(
    val totalCalls: Int = 0,
    val totalConnects: Int = 0,
    val totalErrors: Int = 0,
    val lastTtfbMs: Long = 0L,
    val lastResponseCode: Int = 0,
    val lastContentLengthBytes: Long = 0L,
    val lastConnHeader: String = "",
    val lastUrlTail: String = "",
    val lastErrorClass: String = "",
    val lastErrorMessage: String = "",
    val lastConnectMs: Long = 0L
)

object PlayerHttpStats {
    val flow = MutableStateFlow(PlayerHttpSnapshot())

    fun update(transform: (PlayerHttpSnapshot) -> PlayerHttpSnapshot) {
        flow.value = transform(flow.value)
    }
}
