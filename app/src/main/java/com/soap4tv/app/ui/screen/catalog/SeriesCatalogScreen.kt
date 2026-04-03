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
import com.soap4tv.app.ui.components.FilterChipRow
import com.soap4tv.app.ui.components.FilterOption
import com.soap4tv.app.ui.components.LoadingIndicator
import com.soap4tv.app.ui.components.PosterCard
import com.soap4tv.app.ui.components.SortTabRow
import com.soap4tv.app.ui.theme.OnSurface

@Composable
fun SeriesCatalogScreen(
    viewModel: CatalogViewModel,
    onSeriesClick: (String) -> Unit
) {
    val series by viewModel.filteredSeries.collectAsStateWithLifecycle()
    val sortOption by viewModel.seriesSortOption.collectAsStateWithLifecycle()
    val uhdOn by viewModel.uhdFilter.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        SortTabRow(
            selected = sortOption,
            onSelect = viewModel::setSeriesSort
        )
        FilterChipRow(
            options = listOf(FilterOption("4k", "4K / UHD")),
            selected = if (uhdOn) "4k" else null,
            onSelect = { viewModel.setUhdFilter(it != null) }
        )

        when {
            viewModel.isLoadingSeries -> {
                LoadingIndicator(modifier = Modifier.fillMaxSize())
            }
            viewModel.seriesError != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = viewModel.seriesError ?: "Ошибка загрузки",
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnSurface
                        )
                        Spacer(Modifier.height(16.dp))
                        RetryButton(onClick = { viewModel.loadSeries(forceRefresh = true) })
                    }
                }
            }
            series.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Сериалы не найдены",
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
                    items(series, key = { it.id }) { s ->
                        PosterCard(
                            name = s.name,
                            coverUrl = s.coverUrl,
                            year = s.year,
                            imdbRating = s.imdbRating,
                            isUhd = s.isUhd,
                            hasNewEpisodes = s.hasNewEpisodes,
                            onClick = { onSeriesClick(s.slug) }
                        )
                    }
                }
            }
        }
    }
}
