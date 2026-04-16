package com.soap4tv.app.ui.screen.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soap4tv.app.data.model.Episode
import com.soap4tv.app.data.model.PlaybackData
import com.soap4tv.app.data.repository.CatalogRepository
import com.soap4tv.app.data.repository.MovieRepository
import com.soap4tv.app.data.repository.PlayerRepository
import com.soap4tv.app.data.repository.SeriesRepository
import com.soap4tv.app.data.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val movieRepository: MovieRepository,
    private val catalogRepository: CatalogRepository,
    private val seriesRepository: SeriesRepository,
    private val watchProgressRepository: WatchProgressRepository,
    val okHttpClient: okhttp3.OkHttpClient,
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
                        startFrom = 0L
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

    fun playNextEpisode() {
        val idx = episodeList.indexOfFirst { it.eid == currentEpisodeEid }
        if (idx < 0 || idx >= episodeList.size - 1) return
        val next = episodeList[idx + 1]
        val eid = next.eid ?: return
        val sid = next.sid ?: return
        val hash = next.hash ?: return
        currentEpisodeEid = eid
        updateHasNext()
        loadSeriesEpisode(eid, sid, hash)
    }

    fun saveProgress(
        positionMs: Long,
        durationMs: Long,
        coverUrl: String = ""
    ) {
        val data = playbackData ?: return
        viewModelScope.launch {
            watchProgressRepository.saveProgress(
                episodeId = episodeId,
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
        }
    }
}
