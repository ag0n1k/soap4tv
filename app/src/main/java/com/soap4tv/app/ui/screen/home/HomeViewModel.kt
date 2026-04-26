package com.soap4tv.app.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soap4tv.app.data.local.entity.WatchProgressEntity
import com.soap4tv.app.data.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    watchProgressRepository: WatchProgressRepository
) : ViewModel() {

    // Deduplicate series: keep only the most recent episode per series (by lastWatched).
    // Movies pass through untouched — each movie is unique by contentId anyway.
    val continueWatching: StateFlow<List<WatchProgressEntity>> =
        watchProgressRepository.getContinueWatching()
            .map { list ->
                val seenSeries = HashSet<String>()
                list.filter { item ->
                    if (item.contentType != "series") return@filter true
                    seenSeries.add(item.contentId)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )
}
