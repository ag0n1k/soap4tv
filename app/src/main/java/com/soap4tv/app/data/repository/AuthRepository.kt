package com.soap4tv.app.data.repository

import android.util.Log
import com.soap4tv.app.data.local.CredentialStore
import com.soap4tv.app.data.model.AuthState
import com.soap4tv.app.data.network.SoapApiClient
import com.soap4tv.app.data.network.SoapCookieJar
import com.soap4tv.app.data.parser.TokenParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuthRepository"

@Singleton
class AuthRepository @Inject constructor(
    private val apiClient: SoapApiClient,
    private val cookieJar: SoapCookieJar,
    private val credentialStore: CredentialStore,
    private val mainPage: MainPageSource
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /**
     * Incremented whenever the session was silently renewed underneath the UI.
     * Screens that may have loaded a login page instead of real content observe this
     * and refetch. See [silentRelogin].
     */
    private val _sessionGeneration = MutableStateFlow(0)
    val sessionGeneration: StateFlow<Int> = _sessionGeneration.asStateFlow()

    // In-memory token cache — re-fetched from HTML when needed.
    // Mutex guards refresh so concurrent callers coalesce onto a single network round-trip.
    @Volatile
    private var cachedToken: String? = null
    private val tokenMutex = Mutex()

    /**
     * Decide the startup state.
     *
     * A stored session cookie is trusted immediately so the UI can open on Home without
     * waiting for a network round-trip; validation then runs against that assumption.
     * A failed validation only logs the user out when the *server* says the session is
     * gone and stored credentials cannot renew it — an offline TV or a 502 never does.
     */
    suspend fun checkSession(): Boolean = withContext(Dispatchers.IO) {
        val hasCookie = cookieJar.hasSession()
        if (!hasCookie && credentialStore.load() == null) {
            _authState.value = AuthState.LoggedOut
            return@withContext false
        }
        if (hasCookie) {
            _authState.value = AuthState.LoggedIn
        }
        val valid = validateSession(hasCookie)
        // Never leave the UI on the startup spinner: if validation could not reach a verdict
        // (offline, and no cookie to fall back on), show the login form — it is prefilled.
        if (_authState.value is AuthState.Unknown) {
            _authState.value = AuthState.LoggedOut
        }
        valid
    }

    private suspend fun validateSession(optimisticallyLoggedIn: Boolean): Boolean {
        // Shared with the catalog load that starts moments later — one download, not two.
        val html = mainPage.html().getOrElse { error ->
            // Offline, DNS hiccup, 5xx — not a logout. Keep whatever session we hold;
            // individual screens surface their own load errors with a retry.
            Log.w(TAG, "Session check failed (transient): ${error.javaClass.simpleName}: ${error.message}")
            return optimisticallyLoggedIn
        }
        if (TokenParser.isAuthenticated(html)) {
            cachedToken = TokenParser.parseToken(html)
            _authState.value = AuthState.LoggedIn
            return true
        }
        // The server answered with a real page and we are not on it — the session is gone.
        return silentRelogin()
    }

    /**
     * Login with username and password. On success the credentials are stored encrypted
     * so [silentRelogin] can renew an expired session without user interaction.
     */
    suspend fun login(username: String, password: String): Result<Unit> {
        return try {
            val html = apiClient.login(username, password).getOrThrow()

            if (TokenParser.isAuthenticated(html)) {
                cachedToken = TokenParser.parseToken(html)
                // Also try fetching main page to ensure full auth and token
                if (cachedToken.isNullOrBlank()) {
                    val mainHtml = mainPage.html(forceRefresh = true).getOrElse { "" }
                    cachedToken = TokenParser.parseToken(mainHtml)
                }
                credentialStore.save(username, password)
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
     * Re-authenticate with stored credentials after the server dropped our session.
     * Returns false without logging out when the retry itself fails on the network —
     * only credentials the server actually rejects end the session.
     */
    private suspend fun silentRelogin(): Boolean {
        val credentials = credentialStore.load() ?: run {
            cachedToken = null
            _authState.value = AuthState.LoggedOut
            return false
        }
        Log.i(TAG, "Session expired — re-authenticating with stored credentials")
        val html = apiClient.login(credentials.username, credentials.password).getOrElse { error ->
            Log.w(TAG, "Silent re-login failed (transient): ${error.message}")
            return false
        }
        if (!TokenParser.isAuthenticated(html)) {
            Log.w(TAG, "Stored credentials rejected — logging out")
            cachedToken = null
            credentialStore.clear()
            _authState.value = AuthState.LoggedOut
            return false
        }
        cachedToken = TokenParser.parseToken(html)
        if (cachedToken.isNullOrBlank()) {
            cachedToken = mainPage.html(forceRefresh = true).getOrNull()
                ?.let { TokenParser.parseToken(it) }
        }
        _authState.value = AuthState.LoggedIn
        _sessionGeneration.value += 1
        return true
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
        // A refresh is a reaction to something going wrong — always go to the network.
        val html = mainPage.html(forceRefresh = true).getOrElse { error ->
            // Transient failure: keep the session, let the caller surface the error.
            Log.w(TAG, "Token refresh failed (transient): ${error.message}")
            return null
        }
        if (TokenParser.isAuthenticated(html)) {
            cachedToken = TokenParser.parseToken(html)
            return cachedToken
        }
        cachedToken = null
        return if (silentRelogin()) cachedToken else null
    }

    /**
     * Clear session and logout.
     */
    suspend fun logout() {
        cachedToken = null
        mainPage.invalidate()
        cookieJar.clearCookies()
        credentialStore.clear()
        _authState.value = AuthState.LoggedOut
    }

    /**
     * Execute a block with a valid token, retrying once with a fresh token on failure.
     *
     * A failing block is not evidence of a dead session — the play API also fails on an
     * expired CDN link, a non-JSON response or a timeout — so the original error is
     * returned as-is when the token cannot be refreshed. Logout happens only inside
     * [silentRelogin], where the server has actually rejected us.
     */
    suspend fun <T> withTokenRefresh(block: suspend (token: String) -> Result<T>): Result<T> {
        val token = getToken()
            ?: return Result.failure(Exception("Не удалось получить токен сессии — проверьте сеть"))
        val result = block(token)
        if (result.isSuccess) return result

        val newToken = refreshToken() ?: return result
        return block(newToken)
    }

    suspend fun savedCredentials(): CredentialStore.Credentials? = credentialStore.load()

    private fun extractLoginError(html: String): String? {
        // Site shows error in #message div: "Не, не угадали, попробуйте ещё пару раз"
        val regex = Regex("""id="message"[^>]*>([^<]+)""")
        return regex.find(html)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }
}
