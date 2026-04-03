package com.soap4tv.app.data.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
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

    // In-memory cache for performance
    private var cookieCache: MutableList<Cookie> = mutableListOf()
    private var cacheLoaded = false

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!url.host.contains(TARGET_DOMAIN)) return
        if (cookies.isEmpty()) return

        // Update in-memory cache
        val newNames = cookies.map { it.name }.toSet()
        cookieCache.removeAll { it.name in newNames }
        cookieCache.addAll(cookies)

        // Persist to DataStore
        runBlocking {
            dataStore.edit { prefs ->
                prefs[COOKIES_KEY] = serializeCookies(cookieCache)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!url.host.contains(TARGET_DOMAIN)) return emptyList()

        // Load from DataStore on first access
        if (!cacheLoaded) {
            runBlocking {
                val prefs = dataStore.data.first()
                val serialized = prefs[COOKIES_KEY] ?: ""
                if (serialized.isNotBlank()) {
                    cookieCache = deserializeCookies(serialized).toMutableList()
                }
            }
            cacheLoaded = true
        }

        // Filter out expired cookies
        val now = System.currentTimeMillis()
        return cookieCache.filter { cookie ->
            cookie.expiresAt > now || cookie.persistent.not()
        }
    }

    fun clearCookies() {
        cookieCache.clear()
        runBlocking {
            dataStore.edit { prefs ->
                prefs.remove(COOKIES_KEY)
            }
        }
    }

    fun hasCookies(): Boolean {
        if (!cacheLoaded) {
            runBlocking {
                val prefs = dataStore.data.first()
                val serialized = prefs[COOKIES_KEY] ?: ""
                if (serialized.isNotBlank()) {
                    cookieCache = deserializeCookies(serialized).toMutableList()
                }
            }
            cacheLoaded = true
        }
        return cookieCache.any { it.name == "PHPSESSID" }
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
        } catch (e: Exception) {
            // Return empty on parse error — user will need to re-login
        }
        return result
    }
}
