package com.soap4tv.app.data.model

sealed interface AuthState {
    data object Unknown : AuthState
    data object LoggedIn : AuthState
    data object LoggedOut : AuthState
    data class Error(val message: String) : AuthState
}
