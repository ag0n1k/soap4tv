package com.soap4tv.app.ui.screen.catalog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soap4tv.app.data.model.ContinueWatchingItem
import com.soap4tv.app.data.model.Movie
import com.soap4tv.app.data.model.Series
import com.soap4tv.app.data.repository.AuthRepository
import com.soap4tv.app.data.repository.CatalogRepository
import com.soap4tv.app.data.repository.SortOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val authRepository: AuthRepository
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
    var isLoadingMyPage by mutableStateOf(true)
        private set

    // Tabs fetch on first open — see [onTabSelected].
    private var moviesRequested = false
    private var myPageRequested = false

    val bookmarkedMovies: StateFlow<List<Movie>> = _bookmarkedMovies
    val continueWatchingServer: StateFlow<List<ContinueWatchingItem>> = _continueWatchingServer
    val mySeries: StateFlow<List<Series>> = _mySeries

    // debounce() delays every emission, including the initial empty query, which would hold
    // the first catalog render back by 300 ms after the data is already parsed. Only typing
    // needs the delay.
    private val debouncedQuery = _searchQuery.debounce { query -> if (query.isEmpty()) 0L else 300L }

    // --- Filtered series ---
    // Sorting/filtering the full catalog is CPU work; stateIn(viewModelScope) alone would run
    // the combine block on the main dispatcher.
    val filteredSeries: StateFlow<List<Series>> = combine(
        _allSeries, _seriesSortOption, _seriesGenreFilter,
        _uhdFilter, debouncedQuery
    ) { series, sort, genre, uhdOnly, query ->
        var result = series
        result = catalogRepository.filterSeriesByGenre(result, genre)
        result = catalogRepository.filterByUhd(result, uhdOnly)
        result = catalogRepository.searchSeries(result, query)
        catalogRepository.sortSeries(result, sort)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Filtered movies ---
    val filteredMovies: StateFlow<List<Movie>> = combine(
        _allMovies, _moviesSortOption, debouncedQuery
    ) { movies, sort, query ->
        var result = movies
        result = catalogRepository.searchMovies(result, query)
        catalogRepository.sortMovies(result, sort)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchQuery: StateFlow<String> = _searchQuery
    val seriesSortOption: StateFlow<SortOption> = _seriesSortOption
    val moviesSortOption: StateFlow<SortOption> = _moviesSortOption
    val uhdFilter: StateFlow<Boolean> = _uhdFilter
    val seriesGenreFilter: StateFlow<String?> = _seriesGenreFilter

    init {
        loadSeries()
        observeSessionRenewal()
    }

    /**
     * Load a tab's data the first time it is opened. Firing all four startup requests at once
     * only slowed down the one grid the user is actually looking at — they share a host, a
     * connection pool and the device's CPU for parsing.
     */
    fun onTabSelected(index: Int) {
        when (index) {
            1 -> if (!moviesRequested) {
                moviesRequested = true
                loadMovies()
            }
            2 -> if (!myPageRequested) {
                myPageRequested = true
                loadMyPage()
                loadBookmarkedMovies()
            }
        }
    }

    /**
     * The app starts optimistically on a stored cookie, so a catalog request can race a
     * session that turned out to be dead and come back as the login page — parsed into an
     * empty, error-free list. When the session is renewed underneath us, drop the caches
     * and refetch instead of leaving the user on a blank grid.
     */
    private fun observeSessionRenewal() {
        viewModelScope.launch {
            authRepository.sessionGeneration.drop(1).collect {
                catalogRepository.invalidateCache()
                loadSeries(forceRefresh = true)
                if (moviesRequested) loadMovies(forceRefresh = true)
                if (myPageRequested) {
                    loadMyPage(forceRefresh = true)
                    loadBookmarkedMovies(forceRefresh = true)
                }
            }
        }
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
            isLoadingMyPage = true
            catalogRepository.getMyPage(forceRefresh)
                .onSuccess { (cw, series) ->
                    _continueWatchingServer.value = cw
                    _mySeries.value = series
                }
            isLoadingMyPage = false
        }
    }

    fun setGenreFilter(genre: String?) { _seriesGenreFilter.value = genre }
    fun setUhdFilter(enabled: Boolean) { _uhdFilter.value = enabled }
    fun setSearchQuery(query: String) { _searchQuery.value = query }
}
