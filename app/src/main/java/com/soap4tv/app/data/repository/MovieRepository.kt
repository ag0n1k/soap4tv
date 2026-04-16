package com.soap4tv.app.data.repository

import com.soap4tv.app.data.model.MovieDetail
import com.soap4tv.app.data.network.SoapApiClient
import com.soap4tv.app.data.parser.MovieDetailParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovieRepository @Inject constructor(
    private val apiClient: SoapApiClient,
    private val authRepository: AuthRepository
) {
    private val detailCache = ConcurrentHashMap<Int, MovieDetail>()
    private val detailLock = Mutex()

    suspend fun getMovieDetail(id: Int, forceRefresh: Boolean = false): Result<MovieDetail> {
        if (!forceRefresh) {
            detailCache[id]?.let { return Result.success(it) }
        }
        return detailLock.withLock {
            if (!forceRefresh) {
                detailCache[id]?.let { return@withLock Result.success(it) }
            }
            apiClient.fetchPage("/movies/$id/").map { html ->
                MovieDetailParser.parseMovieDetail(html, id).also { detailCache[id] = it }
            }
        }
    }

    suspend fun likeMovie(id: Int, like: Boolean): Result<Unit> {
        val token = authRepository.getToken() ?: return Result.failure(Exception("Not authenticated"))
        val action = if (like) "like" else "unlike"
        return apiClient.apiPost(
            path = "/api/v2/movies/like/",
            token = token,
            params = mapOf("id" to id.toString(), "sub" to "like", "do" to action)
        ).map { }
    }

    suspend fun markWatched(id: Int, watched: Boolean): Result<Unit> {
        val token = authRepository.getToken() ?: return Result.failure(Exception("Not authenticated"))
        val path = if (watched) "/api/v2/movies/watch/$id" else "/api/v2/movies/unwatch/$id"
        return apiClient.apiPost(
            path = path,
            token = token,
            params = mapOf("id" to id.toString(), "token" to token)
        ).map { }
    }

    suspend fun rateMovie(id: Int, rating: Int): Result<Unit> {
        val token = authRepository.getToken() ?: return Result.failure(Exception("Not authenticated"))
        return apiClient.apiPost(
            path = "/api/v2/movies/rate/",
            token = token,
            params = mapOf("id" to id.toString(), "rating" to rating.toString())
        ).map { }
    }

    fun invalidateCache(id: Int? = null) {
        if (id != null) detailCache.remove(id) else detailCache.clear()
    }
}
