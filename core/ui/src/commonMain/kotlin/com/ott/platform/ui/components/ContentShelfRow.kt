package com.ott.platform.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ott.platform.designsystem.OttColors
import com.ott.platform.designsystem.OttDimens
import com.ott.platform.domain.model.Channel
import com.ott.platform.domain.model.ContentShelf
import com.ott.platform.domain.model.RailContentType
import com.ott.platform.domain.model.ShelfCardStyle
import kotlinx.coroutines.launch

@Composable
fun ContentShelfRow(
    shelf: ContentShelf,
    onChannelClick: (Channel) -> Unit,
    modifier: Modifier = Modifier,
    isTv: Boolean = false,
    onPlay: ((Channel) -> Unit)? = null,
) {
    val openChannel: (Channel) -> Unit = remember(onChannelClick, onPlay) {
        { channel ->
            if (onPlay != null && channel.canPlay) {
                onPlay(channel)
            } else {
                onChannelClick(channel)
            }
        }
    }

    when {
        shelf.isDiscoveryDock -> {
            DiscoveryDockShelf(
                shelf = shelf,
                onChannelActivate = openChannel,
                isTv = isTv,
                modifier = modifier,
            )
        }
        shelf.isThematic -> {
            ThematicShelf(
                shelf = shelf,
                onChannelActivate = openChannel,
                isTv = isTv,
                modifier = modifier,
            )
        }
        else -> {
            StandardShelf(
                shelf = shelf,
                onChannelActivate = openChannel,
                isTv = isTv,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun DiscoveryDockShelf(
    shelf: ContentShelf,
    onChannelActivate: (Channel) -> Unit,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val horizontal = if (isTv) OttDimens.ScreenPadding else OttDimens.PhoneScreenPadding
    val tabs = shelf.discoveryDockTabs
    var selectedTabId by remember(shelf.id, tabs.map { it.id }) {
        mutableStateOf(tabs.firstOrNull()?.id.orEmpty())
    }
    val activeTab = tabs.firstOrNull { it.id == selectedTabId } ?: tabs.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("shelf_discovery_dock_${shelf.id}"),
    ) {
        ShelfTitle(title = shelf.title, isTv = isTv, horizontal = horizontal)

        if (tabs.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = horizontal, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.testTag("discovery_dock_tabs_${shelf.id}"),
            ) {
                items(tabs, key = { it.id }) { tab ->
                    val selected = tab.id == activeTab?.id
                    FocusablePillButton(
                        label = tab.title,
                        onClick = { selectedTabId = tab.id },
                        style = if (selected) PillStyle.Primary else PillStyle.Secondary,
                        compact = !isTv,
                    )
                }
            }
        }

        if (activeTab != null) {
            if (activeTab.isThematic) {
                ThematicShelfBody(
                    shelf = activeTab,
                    onChannelActivate = onChannelActivate,
                    isTv = isTv,
                    showTitle = false,
                )
            } else {
                ShelfItemsRow(
                    shelf = activeTab,
                    onChannelActivate = onChannelActivate,
                    isTv = isTv,
                    scrollOnFocus = isTv,
                )
            }
        }
    }
}

@Composable
private fun ThematicShelf(
    shelf: ContentShelf,
    onChannelActivate: (Channel) -> Unit,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("shelf_thematic_${shelf.id}"),
    ) {
        ThematicShelfBody(
            shelf = shelf,
            onChannelActivate = onChannelActivate,
            isTv = isTv,
            showTitle = true,
        )
    }
}

/**
 * Thematic / branded rail matching the discovery-dock design:
 * - CMS background (logo) pinned to the left, fully visible
 * - Cards start at 40% of the rail width so they never cover the logo at rest
 * - Card + metadata vertically centered in the main rail panel
 * - Focus lands on the first card; scroll keeps cards fully on-screen
 */
@Composable
private fun ThematicShelfBody(
    shelf: ContentShelf,
    onChannelActivate: (Channel) -> Unit,
    isTv: Boolean,
    showTitle: Boolean,
) {
    val horizontal = if (isTv) OttDimens.ScreenPadding else OttDimens.PhoneScreenPadding
    val innerPad = if (isTv) OttDimens.ThematicRailInnerPadding else OttDimens.PhoneThematicRailInnerPadding
    val corner = if (isTv) OttDimens.ThematicRailCorner else OttDimens.PhoneThematicRailCorner
    val containerShape = RoundedCornerShape(corner)
    val firstCardFocus = remember(shelf.id) { FocusRequester() }
    val listState = rememberLazyListState()
    val cardHeight = when (shelf.cardStyle) {
        ShelfCardStyle.Portrait -> if (isTv) OttDimens.TvPortraitCardHeight else OttDimens.PortraitCardHeight
        ShelfCardStyle.Square -> if (isTv) OttDimens.TvSquareCardSize else OttDimens.SquareCardSize
        ShelfCardStyle.Banner -> if (isTv) OttDimens.TvBannerCardHeight else OttDimens.BannerCardHeight
        ShelfCardStyle.Landscape -> if (isTv) OttDimens.TvCardHeight else OttDimens.CardHeight
    }
    // Panel tall enough for poster + title/year, with room to vertically center the block.
    val railMinHeight = cardHeight + OttDimens.CardTitleHeight + innerPad * 2 + if (isTv) 28.dp else 16.dp

    if (showTitle) {
        ShelfTitle(
            title = shelf.title,
            isTv = isTv,
            horizontal = horizontal,
            accent = true,
        )
        Spacer(Modifier.height(if (isTv) 10.dp else 6.dp))
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontal)
            .defaultMinSize(minHeight = railMinHeight)
            .focusGroup()
            .focusProperties {
                // Entering this rail from above/below focuses the first card.
                onEnter = { firstCardFocus.requestFocus() }
            }
            .testTag("thematic_container_${shelf.id}"),
    ) {
        // 40% of the full rail width reserved for CMS brand / logo artwork.
        val logoGutter = maxWidth * OttDimens.ThematicCardStartFraction
        val cardStartGap = if (isTv) 16.dp else 10.dp

        // Background panel — rounded; sized to the full rail (including vertical centering space).
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(containerShape)
                .background(Color(0xFF0B2748).copy(alpha = 0.92f)),
        ) {
            if (!shelf.backgroundImageUrl.isNullOrBlank()) {
                RemoteImage(
                    url = shelf.backgroundImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Keep left brand zone clear; darken only from the card start (~40%) onward.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                OttDimens.ThematicCardStartFraction to Color.Transparent,
                                0.52f to Color(0x18081C36),
                                0.70f to Color(0x44050F1E),
                                1.0f to Color(0x77050F1E),
                            ),
                        ),
                    ),
            )
        }

        // Vertically center card + metadata inside the main rail panel.
        ShelfItemsRow(
            shelf = shelf,
            onChannelActivate = onChannelActivate,
            isTv = isTv,
            listState = listState,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(start = logoGutter),
            contentPadding = PaddingValues(
                start = cardStartGap,
                end = innerPad,
                top = innerPad,
                bottom = innerPad + OttDimens.CardTitleHeight,
            ),
            fillWidth = true,
            scrollOnFocus = true,
            firstItemFocusRequester = firstCardFocus,
        )
    }
}

@Composable
private fun StandardShelf(
    shelf: ContentShelf,
    onChannelActivate: (Channel) -> Unit,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val horizontal = if (isTv) OttDimens.ScreenPadding else OttDimens.PhoneScreenPadding
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("shelf_${shelf.id}"),
    ) {
        Text(
            text = shelf.title,
            style = if (isTv) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            color = OttColors.OnSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = horizontal),
        )
        ShelfItemsRow(
            shelf = shelf,
            onChannelActivate = onChannelActivate,
            isTv = isTv,
            scrollOnFocus = isTv,
        )
    }
}

@Composable
private fun ShelfTitle(
    title: String,
    isTv: Boolean,
    horizontal: Dp,
    accent: Boolean = false,
) {
    Text(
        text = title,
        style = if (isTv) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
        color = if (accent) OttColors.AccentSoft else OttColors.OnSurface,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = horizontal),
    )
}

@Composable
private fun ShelfItemsRow(
    shelf: ContentShelf,
    onChannelActivate: (Channel) -> Unit,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues? = null,
    fillWidth: Boolean = true,
    scrollOnFocus: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    firstItemFocusRequester: FocusRequester? = null,
) {
    val horizontal = if (isTv) OttDimens.ScreenPadding else OttDimens.PhoneScreenPadding
    val gap = when (shelf.cardStyle) {
        ShelfCardStyle.Portrait -> if (isTv) 22.dp else 12.dp
        ShelfCardStyle.Square -> if (isTv) 20.dp else 12.dp
        ShelfCardStyle.Banner -> if (isTv) 24.dp else 14.dp
        ShelfCardStyle.Landscape -> if (isTv) OttDimens.RowGap else OttDimens.PhoneRowGap
    }
    val padding = contentPadding ?: PaddingValues(
        start = horizontal,
        end = horizontal,
        // Extra bottom room so title/subtitle + focus ring aren't clipped by the next shelf.
        top = if (isTv) 16.dp else 8.dp,
        bottom = if (isTv) 28.dp else 16.dp,
    )
    val scope = rememberCoroutineScope()
    val rowModifier = modifier
        .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
        .focusGroup()

    if (shelf.items.isEmpty()) {
        Box(
            modifier = rowModifier
                .padding(padding)
                .height(if (isTv) 48.dp else 36.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "No titles in this rail",
                color = OttColors.OnSurfaceMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        return
    }

    fun onCardFocused(index: Int) {
        if (!scrollOnFocus) return
        scope.launch {
            // Keep the focused card fully inside the horizontal viewport (first & last inclusive).
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == index }
            val fullyVisible = item != null &&
                item.offset >= info.viewportStartOffset &&
                item.offset + item.size <= info.viewportEndOffset
            if (!fullyVisible) {
                listState.animateScrollToItem(index)
            }
        }
    }

    if (shelf.contentType == RailContentType.Grid && shelf.items.size >= 4) {
        val columns = remember(shelf.items) { shelf.items.chunked(2) }
        LazyRow(
            state = listState,
            modifier = rowModifier,
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            itemsIndexed(columns, key = { idx, col -> "${shelf.id}_col_${idx}_${col.first().id.value}" }) { colIndex, columnItems ->
                Column(verticalArrangement = Arrangement.spacedBy(if (isTv) 18.dp else 12.dp)) {
                    columnItems.forEachIndexed { rowIndex, channel ->
                        val isFirst = colIndex == 0 && rowIndex == 0
                        ChannelCard(
                            channel = channel,
                            onClick = { onChannelActivate(channel) },
                            style = shelf.cardStyle,
                            contentType = shelf.contentType,
                            rawDisplayType = shelf.rawDisplayType,
                            isTv = isTv,
                            modifier = Modifier
                                .then(
                                    if (isFirst && firstItemFocusRequester != null) {
                                        Modifier.focusRequester(firstItemFocusRequester)
                                    } else {
                                        Modifier
                                    },
                                )
                                .onFocusChanged { state ->
                                    if (state.isFocused) onCardFocused(colIndex)
                                },
                        )
                    }
                }
            }
        }
    } else {
        LazyRow(
            state = listState,
            modifier = rowModifier,
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            itemsIndexed(shelf.items, key = { idx, item -> "${shelf.id}_${idx}_${item.id.value}" }) { index, channel ->
                ChannelCard(
                    channel = channel,
                    onClick = { onChannelActivate(channel) },
                    style = shelf.cardStyle,
                    contentType = shelf.contentType,
                    rawDisplayType = shelf.rawDisplayType,
                    rank = if (shelf.contentType == RailContentType.Top10) index + 1 else null,
                    isTv = isTv,
                    modifier = Modifier
                        .then(
                            if (index == 0 && firstItemFocusRequester != null) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier
                            },
                        )
                        .onFocusChanged { state ->
                            if (state.isFocused) onCardFocused(index)
                        },
                )
            }
        }
    }
}
