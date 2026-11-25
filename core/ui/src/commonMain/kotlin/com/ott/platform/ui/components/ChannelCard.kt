package com.ott.platform.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ott.platform.designsystem.OttColors
import com.ott.platform.designsystem.OttDimens
import com.ott.platform.domain.model.Channel
import com.ott.platform.domain.model.RailContentType
import com.ott.platform.domain.model.ShelfCardStyle
import kotlinx.coroutines.launch

@Composable
fun ChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    style: ShelfCardStyle = ShelfCardStyle.Landscape,
    contentType: RailContentType = RailContentType.Generic,
    rawDisplayType: String? = null,
    rank: Int? = null,
    isTv: Boolean = false,
) {
    val (width, height) = when (style) {
        ShelfCardStyle.Landscape ->
            if (isTv) OttDimens.TvCardWidth to OttDimens.TvCardHeight
            else OttDimens.CardWidth to OttDimens.CardHeight
        ShelfCardStyle.Portrait ->
            if (isTv) OttDimens.TvPortraitCardWidth to OttDimens.TvPortraitCardHeight
            else OttDimens.PortraitCardWidth to OttDimens.PortraitCardHeight
        ShelfCardStyle.Square ->
            if (isTv) OttDimens.TvSquareCardSize to OttDimens.TvSquareCardSize
            else OttDimens.SquareCardSize to OttDimens.SquareCardSize
        ShelfCardStyle.Banner ->
            if (isTv) OttDimens.TvBannerCardWidth to OttDimens.TvBannerCardHeight
            else OttDimens.BannerCardWidth to OttDimens.BannerCardHeight
    }
    ChannelCard(
        channel = channel,
        onClick = onClick,
        modifier = modifier,
        focused = focused,
        width = width,
        height = height,
        style = style,
        contentType = contentType,
        rawDisplayType = rawDisplayType,
        rank = rank,
        isTv = isTv,
    )
}

/**
 * Leanback-style card with full-bleed CMS artwork, adaptive aspect ratio,
 * Top 10 rank overlay, and crisp blue focus ring on TV.
 */
@Composable
fun ChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    width: Dp,
    height: Dp,
    style: ShelfCardStyle = ShelfCardStyle.Landscape,
    contentType: RailContentType = RailContentType.Generic,
    rawDisplayType: String? = null,
    rank: Int? = null,
    isTv: Boolean = false,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val isFocused = focused || hasFocus
    val ringGap = 3.dp
    // Keep ring + TV focus scale inside layout bounds — LazyRow clips item overflow.
    val focusHalo = if (isTv) 12.dp else 8.dp
    val shape = RoundedCornerShape(OttDimens.CardCorner)
    val outerShape = RoundedCornerShape(OttDimens.CardCorner + 2.dp)
    val interaction = remember { MutableInteractionSource() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val imageUrl = remember(channel.id, channel.images, rawDisplayType, style, contentType) {
        channel.imageForRail(
            rawDisplayType = rawDisplayType ?: style.name,
            contentType = contentType,
        )
    }

    Column(
        modifier = modifier
            .width(width)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { state ->
                hasFocus = state.isFocused
                // Single scroll owner: default bring-into-view (no inflated margins /
                // competing animateScrollToItem). Parents supply real contentPadding.
                if (state.isFocused) {
                    scope.launch { bringIntoViewRequester.bringIntoView() }
                }
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .testTag("channel_card_${channel.id.value}"),
        horizontalAlignment = Alignment.Start,
    ) {
        // Poster + focus ring as siblings so clip(shape) never eats the border, and both
        // stay inside the layout slot (LazyRow clips any overflow of the item).
        Box(
            modifier = Modifier
                .width(width)
                .height(height),
        ) {
            val contentInset = focusHalo + if (isFocused) ringGap else 0.dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentInset)
                    .graphicsLayer {
                        val scale = if (isTv && isFocused) 1.03f else 1f
                        scaleX = scale
                        scaleY = scale
                        clip = false
                    }
                    .clip(shape)
                    .background(OttColors.SurfaceElevated),
            ) {
                RemoteImage(
                    url = imageUrl,
                    contentDescription = channel.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                // Bottom gradient scrim for rank / editorial / badge legibility
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                            ),
                        ),
                )

                if (rank != null) {
                    Text(
                        text = "$rank",
                        color = Color.White,
                        fontSize = if (isTv) 34.sp else 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                // Parental rating only — no play badge (defaultPlaybackUrl makes most items playable).
                if (!channel.parentalRating.isNullOrBlank()) {
                    Text(
                        text = channel.parentalRating!!,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(focusHalo)
                        .border(OttDimens.FocusBorder, OttColors.Focus, outerShape),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = channel.title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(width),
        )
        val subtitle = remember(channel.genres, channel.releaseYear, channel.assetType) {
            listOfNotNull(
                channel.releaseYear,
                channel.genres.firstOrNull(),
            ).joinToString(" · ")
        }
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = OttColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(width),
            )
        }
    }
}
