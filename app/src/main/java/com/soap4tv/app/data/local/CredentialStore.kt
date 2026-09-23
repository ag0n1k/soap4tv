package com.soap4tv.app.data.local

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.soap4tv.app.data.network.CookieCrypto
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CredentialStore"

/**
 * Credentials the user typed on the login screen, kept encrypted at rest with the same
 * Keystore-backed AES-GCM key as the cookie jar.
 *
 * They exist so that a genuinely expired session can be renewed silently: typing a
 * password with a remote every time the server rotates PHPSESSID is the single worst
 * part of the app. Nothing is ever hardcoded — the store is empty until a successful
 * login fills it, and [clear] wipes it on logout or on credentials the server rejects.
 */
@Singleton
class CredentialStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    data class Credentials(val username: String, val password: String)

    companion object {
        private val CREDENTIALS_KEY = stringPreferencesKey("credentials_v1")
    }

    suspend fun save(username: String, password: String) {
        val payload = JSONObject()
            .put("username", username)
            .put("password", password)
            .toString()
        val encrypted = try {
            CookieCrypto.encrypt(payload)
        } catch (e: Exception) {
            Log.w(TAG, "Could not encrypt credentials: ${e.message}")
            return
        }
        dataStore.edit { prefs -> prefs[CREDENTIALS_KEY] = encrypted }
    }

    suspend fun load(): Credentials? {
        val stored = dataStore.data.first()[CREDENTIALS_KEY] ?: return null
        val plaintext = CookieCrypto.decrypt(stored) ?: run {
            Log.w(TAG, "Stored credentials could not be decrypted — dropping them")
            clear()
            return null
        }
        return try {
            val obj = JSONObject(plaintext)
            val username = obj.optString("username")
            val password = obj.optString("password")
            if (username.isBlank() || password.isBlank()) null
            else Credentials(username, password)
        } catch (e: Exception) {
            Log.w(TAG, "Corrupt credential payload: ${e.message}")
            null
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(CREDENTIALS_KEY) }
    }
}
