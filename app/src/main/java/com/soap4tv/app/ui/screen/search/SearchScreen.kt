package com.soap4tv.app.ui.screen.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.soap4tv.app.ui.components.LoadingIndicator
import com.soap4tv.app.ui.components.PosterCard
import com.soap4tv.app.ui.components.SearchBar
import com.soap4tv.app.ui.theme.*

@Composable
fun SearchScreen(
    onSeriesClick: (String) -> Unit,
    onMovieClick: (Int) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        searchFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            var backFocused by remember { mutableStateOf(false) }
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .onFocusChanged { backFocused = it.isFocused }
                    .background(
                        if (backFocused) SurfaceVariant else Color.Transparent,
                        androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = if (backFocused) Accent else OnSurface
                )
            }

            SearchBar(
                query = viewModel.query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = viewModel::onQueryChange,
                modifier = Modifier.weight(1f),
                focusRequester = searchFocusRequester
            )
        }

        // Results
        when {
            viewModel.isLoading -> LoadingIndicator(modifier = Modifier.weight(1f))
            viewModel.query.length >= 2 &&
                viewModel.seriesResults.isEmpty() &&
                viewModel.movieResults.isEmpty() &&
                !viewModel.isLoading -> {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Ничего не найдено",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            }
            viewModel.query.length < 2 -> {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Введите не менее 2 символов для поиска",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            }
            else -> {
                val allItems = buildList {
                    addAll(viewModel.seriesResults.map { SearchItem.SeriesItem(it) })
                    addAll(viewModel.movieResults.map { SearchItem.MovieItem(it) })
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allItems) { item ->
                        when (item) {
                            is SearchItem.SeriesItem -> PosterCard(
                                name = item.series.name,
                                coverUrl = item.series.coverUrl,
                                year = item.series.year,
                                imdbRating = item.series.imdbRating,
                                isUhd = item.series.isUhd,
                                hasNewEpisodes = item.series.hasNewEpisodes,
                                onClick = { onSeriesClick(item.series.slug) }
                            )
                            is SearchItem.MovieItem -> PosterCard(
                                name = item.movie.name,
                                coverUrl = item.movie.coverUrl,
                                year = item.movie.year,
                                imdbRating = item.movie.imdbRating,
                                onClick = { onMovieClick(item.movie.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed interface SearchItem {
    data class SeriesItem(val series: com.soap4tv.app.data.model.Series) : SearchItem
    data class MovieItem(val movie: com.soap4tv.app.data.model.Movie) : SearchItem
}
