package com.soap4tv.app.data.repository

import com.soap4tv.app.data.model.Episode
import com.soap4tv.app.data.model.SeriesDetail
import com.soap4tv.app.data.network.SoapApiClient
import com.soap4tv.app.data.parser.EpisodeListParser
import com.soap4tv.app.data.parser.SeriesDetailParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeriesRepository @Inject constructor(
    private val apiClient: SoapApiClient,
    private val authRepository: AuthRepository
) {
    private val detailCache = ConcurrentHashMap<String, SeriesDetail>()
    private val episodeCache = ConcurrentHashMap<String, List<Episode>>()
    private val detailLock = Mutex()
    private val episodeLock = Mutex()

    suspend fun getSeriesDetail(slug: String, forceRefresh: Boolean = false): Result<SeriesDetail> {
        if (!forceRefresh) {
            detailCache[slug]?.let { return Result.success(it) }
        }
        return detailLock.withLock {
            if (!forceRefresh) {
                detailCache[slug]?.let { return@withLock Result.success(it) }
            }
            apiClient.fetchPage("/soap/$slug/").map { html ->
                SeriesDetailParser.parseSeriesDetail(html, slug).also { detailCache[slug] = it }
            }
        }
    }

    suspend fun getEpisodes(
        slug: String,
        season: Int,
        forceRefresh: Boolean = false
    ): Result<List<Episode>> {
        val key = "$slug/$season"
        if (!forceRefresh) {
            episodeCache[key]?.let { return Result.success(it) }
        }
        return episodeLock.withLock {
            if (!forceRefresh) {
                episodeCache[key]?.let { return@withLock Result.success(it) }
            }
            apiClient.fetchPage("/soap/$slug/$season/").map { html ->
                val (_, episodes) = EpisodeListParser.parseEpisodes(html)
                episodes.also { episodeCache[key] = it }
            }
        }
    }

    suspend fun toggleWatching(seriesId: Int, currentlyWatching: Boolean): Result<Unit> {
        val token = authRepository.getToken() ?: return Result.failure(Exception("Not authenticated"))
        val action = if (currentlyWatching) "unwatch" else "watch"
        return apiClient.apiPost(
            path = "/api/v2/soap/$action/$seriesId/",
            token = token
        ).map { }
    }

    suspend fun markEpisodeWatched(eid: Int, token: String, watched: Boolean): Result<Unit> {
        val what = if (watched) "mark_watched" else "mark_unwatched"
        return apiClient.callbackPost(
            params = mapOf(
                "what" to what,
                "eid" to eid.toString(),
                "token" to token
            )
        ).map { }
    }

    fun invalidateCache(slug: String? = null) {
        if (slug != null) {
            detailCache.remove(slug)
            episodeCache.keys.filter { it.startsWith(slug) }.forEach { episodeCache.remove(it) }
        } else {
            detailCache.clear()
            episodeCache.clear()
        }
    }
}
