package com.soap4tv.app.ui.screen.episodes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soap4tv.app.data.model.Episode
import com.soap4tv.app.data.repository.SeriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EpisodesViewModel @Inject constructor(
    private val seriesRepository: SeriesRepository
) : ViewModel() {

    private var allEpisodes: List<Episode> = emptyList()

    var episodes by mutableStateOf<List<Episode>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var qualityFilter by mutableStateOf<Int?>(null)
        private set
    var translateFilter by mutableStateOf<String?>(null)
        private set

    // Unique quality/translate values for filter chips
    var availableQualities by mutableStateOf<List<Pair<Int, String>>>(emptyList())
        private set
    var availableTranslates by mutableStateOf<List<String>>(emptyList())
        private set

    fun load(slug: String, season: Int) {
        viewModelScope.launch {
            isLoading = true
            error = null
            seriesRepository.getEpisodes(slug, season)
                .onSuccess { list ->
                    allEpisodes = list
                    availableQualities = list.mapNotNull { ep ->
                        if (ep.quality > 0 && ep.qualityLabel.isNotBlank())
                            ep.quality to ep.qualityLabel
                        else null
                    }.distinct().sortedByDescending { it.first }
                    availableTranslates = list.map { it.translateLabel }
                        .filter { it.isNotBlank() }.distinct()
                    applyFilters()
                }
                .onFailure { error = it.message }
            isLoading = false
        }
    }

    fun onQualityFilterChange(quality: Int?) {
        qualityFilter = quality
        applyFilters()
    }

    fun onTranslateFilterChange(translate: String?) {
        translateFilter = translate
        applyFilters()
    }

    private fun applyFilters() {
        var result = allEpisodes
        qualityFilter?.let { q -> result = result.filter { it.quality == q } }
        translateFilter?.let { t -> result = result.filter { it.translateLabel == t } }
        episodes = result
    }
}
