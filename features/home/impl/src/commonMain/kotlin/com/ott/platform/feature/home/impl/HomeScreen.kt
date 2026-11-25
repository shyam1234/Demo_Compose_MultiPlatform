package com.ott.platform.feature.home.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ott.platform.designsystem.OttColors
import com.ott.platform.designsystem.OttDimens
import com.ott.platform.domain.model.AppMenuItem
import com.ott.platform.domain.model.Channel
import com.ott.platform.ui.components.ContentShelfRow
import com.ott.platform.ui.components.FocusablePillButton
import com.ott.platform.ui.components.HeroBannerCarousel
import com.ott.platform.ui.components.OttErrorPage
import com.ott.platform.ui.components.OttLoadingIndicator
import com.ott.platform.ui.components.PillStyle
import com.ott.platform.ui.components.RemoteImage
import com.ott.platform.ui.components.isServerUnavailableMessage
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun HomeRoute(
    onOpenDetails: (Channel) -> Unit,
    onPlay: (Channel) -> Unit,
    isTv: Boolean = false,
    layoutId: String? = null,
    subMenuItems: List<AppMenuItem> = emptyList(),
    viewModel: HomeViewModel = koinViewModel(
        key = layoutId ?: "home_default",
        parameters = { parametersOf(layoutId) },
    ),
) {
    LaunchedEffect(layoutId) {
        if (layoutId != null) {
            viewModel.selectLayout(layoutId)
        }
    }
    val state by viewModel.state.collectAsState()
    HomeScreen(
        state = state,
        isTv = isTv,
        subMenuItems = subMenuItems,
        onSubMenuClick = { item -> item.layoutId?.let(viewModel::selectLayout) },
        onRetry = { viewModel.refresh() },
        onOpenDetails = onOpenDetails,
        onPlay = onPlay,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
    onOpenDetails: (Channel) -> Unit,
    onPlay: (Channel) -> Unit,
    isTv: Boolean = false,
    subMenuItems: List<AppMenuItem> = emptyList(),
    onSubMenuClick: (AppMenuItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading && state.feed == null -> {
            Box(
                modifier
                    .fillMaxSize()
                    .background(OttColors.Background),
                contentAlignment = Alignment.Center,
            ) {
                OttLoadingIndicator(modifier = Modifier.testTag("home_loading"))
            }
        }
        state.error != null && state.feed == null -> {
            OttErrorPage(
                onRetry = onRetry,
                compact = !isTv,
                detail = state.error.takeIf { isServerUnavailableMessage(it) || it.isNotBlank() },
                modifier = modifier.testTag("home_retry"),
            )
        }
        else -> {
            val feed = state.feed ?: return
            val edge = if (isTv) OttDimens.ScreenPadding else OttDimens.PhoneScreenPadding
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(OttColors.Background),
            ) {
                // Subtle atmospheric theme backdrop provided by CMS Layout theme_url
                if (!feed.themeUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isTv) 480.dp else 320.dp),
                    ) {
                        RemoteImage(
                            url = feed.themeUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0.18f),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, OttColors.Background),
                                    ),
                                ),
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { clip = false }
                        .focusGroup()
                        .focusRestorer()
                        .testTag("home_feed"),
                    // Real list insets only (chrome + last-item breathing room) — focus
                    // bring-into-view owns visibility, not oversized padding hacks.
                    contentPadding = PaddingValues(
                        top = if (isTv) 16.dp else 8.dp,
                        bottom = if (isTv) 80.dp else 64.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(
                        if (isTv) OttDimens.ShelfSpacing else OttDimens.PhoneShelfSpacing,
                    ),
                ) {
                    if (subMenuItems.isNotEmpty()) {
                        item(key = "sub_menu_bar") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = edge, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.testTag("home_submenu"),
                            ) {
                                items(subMenuItems, key = { it.id }) { sub ->
                                    val selected = sub.layoutId == state.activeLayoutId
                                    FocusablePillButton(
                                        label = sub.title,
                                        onClick = { onSubMenuClick(sub) },
                                        style = if (selected) PillStyle.Primary else PillStyle.Secondary,
                                        compact = !isTv,
                                    )
                                }
                            }
                        }
                    }

                    if (feed.heroes.isNotEmpty()) {
                        item(key = "hero_carousel") {
                            HeroBannerCarousel(
                                channels = feed.heroes,
                                onPlay = onPlay,
                                onDetails = onOpenDetails,
                                height = if (isTv) OttDimens.TvHeroHeight else OttDimens.HeroHeight,
                                isTv = isTv,
                            )
                        }
                    }

                    items(feed.shelves, key = { it.id }) { shelf ->
                        ContentShelfRow(
                            shelf = shelf,
                            onChannelClick = onOpenDetails,
                            onPlay = onPlay,
                            isTv = isTv,
                        )
                    }

                    if (state.error != null) {
                        item {
                            Row(modifier = Modifier.padding(horizontal = edge)) {
                                Text(
                                    text = "Partial error: ${state.error}",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
