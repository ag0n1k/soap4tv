package com.soap4tv.app.data.repository

import com.soap4tv.app.data.network.SoapApiClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single fetch point for the site's main page.
 *
 * `/` is wanted by two unrelated consumers at startup: [AuthRepository] needs the API token
 * out of it, [CatalogRepository] needs the series catalog. They used to download and parse
 * the same ~1 MB page twice within a second of each other. Here the second caller either
 * gets the cached HTML or, if the first request is still in flight, waits on the mutex and
 * shares its result.
 *
 * The cached HTML is deliberately short-lived: it exists to collapse a burst of startup
 * requests, not to serve stale content.
 */
@Singleton
class MainPageSource @Inject constructor(
    private val apiClient: SoapApiClient
) {
    companion object {
        private const val DEFAULT_MAX_AGE_MS = 30_000L
    }

    private val lock = Mutex()

    @Volatile private var cachedHtml: String? = null
    @Volatile private var fetchedAt = 0L

    suspend fun html(
        forceRefresh: Boolean = false,
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS
    ): Result<String> {
        if (!forceRefresh) fresh(maxAgeMs)?.let { return Result.success(it) }
        return lock.withLock {
            // The request we queued behind may have just filled the cache.
            if (!forceRefresh) fresh(maxAgeMs)?.let { return@withLock Result.success(it) }
            apiClient.fetchPage("/").onSuccess { html ->
                cachedHtml = html
                fetchedAt = System.currentTimeMillis()
            }
        }
    }

    fun invalidate() {
        cachedHtml = null
        fetchedAt = 0L
    }

    private fun fresh(maxAgeMs: Long): String? {
        val html = cachedHtml ?: return null
        if (System.currentTimeMillis() - fetchedAt > maxAgeMs) {
            // Drop the reference so a megabyte of HTML doesn't sit in memory for the session.
            cachedHtml = null
            return null
        }
        return html
    }
}
