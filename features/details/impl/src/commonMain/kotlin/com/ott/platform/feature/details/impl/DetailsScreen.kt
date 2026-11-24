package com.ott.platform.feature.details.impl

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ott.platform.designsystem.OttColors
import com.ott.platform.designsystem.OttDimens
import com.ott.platform.domain.model.Channel
import com.ott.platform.domain.model.ImageSlot
import com.ott.platform.ui.components.OttErrorPage
import com.ott.platform.ui.components.OttLoadingIndicator
import com.ott.platform.ui.components.RemoteImage
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DetailsRoute(
    contentId: String,
    onPlay: (Channel) -> Unit,
    onBack: () -> Unit,
    isTv: Boolean = false,
    viewModel: DetailsViewModel = koinViewModel(parameters = { parametersOf(contentId) }),
) {
    val state by viewModel.state.collectAsState()
    DetailsScreen(
        state = state,
        onPlay = { state.channel?.let(onPlay) },
        onToggleWatchlist = viewModel::onToggleWatchlist,
        onRetry = viewModel::load,
        onBack = onBack,
        isTv = isTv,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(
    state: DetailsUiState,
    onPlay: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
    isTv: Boolean = false,
) {
    val edge = if (isTv) OttDimens.ScreenPadding else OttDimens.PhoneScreenPadding
    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            OttLoadingIndicator(modifier = Modifier.testTag("details_loading"))
        }
        state.error != null -> OttErrorPage(
            onRetry = onRetry,
            compact = !isTv,
            detail = state.error,
            modifier = modifier.testTag("details_error"),
        )
        else -> {
            val channel = state.channel ?: return
            val backdropUrl = remember(channel.id, channel.images, channel.backdropUrl, channel.logoUrl) {
                channel.imageFor(ImageSlot.DetailsBackdrop)
            }
            val logoUrl = remember(channel.id, channel.images) {
                channel.imageFor(ImageSlot.Logo)
            }
            val metaParts = remember(channel) {
                listOfNotNull(
                    channel.releaseYear,
                    channel.runtimeMinutes?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" },
                    channel.parentalRating,
                    channel.imdbRating?.let { "★ $it" },
                    channel.quality,
                    channel.language?.uppercase(),
                    channel.assetType?.replaceFirstChar { it.uppercase() },
                )
            }
            val canPlay = channel.isPlayable || !channel.trailerUrl.isNullOrBlank()
            val primaryFocusRequester = remember { FocusRequester() }

            LaunchedEffect(channel.id) {
                runCatching { primaryFocusRequester.requestFocus() }
            }

            Column(
                modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(edge)
                    .testTag("details_content"),
            ) {
                var backFocused by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = onBack,
                    border = BorderStroke(
                        if (backFocused) 2.dp else 1.dp,
                        if (backFocused) OttColors.Focus else Color.White.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.onFocusChanged { backFocused = it.isFocused },
                ) { Text("< Back") }
                Spacer(Modifier.height(16.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isTv) 320.dp else 210.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = OttColors.SurfaceElevated,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        RemoteImage(
                            url = backdropUrl,
                            contentDescription = channel.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                    ),
                                ),
                        )
                        if (!logoUrl.isNullOrBlank()) {
                            RemoteImage(
                                url = logoUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp)
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (metaParts.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = metaParts.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OttColors.AccentSoft,
                        fontWeight = FontWeight.Medium,
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = channel.categoryName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OttColors.OnSurfaceMuted,
                    )
                }

                if (channel.genres.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        channel.genres.forEach { genre ->
                            Text(
                                text = genre,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(OttColors.SurfaceElevated)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                if (channel.synopsis.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = channel.synopsis,
                        style = MaterialTheme.typography.bodyLarge,
                        color = OttColors.OnSurface,
                    )
                }

                if (!channel.director.isNullOrBlank() || channel.cast.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    if (!channel.director.isNullOrBlank()) {
                        Text(
                            text = "Director: ${channel.director}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OttColors.OnSurfaceMuted,
                        )
                    }
                    if (channel.cast.isNotEmpty()) {
                        Text(
                            text = "Cast: ${channel.cast.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OttColors.OnSurfaceMuted,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    var playFocused by remember { mutableStateOf(false) }
                    var watchlistFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = onPlay,
                        enabled = canPlay,
                        border = if (playFocused) BorderStroke(2.dp, OttColors.Focus) else null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OttColors.Accent,
                            disabledContainerColor = OttColors.SurfaceElevated,
                            disabledContentColor = OttColors.OnSurfaceMuted,
                        ),
                        modifier = Modifier
                            .then(if (canPlay) Modifier.focusRequester(primaryFocusRequester) else Modifier)
                            .onFocusChanged { playFocused = it.isFocused }
                            .testTag("details_play"),
                    ) {
                        Text(
                            when {
                                channel.isPlayable -> "Watch Now"
                                !channel.trailerUrl.isNullOrBlank() -> "Watch Trailer"
                                else -> "Stream Coming Soon"
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = onToggleWatchlist,
                        border = BorderStroke(
                            if (watchlistFocused) 2.dp else 1.dp,
                            if (watchlistFocused) OttColors.Focus else Color.White.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier
                            .then(if (!canPlay) Modifier.focusRequester(primaryFocusRequester) else Modifier)
                            .onFocusChanged { watchlistFocused = it.isFocused }
                            .testTag("details_watchlist"),
                    ) {
                        Text(if (state.inWatchlist) "In Watchlist" else "Add to Watchlist")
                    }
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
    }
}
