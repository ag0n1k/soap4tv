package com.soap4tv.app.ui.screen.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soap4tv.app.data.model.SeriesDetail
import com.soap4tv.app.data.repository.SeriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    private val seriesRepository: SeriesRepository
) : ViewModel() {

    var detail by mutableStateOf<SeriesDetail?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var isTogglingWatch by mutableStateOf(false)
        private set

    fun load(slug: String) {
        viewModelScope.launch {
            isLoading = true
            error = null
            seriesRepository.getSeriesDetail(slug)
                .onSuccess { detail = it }
                .onFailure { error = it.message }
            isLoading = false
        }
    }

    fun toggleWatching() {
        val d = detail ?: return
        viewModelScope.launch {
            isTogglingWatch = true
            seriesRepository.toggleWatching(d.id, d.isWatching)
                .onSuccess {
                    // Refresh to get updated state
                    seriesRepository.getSeriesDetail(d.slug, forceRefresh = true)
                        .onSuccess { detail = it }
                }
            isTogglingWatch = false
        }
    }
}
