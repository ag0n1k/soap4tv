package com.soap4tv.app.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.soap4tv.app.data.model.ContinueWatchingItem
import com.soap4tv.app.ui.components.PosterCard
import com.soap4tv.app.ui.screen.catalog.CatalogViewModel
import com.soap4tv.app.ui.theme.*

@Composable
fun MyContentScreen(
    homeViewModel: HomeViewModel,
    catalogViewModel: CatalogViewModel,
    onSeriesClick: (String) -> Unit,
    onMovieClick: (Int) -> Unit,
    onEpisodesClick: (slug: String, season: Int) -> Unit
) {
    val localHistory by homeViewModel.continueWatching.collectAsStateWithLifecycle()
    val continueWatching by catalogViewModel.continueWatchingServer.collectAsStateWithLifecycle()
    val mySeries by catalogViewModel.mySeries.collectAsStateWithLifecycle()
    val bookmarkedMovies by catalogViewModel.bookmarkedMovies.collectAsStateWithLifecycle()

    val hasContent = localHistory.isNotEmpty() || continueWatching.isNotEmpty() ||
        mySeries.isNotEmpty() || bookmarkedMovies.isNotEmpty()

    if (!hasContent) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Здесь пока пусто. Начните смотреть!",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Local watch history (from Room — recent on this device)
        if (localHistory.isNotEmpty()) {
            item { SectionHeader("Недавно на этом устройстве") }
            item {
                ContinueWatchingRow(
                    items = localHistory,
                    onItemClick = { item ->
                        if (item.contentType == "series") {
                            // Jump straight into the episode list for the last-watched
                            // season. Cheap — we already have slug + seasonNumber in the
                            // watch progress row; no extra network call needed.
                            onEpisodesClick(item.contentId, item.seasonNumber)
                        } else onMovieClick(item.contentId.toIntOrNull() ?: 0)
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        // Continue Watching (server-side with screenshots)
        if (continueWatching.isNotEmpty()) {
            item {
                SectionHeader("Продолжить просмотр")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(continueWatching) { item ->
                        ContinueWatchingServerCard(
                            item = item,
                            onClick = { onEpisodesClick(item.slug, item.season) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // My Series (from /sort/my/ server list)
        if (mySeries.isNotEmpty()) {
            item { SectionHeader("Мои сериалы") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(mySeries, key = { it.id }) { s ->
                        PosterCard(
                            name = s.name,
                            coverUrl = s.coverUrl,
                            year = s.year,
                            imdbRating = s.imdbRating,
                            isUhd = s.isUhd,
                            hasNewEpisodes = s.hasNewEpisodes,
                            onClick = { onSeriesClick(s.slug) },
                            modifier = Modifier.width(140.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // My Movies (bookmarked)
        if (bookmarkedMovies.isNotEmpty()) {
            item { SectionHeader("Мои фильмы") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(bookmarkedMovies, key = { it.id }) { m ->
                        PosterCard(
                            name = m.name,
                            coverUrl = m.coverUrl,
                            year = m.year,
                            imdbRating = m.imdbRating,
                            onClick = { onMovieClick(m.id) },
                            modifier = Modifier.width(140.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingServerCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) SurfaceVariant else Surface)
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceVariant)
        ) {
            AsyncImage(
                model = item.screenshotUrl,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Episode badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = item.episode,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelLarge,
            color = if (isFocused) OnBackground else OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = OnBackground,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}
