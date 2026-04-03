package com.soap4tv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.soap4tv.app.data.model.Episode
import com.soap4tv.app.ui.theme.*

@Composable
fun EpisodeRow(
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isFocused -> SurfaceVariant
                    episode.isWatched -> Surface.copy(alpha = 0.5f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isFocused) 1.dp else 0.dp,
                color = if (isFocused) FocusHighlight else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { if (episode.canPlay) onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Episode number
        Text(
            text = episode.number.toString().padStart(2, '0'),
            style = MaterialTheme.typography.titleMedium,
            color = if (isFocused) Accent else TextSecondary,
            modifier = Modifier.width(36.dp)
        )

        // Watched indicator
        Icon(
            imageVector = if (episode.isWatched) Icons.Default.Check else Icons.Default.PlayArrow,
            contentDescription = if (episode.isWatched) "Watched" else "Not watched",
            tint = if (episode.isWatched) WatchedGreen
                else if (isFocused) Accent
                else TextSecondary.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )

        // Titles
        Column(modifier = Modifier.weight(1f)) {
            val displayTitle = episode.titleRu.ifBlank { episode.titleEn }
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isFocused) OnBackground else if (episode.isWatched) TextSecondary else OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (episode.titleRu.isNotBlank() && episode.titleEn.isNotBlank() &&
                episode.titleRu != episode.titleEn) {
                Text(
                    text = episode.titleEn,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (episode.airDate.isNotBlank()) {
                Text(
                    text = episode.airDate,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary.copy(alpha = 0.7f)
                )
            }
        }

        // Quality badge
        if (episode.qualityLabel.isNotBlank()) {
            QualityBadge(label = episode.qualityLabel)
        }

        // Translate / dub label
        if (episode.translateLabel.isNotBlank()) {
            Text(
                text = episode.translateLabel,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.widthIn(max = 100.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun QualityBadge(label: String, modifier: Modifier = Modifier) {
    val color = when (label.uppercase()) {
        "FHD", "1080P" -> QualityFHD
        "HD", "720P" -> QualityHD
        else -> QualitySD
    }
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}
