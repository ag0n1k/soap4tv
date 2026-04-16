package com.soap4tv.app.data.repository

import com.soap4tv.app.data.model.ContinueWatchingItem
import com.soap4tv.app.data.model.Movie
import com.soap4tv.app.data.model.Series
import com.soap4tv.app.data.network.SoapApiClient
import com.soap4tv.app.data.parser.CatalogParser
import com.soap4tv.app.data.parser.MovieCatalogParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

enum class SortOption {
    TITLE, YEAR, IMDB, LIKES, COMPLETE
}

@Singleton
class CatalogRepository @Inject constructor(
    private val apiClient: SoapApiClient
) {
    @Volatile private var cachedSeries: List<Series>? = null
    @Volatile private var cachedMovies: List<Movie>? = null
    @Volatile private var cachedBookmarkedMovies: List<Movie>? = null
    @Volatile private var cachedContinueWatching: List<ContinueWatchingItem>? = null
    @Volatile private var cachedMySeries: List<Series>? = null

    // Mutex coalesces concurrent cache-miss fetches so we don't duplicate HTTP work.
    private val seriesLock = Mutex()
    private val moviesLock = Mutex()
    private val bookmarksLock = Mutex()
    private val myPageLock = Mutex()

    suspend fun getSeries(forceRefresh: Boolean = false): Result<List<Series>> {
        if (!forceRefresh) cachedSeries?.let { return Result.success(it) }
        return seriesLock.withLock {
            if (!forceRefresh) cachedSeries?.let { return@withLock Result.success(it) }
            apiClient.fetchPage("/").map { html ->
                CatalogParser.parseSeries(html).also { cachedSeries = it }
            }
        }
    }

    suspend fun getMovies(forceRefresh: Boolean = false): Result<List<Movie>> {
        if (!forceRefresh) cachedMovies?.let { return Result.success(it) }
        return moviesLock.withLock {
            if (!forceRefresh) cachedMovies?.let { return@withLock Result.success(it) }
            apiClient.fetchPage("/movies/").map { html ->
                MovieCatalogParser.parseMovies(html).also { cachedMovies = it }
            }
        }
    }

    fun sortSeries(series: List<Series>, option: SortOption): List<Series> = when (option) {
        SortOption.TITLE -> series.sortedBy { it.name.lowercase() }
        SortOption.YEAR -> series.sortedByDescending { it.year }
        SortOption.IMDB -> series.sortedByDescending { it.imdbRating }
        SortOption.LIKES -> series.sortedByDescending { it.likes }
        SortOption.COMPLETE -> series.sortedByDescending { it.isComplete }
    }

    fun sortMovies(movies: List<Movie>, option: SortOption): List<Movie> = when (option) {
        SortOption.TITLE -> movies.sortedBy { it.name.lowercase() }
        SortOption.YEAR -> movies.sortedByDescending { it.year }
        SortOption.IMDB -> movies.sortedByDescending { it.imdbRating }
        SortOption.LIKES -> movies.sortedByDescending { it.likes }
        SortOption.COMPLETE -> movies // no "complete" for movies
    }

    fun filterSeriesByGenre(series: List<Series>, genre: String?): List<Series> {
        if (genre.isNullOrBlank()) return series
        return series.filter { s -> s.genres.any { it.contains(genre, ignoreCase = true) } }
    }

    fun searchSeries(series: List<Series>, query: String): List<Series> {
        if (query.isBlank()) return series
        val q = query.lowercase()
        return series.filter { it.name.lowercase().contains(q) }
    }

    fun searchMovies(movies: List<Movie>, query: String): List<Movie> {
        if (query.isBlank()) return movies
        val q = query.lowercase()
        return movies.filter { it.name.lowercase().contains(q) }
    }

    /**
     * Fetch /sort/my/ — returns server-side continue watching + my series list.
     */
    suspend fun getMyPage(forceRefresh: Boolean = false): Result<Pair<List<ContinueWatchingItem>, List<Series>>> {
        if (!forceRefresh) {
            val cw = cachedContinueWatching
            val my = cachedMySeries
            if (cw != null && my != null) return Result.success(cw to my)
        }
        return myPageLock.withLock {
            if (!forceRefresh) {
                val cw = cachedContinueWatching
                val my = cachedMySeries
                if (cw != null && my != null) return@withLock Result.success(cw to my)
            }
            apiClient.fetchPage("/sort/my/").map { html ->
                val cw = CatalogParser.parseContinueWatching(html)
                val series = CatalogParser.parseSeries(html)
                cachedContinueWatching = cw
                cachedMySeries = series
                cw to series
            }
        }
    }

    suspend fun getBookmarkedMovies(forceRefresh: Boolean = false): Result<List<Movie>> {
        if (!forceRefresh) cachedBookmarkedMovies?.let { return Result.success(it) }
        return bookmarksLock.withLock {
            if (!forceRefresh) cachedBookmarkedMovies?.let { return@withLock Result.success(it) }
            apiClient.fetchPage("/movies/bookmarks/").map { html ->
                MovieCatalogParser.parseMovies(html).also { cachedBookmarkedMovies = it }
            }
        }
    }

    fun filterByUhd(series: List<Series>, uhdOnly: Boolean): List<Series> {
        return if (uhdOnly) series.filter { it.isUhd } else series
    }

    fun invalidateCache() {
        cachedSeries = null
        cachedMovies = null
        cachedBookmarkedMovies = null
        cachedContinueWatching = null
        cachedMySeries = null
    }
}
