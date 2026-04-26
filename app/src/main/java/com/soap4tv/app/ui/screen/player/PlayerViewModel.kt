package com.soap4tv.app.ui.screen.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soap4tv.app.data.model.Episode
import com.soap4tv.app.data.model.PlaybackData
import com.soap4tv.app.data.network.PlayerHttp
import com.soap4tv.app.data.repository.CatalogRepository
import com.soap4tv.app.data.repository.MovieRepository
import com.soap4tv.app.data.repository.PlayerRepository
import com.soap4tv.app.data.repository.SeriesRepository
import com.soap4tv.app.data.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val movieRepository: MovieRepository,
    private val catalogRepository: CatalogRepository,
    private val seriesRepository: SeriesRepository,
    private val watchProgressRepository: WatchProgressRepository,
    @PlayerHttp val okHttpClient: okhttp3.OkHttpClient,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    var playbackData by mutableStateOf<PlaybackData?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    // Current content metadata for saving progress
    private var episodeId: Int = 0
    private var contentType: String = ""
    private var contentId: String = ""
    private var seasonNumber: Int = 0
    private var episodeNumber: Int = 0
    private var seriesTitle: String = ""
    private var currentSlug: String? = null
    // Original play-API params for the current series episode — kept so we can
    // refetch a fresh stream URL mid-playback when the CDN session goes stale
    // (the user's "просмотр обновился" pattern: pre-buffered URL stops responding,
    // exiting + re-entering returns a working URL).
    private var currentEid: String = ""
    private var currentSid: String = ""
    private var currentHash: String = ""
    // Cool-down so a player stuck on a bad refresh doesn't trigger a tight refresh
    // loop. Reset on every loadSeriesEpisode (new episode = fresh budget).
    private var lastRefreshAtMs: Long = 0L

    // Refresh stream event channel: emitting (newStreamUrl, startSec) lets PlayerScreen
    // call setMediaItem on the EXISTING ExoPlayer instead of rebuilding it. Rebuilding
    // would tear down the PlayerView's surface attachment, leaving audio playing while
    // video frames render to nowhere.
    private val _refreshStreamEvents = MutableSharedFlow<Pair<String, Long>>(extraBufferCapacity = 1)
    val refreshStreamEvents: SharedFlow<Pair<String, Long>> = _refreshStreamEvents.asSharedFlow()
    // Set to true once we've pushed mark_watched to the server for the current episode,
    // so we don't hammer /callback/ every save tick after the 90% threshold.
    private var markedWatched: Boolean = false

    // Episode list for autoplay
    private var episodeList: List<Episode> = emptyList()
    private var currentEpisodeEid: String = ""
    var hasNextEpisode by mutableStateOf(false)
        private set

    init {
        // Auto-load from nav args via SavedStateHandle
        val eid = savedStateHandle.get<String>("eid")
        val sid = savedStateHandle.get<String>("sid")
        val hash = savedStateHandle.get<String>("hash")
        val slug = savedStateHandle.get<String>("slug")
        val season = savedStateHandle.get<Int>("season")
        val movieId = savedStateHandle.get<Int>("id")

        when {
            eid != null && sid != null && hash != null -> {
                currentSlug = slug
                seasonNumber = season ?: 0
                loadSeriesEpisode(eid, sid, hash)
                if (slug != null && season != null) {
                    loadEpisodeList(slug, season, eid)
                }
            }
            movieId != null -> loadMovie(movieId)
            else -> { isLoading = false; error = "No playback source" }
        }
    }

    /**
     * Load playback data for a series episode.
     */
    fun loadSeriesEpisode(eid: String, sid: String, hash: String) {
        episodeId = eid.toIntOrNull() ?: 0
        contentType = "series"
        contentId = sid // fallback, will be replaced by slug below
        currentEid = eid
        currentSid = sid
        currentHash = hash
        lastRefreshAtMs = 0L
        // Look up series slug + name from cached catalog by series ID
        viewModelScope.launch {
            val sidInt = sid.toIntOrNull()
            if (sidInt != null) {
                catalogRepository.getSeries().getOrNull()
                    ?.find { it.id == sidInt }
                    ?.let {
                        seriesTitle = it.name
                        contentId = it.slug // use slug for navigation from history
                    }
            }
        }
        viewModelScope.launch {
            isLoading = true
            error = null
            playerRepository.getSeriesPlaybackData(eid, sid, hash)
                .onSuccess { playbackData = it }
                .onFailure { error = it.message }
            isLoading = false
        }
    }

    /**
     * Load playback data for a movie from its detail page.
     */
    fun loadMovie(movieId: Int) {
        episodeId = -movieId // negative IDs for movies
        contentType = "movie"
        contentId = movieId.toString()
        seasonNumber = 0
        episodeNumber = 0
        viewModelScope.launch {
            isLoading = true
            error = null
            movieRepository.getMovieDetail(movieId)
                .onSuccess { detail ->
                    playbackData = PlaybackData(
                        streamUrl = detail.hlsUrl,
                        subtitles = detail.subtitles,
                        posterUrl = detail.posterUrl,
                        title = detail.title,
                        startFrom = 0L,
                        episodeId = -movieId
                    )
                }
                .onFailure { error = it.message }
            isLoading = false
        }
    }

    private fun loadEpisodeList(slug: String, season: Int, currentEid: String) {
        currentEpisodeEid = currentEid
        viewModelScope.launch {
            seriesRepository.getEpisodes(slug, season)
                .onSuccess { list ->
                    episodeList = list.filter { it.canPlay }
                    updateHasNext()
                }
        }
    }

    private fun updateHasNext() {
        val idx = episodeList.indexOfFirst { it.eid == currentEpisodeEid }
        hasNextEpisode = idx >= 0 && idx < episodeList.size - 1
    }

    /**
     * Re-fetch the stream URL for the current series episode and resume from the given
     * position. Used by the player's stall watchdog when the CDN session goes stale —
     * exiting and re-entering the episode also fixes it, but this avoids the manual step.
     * No-op for movies (movies have a single fetch on entry; if this becomes an issue
     * for movies, mirror the pattern by storing the movie id and calling getMovieDetail).
     */
    fun refreshStream(currentPositionMs: Long) {
        val eid = currentEid
        val sid = currentSid
        val hash = currentHash
        if (eid.isEmpty() || sid.isEmpty() || hash.isEmpty()) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastRefreshAtMs < 30_000L) return
        lastRefreshAtMs = now
        val resumeSec = (currentPositionMs / 1000).coerceAtLeast(0)
        viewModelScope.launch {
            // Emit a refresh event so the player screen can call setMediaItem on the
            // EXISTING player instead of rebuilding it (which would detach PlayerView's
            // surface and leave video frames going nowhere — audio + subs would still
            // work because they don't need that surface).
            playerRepository.getSeriesPlaybackData(eid, sid, hash)
                .onSuccess { fresh ->
                    _refreshStreamEvents.tryEmit(fresh.streamUrl to resumeSec)
                }
                .onFailure { error = it.message }
        }
    }

    fun playNextEpisode() {
        val idx = episodeList.indexOfFirst { it.eid == currentEpisodeEid }
        if (idx < 0 || idx >= episodeList.size - 1) return
        val next = episodeList[idx + 1]
        val eid = next.eid ?: return
        val sid = next.sid ?: return
        val hash = next.hash ?: return
        currentEpisodeEid = eid
        markedWatched = false
        updateHasNext()
        loadSeriesEpisode(eid, sid, hash)
    }

    fun saveProgress(
        positionMs: Long,
        durationMs: Long,
        coverUrl: String = "",
        snapshot: PlaybackData? = null
    ) {
        // Use the explicit snapshot when provided (dispose-time saves of an OLD player
        // after autoplay has already mutated ViewModel state for the next episode);
        // otherwise fall back to the current playbackData.
        val data = snapshot ?: playbackData ?: return
        val snapEpisodeId = data.episodeId.takeIf { it != 0 } ?: episodeId
        // Cache the slug locally so the coroutine can't read a value that has been
        // changed by the autoplay path between launch and execution.
        val snapSlug = currentSlug
        viewModelScope.launch {
            watchProgressRepository.saveProgress(
                episodeId = snapEpisodeId,
                contentType = contentType,
                contentId = contentId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                title = data.title,
                seriesTitle = seriesTitle.ifBlank { data.title },
                coverUrl = coverUrl.ifBlank { data.posterUrl },
                positionMs = positionMs,
                durationMs = durationMs
            )

            // Once the episode crosses 90% (matching the site's own watched threshold),
            // tell the server and drop our cached episode list so the watched badge
            // shows up when the user returns to the episode list.
            if (contentType == "series" && !markedWatched &&
                durationMs > 0 && positionMs.toDouble() / durationMs > 0.9 && snapEpisodeId > 0
            ) {
                // Only commit the markedWatched flag if the snapshot still matches the
                // current episode — otherwise we'd block the NEXT episode (which now
                // owns episodeId) from ever being marked when its own threshold hits.
                if (snapEpisodeId == episodeId) {
                    markedWatched = true
                }
                seriesRepository.markEpisodeWatched(snapEpisodeId, watched = true)
                snapSlug?.let { seriesRepository.invalidateCache(slug = it) }
            }
        }
    }
}
