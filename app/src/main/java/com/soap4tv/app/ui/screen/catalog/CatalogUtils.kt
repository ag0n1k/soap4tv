package com.soap4tv.app.ui.screen.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.soap4tv.app.ui.theme.*

@Composable
internal fun RetryButton(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Text(
        text = "Повторить",
        style = MaterialTheme.typography.labelLarge,
        color = if (isFocused) OnBackground else OnSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) SurfaceVariant else Surface)
            .border(1.dp, if (isFocused) Accent else SurfaceVariant, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 24.dp, vertical = 12.dp)
    )
}
