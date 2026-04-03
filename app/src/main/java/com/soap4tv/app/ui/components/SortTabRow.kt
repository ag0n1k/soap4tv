package com.soap4tv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.soap4tv.app.data.repository.SortOption
import com.soap4tv.app.ui.theme.*

private val SORT_LABELS = mapOf(
    SortOption.TITLE to "Название",
    SortOption.YEAR to "Год",
    SortOption.IMDB to "Рейтинг",
    SortOption.LIKES to "Смотрят",
    SortOption.COMPLETE to "Закончен"
)

@Composable
fun SortTabRow(
    selected: SortOption,
    onSelect: (SortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(SortOption.values()) { option ->
            SortTab(
                text = SORT_LABELS[option] ?: option.name,
                isSelected = option == selected,
                onClick = { onSelect(option) }
            )
        }
    }
}

@Composable
private fun SortTab(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    isSelected -> Accent
                    isFocused -> SurfaceVariant
                    else -> Surface
                }
            )
            .border(
                width = if (isFocused && !isSelected) 1.dp else 0.dp,
                color = if (isFocused) Accent else Surface,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                isSelected -> OnPrimary
                isFocused -> OnBackground
                else -> OnSurface
            }
        )
    }
}
