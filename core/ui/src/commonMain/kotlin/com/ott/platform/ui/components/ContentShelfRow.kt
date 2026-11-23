package com.ott.platform.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable
private fun ThematicShelfBody(
    shelf: ContentShelf,
    onChannelActivate: (Channel) -> Unit,
    isTv: Boolean,
    showTitle: Boolean,
) {
    val horizontal = if (isTv) OttDimens.ScreenPadding else OttDimens.PhoneScreenPadding
    val containerShape = RoundedCornerShape(if (isTv) 20.dp else 16.dp)

    if (showTitle) {
        ShelfTitle(
            title = shelf.title,
            isTv = isTv,
            horizontal = horizontal,
            accent = true,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontal)
            .clip(containerShape)
            // Visible thematic panel even when CMS background artwork fails (e.g. 502).
            .background(OttColors.Accent.copy(alpha = 0.22f))
            .testTag("thematic_container_${shelf.id}"),
    ) {
        if (!shelf.backgroundImageUrl.isNullOrBlank()) {
            RemoteImage(
                url = shelf.backgroundImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xCC0B2748),
                                Color(0x99081C36),
                                Color(0x66050F1E),
                            ),
                        ),
                    ),
            )
        }
        ShelfItemsRow(
            shelf = shelf,
            onChannelActivate = onChannelActivate,
            isTv = isTv,
            contentPadding = PaddingValues(
                horizontal = if (isTv) 20.dp else 14.dp,
                vertical = if (isTv) 20.dp else 14.dp,
            ),
            fillWidth = true,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = shelf.title,
                style = if (isTv) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                color = OttColors.OnSurface,
                fontWeight = FontWeight.SemiBold,
            )
            val badge = when (shelf.contentType) {
                RailContentType.Top10 -> "TOP 10"
                RailContentType.New -> "NEW"
                RailContentType.Editorial -> "SPOTLIGHT"
                RailContentType.Grid -> "${shelf.items.size} TITLES"
                else -> null
            }
            if (badge != null) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = OttColors.AccentSoft,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(OttColors.SurfaceElevated)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        ShelfItemsRow(
            shelf = shelf,
            onChannelActivate = onChannelActivate,
            isTv = isTv,
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
    contentPadding: PaddingValues? = null,
    fillWidth: Boolean = true,
) {
    val horizontal = if (isTv) OttDimens.ScreenPadding else OttDimens.PhoneScreenPadding
    val gap = when (shelf.cardStyle) {
        ShelfCardStyle.Portrait -> if (isTv) 22.dp else 12.dp
        ShelfCardStyle.Square -> if (isTv) 20.dp else 12.dp
        ShelfCardStyle.Banner -> if (isTv) 24.dp else 14.dp
        ShelfCardStyle.Landscape -> if (isTv) OttDimens.RowGap else OttDimens.PhoneRowGap
    }
    val padding = contentPadding ?: PaddingValues(
        horizontal = horizontal,
        vertical = if (isTv) 16.dp else 8.dp,
    )

    if (shelf.items.isEmpty()) {
        Box(
            modifier = Modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
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

    if (shelf.contentType == RailContentType.Grid && shelf.items.size >= 4) {
        val columns = remember(shelf.items) { shelf.items.chunked(2) }
        LazyRow(
            modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            itemsIndexed(columns, key = { idx, col -> "${shelf.id}_col_${idx}_${col.first().id.value}" }) { _, columnItems ->
                Column(verticalArrangement = Arrangement.spacedBy(if (isTv) 18.dp else 12.dp)) {
                    columnItems.forEach { channel ->
                        ChannelCard(
                            channel = channel,
                            onClick = { onChannelActivate(channel) },
                            style = shelf.cardStyle,
                            contentType = shelf.contentType,
                            rawDisplayType = shelf.rawDisplayType,
                            isTv = isTv,
                        )
                    }
                }
            }
        }
    } else {
        LazyRow(
            modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
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
                )
            }
        }
    }
}
