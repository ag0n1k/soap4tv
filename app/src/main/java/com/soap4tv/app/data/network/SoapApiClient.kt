package com.soap4tv.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoapApiClient @Inject constructor(
    val okHttpClient: OkHttpClient
) {
    companion object {
        const val BASE_URL = "https://soap4youand.me"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 9; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    /**
     * POST /login/ to authenticate.
     * Returns the HTML of the response page (contains #token if successful).
     */
    suspend fun login(username: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .addEncoded("login", username)
                .addEncoded("password", password)
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/login/")
                .post(body)
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", BASE_URL)
                .header("Referer", "$BASE_URL/login/")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val html = response.body?.string() ?: ""
            response.close()
            Result.success(html)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * GET a page and return its HTML.
     */
    suspend fun fetchPage(path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = if (path.startsWith("http")) path else "$BASE_URL$path"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val html = response.body?.string() ?: ""
            response.close()
            Result.success(html)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * POST to API v2 endpoints.
     * Headers: x-api-token, x-user-agent: "browser: public v0.1"
     * Returns JSON string.
     */
    suspend fun apiPost(
        path: String,
        token: String,
        params: Map<String, String> = emptyMap()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bodyBuilder = FormBody.Builder()
            params.forEach { (key, value) -> bodyBuilder.add(key, value) }

            val request = Request.Builder()
                .url("$BASE_URL$path")
                .post(bodyBuilder.build())
                .header("User-Agent", USER_AGENT)
                .header("x-api-token", token)
                .header("x-user-agent", "browser: public v0.1")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val json = response.body?.string() ?: ""
            response.close()
            Result.success(json)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * POST /callback/ for marking episodes watched/unwatched.
     */
    suspend fun callbackPost(params: Map<String, String>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bodyBuilder = FormBody.Builder()
            params.forEach { (key, value) -> bodyBuilder.add(key, value) }

            val request = Request.Builder()
                .url("$BASE_URL/callback/")
                .post(bodyBuilder.build())
                .header("User-Agent", USER_AGENT)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val json = response.body?.string() ?: ""
            response.close()
            Result.success(json)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
