package com.soap4tv.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.soap4tv.app.data.model.AuthState
import com.soap4tv.app.data.network.SoapApiClient
import com.soap4tv.app.data.network.SoapCookieJar
import com.soap4tv.app.data.parser.TokenParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val apiClient: SoapApiClient,
    private val cookieJar: SoapCookieJar,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val USERNAME_KEY = stringPreferencesKey("username")
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // In-memory token cache — re-fetched from HTML when needed.
    // Mutex guards refresh so concurrent callers coalesce onto a single network round-trip.
    @Volatile
    private var cachedToken: String? = null
    private val tokenMutex = Mutex()

    /**
     * Check if a valid session exists on startup.
     * Returns true if the user is already logged in.
     */
    suspend fun checkSession(): Boolean {
        if (!cookieJar.hasCookies()) {
            _authState.value = AuthState.LoggedOut
            return false
        }
        return try {
            val html = apiClient.fetchPage("/").getOrElse {
                _authState.value = AuthState.LoggedOut
                return false
            }
            if (TokenParser.isAuthenticated(html)) {
                cachedToken = TokenParser.parseToken(html)
                _authState.value = AuthState.LoggedIn
                true
            } else {
                _authState.value = AuthState.LoggedOut
                false
            }
        } catch (e: Exception) {
            _authState.value = AuthState.LoggedOut
            false
        }
    }

    /**
     * Login with username and password.
     */
    suspend fun login(username: String, password: String): Result<Unit> {
        return try {
            val html = apiClient.login(username, password).getOrThrow()

            if (TokenParser.isAuthenticated(html)) {
                cachedToken = TokenParser.parseToken(html)
                // Also try fetching main page to ensure full auth and token
                if (cachedToken.isNullOrBlank()) {
                    val mainHtml = apiClient.fetchPage("/").getOrElse { "" }
                    cachedToken = TokenParser.parseToken(mainHtml)
                }
                dataStore.edit { prefs -> prefs[USERNAME_KEY] = username }
                _authState.value = AuthState.LoggedIn
                Result.success(Unit)
            } else {
                // Check for error message in the response HTML
                val errorMsg = extractLoginError(html) ?: "Login failed. Check your credentials."
                _authState.value = AuthState.Error(errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            val msg = e.message ?: "Network error"
            _authState.value = AuthState.Error(msg)
            Result.failure(e)
        }
    }

    /**
     * Get the current API token.
     * Uses cached value or re-fetches from main page.
     */
    suspend fun getToken(): String? {
        cachedToken?.let { return it }
        return tokenMutex.withLock {
            // Another caller might have populated the token while we awaited the lock.
            cachedToken ?: refreshTokenLocked()
        }
    }

    /**
     * Force re-fetch the token from main page. Concurrent callers share one network round-trip.
     */
    suspend fun refreshToken(): String? = tokenMutex.withLock { refreshTokenLocked() }

    private suspend fun refreshTokenLocked(): String? {
        return try {
            val html = apiClient.fetchPage("/").getOrElse { return null }
            if (TokenParser.isAuthenticated(html)) {
                cachedToken = TokenParser.parseToken(html)
                if (cachedToken.isNullOrBlank()) {
                    _authState.value = AuthState.LoggedOut
                }
                cachedToken
            } else {
                _authState.value = AuthState.LoggedOut
                cachedToken = null
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Clear session and logout.
     */
    suspend fun logout() {
        cachedToken = null
        cookieJar.clearCookies()
        dataStore.edit { prefs -> prefs.remove(USERNAME_KEY) }
        _authState.value = AuthState.LoggedOut
    }

    /**
     * Execute a block with a valid token, retrying once with a fresh token on failure.
     * On second failure, triggers logout if it looks like an auth error.
     */
    suspend fun <T> withTokenRefresh(block: suspend (token: String) -> Result<T>): Result<T> {
        val token = getToken() ?: run {
            _authState.value = AuthState.LoggedOut
            return Result.failure(Exception("Not authenticated"))
        }
        val result = block(token)
        if (result.isSuccess) return result

        // Retry with refreshed token
        val newToken = refreshToken() ?: run {
            _authState.value = AuthState.LoggedOut
            return Result.failure(Exception("Session expired, please log in again"))
        }
        return block(newToken)
    }

    suspend fun getSavedUsername(): String? {
        return dataStore.data.first()[USERNAME_KEY]
    }

    private fun extractLoginError(html: String): String? {
        // Site shows error in #message div: "Не, не угадали, попробуйте ещё пару раз"
        val regex = Regex("""id="message"[^>]*>([^<]+)""")
        return regex.find(html)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }
}
