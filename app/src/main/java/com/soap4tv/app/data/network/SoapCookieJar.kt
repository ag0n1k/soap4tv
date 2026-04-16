package com.soap4tv.app.data.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoapCookieJar @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : CookieJar {

    companion object {
        private val COOKIES_KEY = stringPreferencesKey("cookies")
        private const val TARGET_DOMAIN = "soap4youand.me"
    }

    // Singleton-scoped, app-lifetime coroutine for async persistence.
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Thread-safe in-memory store keyed by cookie name.
    private val cookieCache = ConcurrentHashMap<String, Cookie>()

    // Initial load happens synchronously once — this is acceptable at process start,
    // before any network call; afterwards all CookieJar methods are non-blocking.
    @Volatile
    private var loaded = false
    private val loadLock = Any()

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(loadLock) {
            if (loaded) return
            val stored = runBlocking {
                dataStore.data.first()[COOKIES_KEY] ?: ""
            }
            val plaintext = if (stored.isBlank()) "" else CookieCrypto.decrypt(stored) ?: ""
            if (plaintext.isNotBlank()) {
                deserializeCookies(plaintext).forEach { cookieCache[it.name] = it }
            }
            loaded = true
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!url.host.contains(TARGET_DOMAIN)) return
        if (cookies.isEmpty()) return
        ensureLoaded()

        cookies.forEach { cookieCache[it.name] = it }
        persistAsync()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!url.host.contains(TARGET_DOMAIN)) return emptyList()
        ensureLoaded()

        val now = System.currentTimeMillis()
        return cookieCache.values.filter { cookie ->
            cookie.expiresAt > now || !cookie.persistent
        }
    }

    fun clearCookies() {
        cookieCache.clear()
        loaded = true
        persistenceScope.launch {
            dataStore.edit { prefs -> prefs.remove(COOKIES_KEY) }
        }
    }

    fun hasCookies(): Boolean {
        ensureLoaded()
        return cookieCache.containsKey("PHPSESSID")
    }

    private fun persistAsync() {
        val snapshot = cookieCache.values.toList()
        persistenceScope.launch {
            val encrypted = CookieCrypto.encrypt(serializeCookies(snapshot))
            dataStore.edit { prefs ->
                prefs[COOKIES_KEY] = encrypted
            }
        }
    }

    private fun serializeCookies(cookies: List<Cookie>): String {
        val array = JSONArray()
        for (cookie in cookies) {
            val obj = JSONObject().apply {
                put("name", cookie.name)
                put("value", cookie.value)
                put("domain", cookie.domain)
                put("path", cookie.path)
                put("expiresAt", cookie.expiresAt)
                put("secure", cookie.secure)
                put("httpOnly", cookie.httpOnly)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeCookies(json: String): List<Cookie> {
        val result = mutableListOf<Cookie>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val cookie = Cookie.Builder()
                    .name(obj.getString("name"))
                    .value(obj.getString("value"))
                    .domain(obj.getString("domain"))
                    .path(obj.getString("path"))
                    .expiresAt(obj.getLong("expiresAt"))
                    .apply {
                        if (obj.optBoolean("secure")) secure()
                        if (obj.optBoolean("httpOnly")) httpOnly()
                    }
                    .build()
                result.add(cookie)
            }
        } catch (_: Exception) {
            // Corrupt store — user re-logs in.
        }
        return result
    }
}
