package com.soap4tv.app.ui.screen.catalog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soap4tv.app.data.model.ContinueWatchingItem
import com.soap4tv.app.data.model.Movie
import com.soap4tv.app.data.model.Series
import com.soap4tv.app.data.repository.CatalogRepository
import com.soap4tv.app.data.repository.SortOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository
) : ViewModel() {

    // --- Series state ---
    private val _allSeries = MutableStateFlow<List<Series>>(emptyList())
    private val _seriesSortOption = MutableStateFlow(SortOption.TITLE)
    private val _seriesGenreFilter = MutableStateFlow<String?>(null)
    private val _uhdFilter = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")

    var isLoadingSeries by mutableStateOf(true)
        private set
    var seriesError by mutableStateOf<String?>(null)
        private set

    // --- Movies state ---
    private val _allMovies = MutableStateFlow<List<Movie>>(emptyList())
    private val _moviesSortOption = MutableStateFlow(SortOption.TITLE)
    private val _bookmarkedMovies = MutableStateFlow<List<Movie>>(emptyList())
    private val _continueWatchingServer = MutableStateFlow<List<ContinueWatchingItem>>(emptyList())
    private val _mySeries = MutableStateFlow<List<Series>>(emptyList())

    var isLoadingMovies by mutableStateOf(true)
        private set
    var moviesError by mutableStateOf<String?>(null)
        private set

    val bookmarkedMovies: StateFlow<List<Movie>> = _bookmarkedMovies
    val continueWatchingServer: StateFlow<List<ContinueWatchingItem>> = _continueWatchingServer
    val mySeries: StateFlow<List<Series>> = _mySeries

    // --- Filtered series ---
    val filteredSeries: StateFlow<List<Series>> = combine(
        _allSeries, _seriesSortOption, _seriesGenreFilter,
        _uhdFilter, _searchQuery.debounce(300)
    ) { series, sort, genre, uhdOnly, query ->
        var result = series
        result = catalogRepository.filterSeriesByGenre(result, genre)
        result = catalogRepository.filterByUhd(result, uhdOnly)
        result = catalogRepository.searchSeries(result, query)
        catalogRepository.sortSeries(result, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Filtered movies ---
    val filteredMovies: StateFlow<List<Movie>> = combine(
        _allMovies, _moviesSortOption, _searchQuery.debounce(300)
    ) { movies, sort, query ->
        var result = movies
        result = catalogRepository.searchMovies(result, query)
        catalogRepository.sortMovies(result, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchQuery: StateFlow<String> = _searchQuery
    val seriesSortOption: StateFlow<SortOption> = _seriesSortOption
    val moviesSortOption: StateFlow<SortOption> = _moviesSortOption
    val uhdFilter: StateFlow<Boolean> = _uhdFilter
    val seriesGenreFilter: StateFlow<String?> = _seriesGenreFilter

    init {
        loadSeries()
        loadMovies()
        loadBookmarkedMovies()
        loadMyPage()
    }

    fun loadSeries(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            isLoadingSeries = true
            seriesError = null
            catalogRepository.getSeries(forceRefresh)
                .onSuccess { _allSeries.value = it }
                .onFailure { seriesError = it.message }
            isLoadingSeries = false
        }
    }

    fun loadMovies(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            isLoadingMovies = true
            moviesError = null
            catalogRepository.getMovies(forceRefresh)
                .onSuccess { _allMovies.value = it }
                .onFailure { moviesError = it.message }
            isLoadingMovies = false
        }
    }

    fun setSeriesSort(option: SortOption) { _seriesSortOption.value = option }
    fun setMoviesSort(option: SortOption) { _moviesSortOption.value = option }
    fun loadBookmarkedMovies(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            catalogRepository.getBookmarkedMovies(forceRefresh)
                .onSuccess { _bookmarkedMovies.value = it }
        }
    }

    fun loadMyPage(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            catalogRepository.getMyPage(forceRefresh)
                .onSuccess { (cw, series) ->
                    _continueWatchingServer.value = cw
                    _mySeries.value = series
                }
        }
    }

    fun setGenreFilter(genre: String?) { _seriesGenreFilter.value = genre }
    fun setUhdFilter(enabled: Boolean) { _uhdFilter.value = enabled }
    fun setSearchQuery(query: String) { _searchQuery.value = query }
}
