package com.soap4tv.app.ui.screen.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soap4tv.app.ui.components.LoadingIndicator
import com.soap4tv.app.ui.components.PosterCard
import com.soap4tv.app.ui.components.SortTabRow
import com.soap4tv.app.ui.theme.OnSurface

@Composable
fun MovieCatalogScreen(
    viewModel: CatalogViewModel,
    onMovieClick: (Int) -> Unit
) {
    val movies by viewModel.filteredMovies.collectAsStateWithLifecycle()
    val sortOption by viewModel.moviesSortOption.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        SortTabRow(
            selected = sortOption,
            onSelect = viewModel::setMoviesSort
        )

        when {
            viewModel.isLoadingMovies -> {
                LoadingIndicator(modifier = Modifier.fillMaxSize())
            }
            viewModel.moviesError != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = viewModel.moviesError ?: "Ошибка загрузки",
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnSurface
                        )
                        Spacer(Modifier.height(16.dp))
                        RetryButton(onClick = { viewModel.loadMovies(forceRefresh = true) })
                    }
                }
            }
            movies.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Фильмы не найдены",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurface
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(movies, key = { it.id }) { m ->
                        PosterCard(
                            name = m.name,
                            coverUrl = m.coverUrl,
                            year = m.year,
                            imdbRating = m.imdbRating,
                            onClick = { onMovieClick(m.id) }
                        )
                    }
                }
            }
        }
    }
}
