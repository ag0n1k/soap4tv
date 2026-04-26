package com.soap4tv.app.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.soap4tv.app.data.local.entity.WatchProgressEntity
import com.soap4tv.app.ui.screen.catalog.CatalogViewModel
import com.soap4tv.app.ui.screen.catalog.MovieCatalogScreen
import com.soap4tv.app.ui.screen.catalog.SeriesCatalogScreen
import com.soap4tv.app.ui.theme.*

@Composable
fun HomeScreen(
    onSeriesClick: (String) -> Unit,
    onMovieClick: (Int) -> Unit,
    onSearchClick: () -> Unit,
    onEpisodesClick: (slug: String, season: Int) -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel(),
    catalogViewModel: CatalogViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Сериалы", "Фильмы", "Моё")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Top bar with tabs and search
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tab row
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.weight(1f),
                containerColor = Color.Transparent,
                contentColor = Accent,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Accent
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selectedTab == index) Accent else TextSecondary
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // Search button
            SearchIconButton(onClick = onSearchClick)
        }

        // Tab content
        when (selectedTab) {
            0 -> SeriesCatalogScreen(
                viewModel = catalogViewModel,
                onSeriesClick = onSeriesClick
            )
            1 -> MovieCatalogScreen(
                viewModel = catalogViewModel,
                onMovieClick = onMovieClick
            )
            2 -> MyContentScreen(
                homeViewModel = homeViewModel,
                catalogViewModel = catalogViewModel,
                onSeriesClick = onSeriesClick,
                onMovieClick = onMovieClick,
                onEpisodesClick = onEpisodesClick
            )
        }
    }
}

@Composable
private fun SearchIconButton(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .background(
                if (isFocused) SurfaceVariant else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = if (isFocused) Accent else OnSurface,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
internal fun ContinueWatchingRow(
    items: List<WatchProgressEntity>,
    onItemClick: (WatchProgressEntity) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = "Продолжить просмотр",
            style = MaterialTheme.typography.titleMedium,
            color = OnBackground,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item ->
                ContinueWatchingCard(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
internal fun ContinueWatchingCard(
    item: WatchProgressEntity,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    // Extract episode badge from title (e.g. "s01e03 | Destroyer..." → "s01e03")
    val episodeBadge = if (item.seasonNumber > 0) {
        "s%02de%02d".format(item.seasonNumber, item.episodeNumber)
    } else {
        val match = Regex("""^(s\d+e\d+)""", RegexOption.IGNORE_CASE).find(item.title)
        match?.groupValues?.get(1)
    }

    // Series name: strip "s01e03 | " prefix from whatever we have
    val raw = item.seriesTitle.ifBlank { item.title }
    val displayTitle = raw.replace(Regex("""^s\d+e\d+\s*[|:.\-–]\s*""", RegexOption.IGNORE_CASE), "").ifBlank { raw }

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
                model = item.coverUrl,
                contentDescription = displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Episode badge overlay
            if (episodeBadge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = episodeBadge,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
            // Progress bar
            if (item.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { item.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(3.dp),
                    color = Accent,
                    trackColor = Color.Transparent
                )
            }
        }
        Text(
            text = displayTitle,
            style = MaterialTheme.typography.labelLarge,
            color = if (isFocused) OnBackground else OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
    }
}
