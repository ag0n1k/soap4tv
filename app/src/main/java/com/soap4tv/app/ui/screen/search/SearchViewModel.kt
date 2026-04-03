package com.soap4tv.app.ui.screen.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soap4tv.app.data.model.Movie
import com.soap4tv.app.data.model.Series
import com.soap4tv.app.data.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository
) : ViewModel() {

    var query by mutableStateOf("")
        private set
    var seriesResults by mutableStateOf<List<Series>>(emptyList())
        private set
    var movieResults by mutableStateOf<List<Movie>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set

    private val _queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            _queryFlow
                .debounce(300)
                .distinctUntilChanged()
                .filter { it.length >= 2 }
                .collect { q -> performSearch(q) }
        }
    }

    fun onQueryChange(q: String) {
        query = q
        _queryFlow.value = q
        if (q.length < 2) {
            seriesResults = emptyList()
            movieResults = emptyList()
        }
    }

    private suspend fun performSearch(q: String) {
        isLoading = true
        // Client-side search over cached catalog data
        val allSeries = catalogRepository.getSeries().getOrDefault(emptyList())
        val allMovies = catalogRepository.getMovies().getOrDefault(emptyList())

        seriesResults = catalogRepository.searchSeries(allSeries, q)
        movieResults = catalogRepository.searchMovies(allMovies, q)
        isLoading = false
    }
}
