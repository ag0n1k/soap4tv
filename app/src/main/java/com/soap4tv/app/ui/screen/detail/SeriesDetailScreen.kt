package com.soap4tv.app.ui.screen.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.soap4tv.app.data.model.Season
import com.soap4tv.app.ui.components.LoadingIndicator
import com.soap4tv.app.ui.theme.*

@Composable
fun SeriesDetailScreen(
    slug: String,
    onSeasonClick: (String, Int) -> Unit,
    onBack: () -> Unit,
    viewModel: SeriesDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(slug) { viewModel.load(slug) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        when {
            viewModel.isLoading -> LoadingIndicator(modifier = Modifier.fillMaxSize())
            viewModel.error != null -> ErrorView(
                message = viewModel.error!!,
                onRetry = { viewModel.load(slug) },
                onBack = onBack
            )
            viewModel.detail != null -> {
                val detail = viewModel.detail!!
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left panel: poster + actions (35%)
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.35f)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AsyncImage(
                            model = detail.coverUrl,
                            contentDescription = detail.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(16.dp))

                        // Ratings
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (detail.imdbRating.isNotBlank() && detail.imdbRating != "0") {
                                RatingBadge(label = "IMDb", value = detail.imdbRating)
                            }
                            if (detail.kinopoiskRating.isNotBlank() && detail.kinopoiskRating != "0") {
                                RatingBadge(label = "КП", value = detail.kinopoiskRating)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Watch/Unwatch button
                        DetailButton(
                            text = if (detail.isWatching) "В моём списке" else "Добавить в список",
                            isActive = detail.isWatching,
                            onClick = { viewModel.toggleWatching() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Right panel: info + seasons (65%)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 24.dp, top = 24.dp, bottom = 24.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Back button
                        FocusableIconButton(
                            onClick = onBack,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Title
                        Text(
                            text = detail.title,
                            style = MaterialTheme.typography.displaySmall,
                            color = OnBackground
                        )
                        if (detail.titleRu.isNotBlank() && detail.titleRu != detail.title) {
                            Text(
                                text = detail.titleRu,
                                style = MaterialTheme.typography.titleLarge,
                                color = TextSecondary
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // Status + year
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (detail.status.isNotBlank()) {
                                InfoChip(text = detail.status)
                            }
                            if (detail.year > 0) {
                                InfoChip(text = detail.year.toString())
                            }
                            if (detail.country.isNotBlank()) {
                                InfoChip(text = detail.country)
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Description
                        if (detail.description.isNotBlank()) {
                            Text(
                                text = detail.description,
                                style = MaterialTheme.typography.bodyLarge,
                                color = OnSurface,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(12.dp))
                        }

                        // Info rows
                        if (detail.genres.isNotBlank()) {
                            InfoRow(label = "Жанр", value = detail.genres)
                        }
                        if (detail.network.isNotBlank()) {
                            InfoRow(label = "Канал", value = detail.network)
                        }
                        if (detail.duration.isNotBlank()) {
                            InfoRow(label = "Длительность", value = detail.duration)
                        }
                        if (detail.actors.isNotBlank()) {
                            InfoRow(label = "Актёры", value = detail.actors)
                        }

                        Spacer(Modifier.height(24.dp))

                        // Seasons
                        if (detail.seasons.isNotEmpty()) {
                            Text(
                                text = "Сезоны",
                                style = MaterialTheme.typography.titleLarge,
                                color = OnBackground
                            )
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(detail.seasons) { season ->
                                    SeasonCard(
                                        season = season,
                                        onClick = { onSeasonClick(slug, season.number) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonCard(season: Season, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.07f else 1.0f,
        label = "season_scale"
    )

    Column(
        modifier = Modifier
            .scale(scale)
            .width(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Accent else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .background(Surface)
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (season.coverUrl.isNotBlank()) {
            AsyncImage(
                model = season.coverUrl,
                contentDescription = "Сезон ${season.number}",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceVariant),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = season.number.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextSecondary
                )
            }
        }
        Text(
            text = "Сезон ${season.number}",
            style = MaterialTheme.typography.labelLarge,
            color = if (isFocused) OnBackground else OnSurface,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun RatingBadge(label: String, value: String) {
    Box(
        modifier = Modifier
            .background(SurfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = OnBackground
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Box(
        modifier = Modifier
            .background(SurfaceVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = OnSurface
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.widthIn(min = 120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun DetailButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isActive -> Accent.copy(alpha = 0.2f)
                    isFocused -> SurfaceVariant
                    else -> Surface
                }
            )
            .border(
                width = if (isFocused || isActive) 1.dp else 0.dp,
                color = if (isFocused) Accent else if (isActive) Accent.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (isFocused || isActive) Accent else OnSurface
        )
    }
}

@Composable
private fun FocusableIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    IconButton(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .background(
                if (isFocused) SurfaceVariant else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
    ) {
        Icon(
            imageVector = Icons.Default.ArrowBack,
            contentDescription = "Back",
            tint = if (isFocused) Accent else OnSurface
        )
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurface
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DetailButton(text = "Назад", onClick = onBack)
                DetailButton(text = "Повторить", onClick = onRetry)
            }
        }
    }
}
