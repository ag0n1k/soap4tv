package com.soap4tv.app.ui.screen.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soap4tv.app.data.model.MovieDetail
import com.soap4tv.app.data.repository.MovieRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val movieRepository: MovieRepository
) : ViewModel() {

    var detail by mutableStateOf<MovieDetail?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun load(movieId: Int) {
        viewModelScope.launch {
            isLoading = true
            error = null
            movieRepository.getMovieDetail(movieId)
                .onSuccess { detail = it }
                .onFailure { error = it.message }
            isLoading = false
        }
    }

    fun toggleBookmark() {
        val d = detail ?: return
        viewModelScope.launch {
            movieRepository.likeMovie(d.id, !d.isBookmarked)
                .onSuccess {
                    movieRepository.getMovieDetail(d.id, forceRefresh = true)
                        .onSuccess { detail = it }
                }
        }
    }

    fun toggleWatched() {
        val d = detail ?: return
        viewModelScope.launch {
            movieRepository.markWatched(d.id, !d.isWatched)
                .onSuccess {
                    movieRepository.getMovieDetail(d.id, forceRefresh = true)
                        .onSuccess { detail = it }
                }
        }
    }
}
