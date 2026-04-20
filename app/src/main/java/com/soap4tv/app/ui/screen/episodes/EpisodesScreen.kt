package com.soap4tv.app.ui.screen.episodes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.soap4tv.app.ui.components.EpisodeRow
import com.soap4tv.app.ui.components.FilterChipRow
import com.soap4tv.app.ui.components.FilterOption
import com.soap4tv.app.ui.components.LoadingIndicator
import com.soap4tv.app.ui.screen.detail.DetailButton
import com.soap4tv.app.ui.theme.*

@Composable
fun EpisodesScreen(
    slug: String,
    season: Int,
    onEpisodeClick: (eid: String, sid: String, hash: String) -> Unit,
    onBack: () -> Unit,
    viewModel: EpisodesViewModel = hiltViewModel()
) {
    LaunchedEffect(slug, season) { viewModel.load(slug, season) }

    // Force-refresh the episode list when the user returns to this screen (e.g. from
    // the player). The site may now flag the just-watched episode as watched server-side,
    // and our local DB knows about any >90% completions.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, slug, season) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.load(slug, season, forceRefresh = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
            var isFocused by remember { mutableStateOf(false) }
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .onFocusChanged { isFocused = it.isFocused }
                    .background(
                        if (isFocused) SurfaceVariant else Color.Transparent,
                        androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = if (isFocused) Accent else OnSurface
                )
            }
            Text(
                text = "Сезон $season",
                style = MaterialTheme.typography.titleLarge,
                color = OnBackground
            )
        }

        // Quality filter chips
        if (viewModel.availableQualities.size > 1) {
            FilterChipRow(
                options = viewModel.availableQualities.map { (q, label) ->
                    FilterOption(q.toString(), label)
                },
                selected = viewModel.qualityFilter?.toString(),
                onSelect = { key -> viewModel.onQualityFilterChange(key?.toIntOrNull()) }
            )
        }
        // Translate filter chips
        if (viewModel.availableTranslates.size > 1) {
            FilterChipRow(
                options = viewModel.availableTranslates.map { FilterOption(it, it) },
                selected = viewModel.translateFilter,
                onSelect = { viewModel.onTranslateFilterChange(it) }
            )
        }

        when {
            viewModel.isLoading -> LoadingIndicator(modifier = Modifier.weight(1f))
            viewModel.error != null -> {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = viewModel.error!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnSurface
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            DetailButton(text = "Назад", onClick = onBack)
                            DetailButton(
                                text = "Повторить",
                                onClick = { viewModel.load(slug, season) }
                            )
                        }
                    }
                }
            }
            viewModel.episodes.isEmpty() -> {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Эпизоды не найдены",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(viewModel.episodes, key = { it.id }) { episode ->
                        EpisodeRow(
                            episode = episode,
                            isLocallyWatched = viewModel.locallyWatchedIds.contains(episode.id),
                            onClick = {
                                if (episode.canPlay && episode.eid != null &&
                                    episode.sid != null && episode.hash != null
                                ) {
                                    onEpisodeClick(episode.eid, episode.sid, episode.hash)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
