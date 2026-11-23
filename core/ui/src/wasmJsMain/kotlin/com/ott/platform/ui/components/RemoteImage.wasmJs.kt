package com.ott.platform.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.ott.platform.designsystem.OttColors

@Composable
actual fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    alignment: Alignment,
) {
    if (url.isNullOrBlank()) {
        Placeholder(contentDescription, modifier)
        return
    }
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier.background(OttColors.SurfaceElevated),
        contentScale = contentScale,
        alignment = alignment,
    )
}

@Composable
private fun Placeholder(label: String?, modifier: Modifier) {
    Box(
        modifier = modifier.background(OttColors.SurfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label?.take(1)?.uppercase().orEmpty().ifBlank { "·" },
            color = Color.White,
        )
    }
}
