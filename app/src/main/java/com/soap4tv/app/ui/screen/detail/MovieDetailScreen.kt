package com.soap4tv.app.ui.screen.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.soap4tv.app.ui.components.LoadingIndicator
import com.soap4tv.app.ui.theme.*

@Composable
fun MovieDetailScreen(
    movieId: Int,
    onPlayClick: () -> Unit,
    onBack: () -> Unit,
    viewModel: MovieDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(movieId) { viewModel.load(movieId) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        when {
            viewModel.isLoading -> LoadingIndicator(modifier = Modifier.fillMaxSize())
            viewModel.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = viewModel.error!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnSurface
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            DetailButton(text = "Назад", onClick = onBack)
                            DetailButton(text = "Повторить", onClick = { viewModel.load(movieId) })
                        }
                    }
                }
            }
            viewModel.detail != null -> {
                val detail = viewModel.detail!!
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left: poster (35%)
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.35f)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AsyncImage(
                            model = detail.posterUrl,
                            contentDescription = detail.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(16.dp))

                        // Play button
                        DetailButton(
                            text = "Смотреть",
                            onClick = onPlayClick,
                            modifier = Modifier.fillMaxWidth(),
                            isActive = true
                        )

                        Spacer(Modifier.height(8.dp))

                        DetailButton(
                            text = if (detail.isBookmarked) "В избранном" else "В избранное",
                            onClick = { viewModel.toggleBookmark() },
                            modifier = Modifier.fillMaxWidth(),
                            isActive = detail.isBookmarked
                        )

                        Spacer(Modifier.height(8.dp))

                        DetailButton(
                            text = if (detail.isWatched) "Просмотрено" else "Отметить просмотренным",
                            onClick = { viewModel.toggleWatched() },
                            modifier = Modifier.fillMaxWidth(),
                            isActive = detail.isWatched
                        )
                    }

                    // Right: info (65%)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 24.dp, top = 24.dp, bottom = 24.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = detail.title,
                            style = MaterialTheme.typography.displaySmall,
                            color = OnBackground
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (detail.year > 0) {
                                Box(
                                    modifier = Modifier
                                        .background(SurfaceVariant, RoundedCornerShape(16.dp))
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = detail.year.toString(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = OnSurface
                                    )
                                }
                            }
                            if (detail.duration.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .background(SurfaceVariant, RoundedCornerShape(16.dp))
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = detail.duration,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = OnSurface
                                    )
                                }
                            }
                            if (detail.qualities.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .background(SurfaceVariant, RoundedCornerShape(16.dp))
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = detail.qualities,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = QualityHD
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        if (detail.description.isNotBlank()) {
                            Text(
                                text = detail.description,
                                style = MaterialTheme.typography.bodyLarge,
                                color = OnSurface,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(16.dp))
                        }

                        // Info rows from the map
                        detail.infoRows.forEach { (label, value) ->
                            if (value.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$label: ",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        modifier = Modifier.widthIn(min = 140.dp)
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
                        }

                        // Subtitles
                        if (detail.subtitles.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Субтитры: ${detail.subtitles.joinToString(", ") { it.label }}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
