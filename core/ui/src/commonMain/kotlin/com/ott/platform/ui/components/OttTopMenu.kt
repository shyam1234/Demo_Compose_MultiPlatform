package com.ott.platform.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ott.platform.designsystem.OttColors
import com.ott.platform.designsystem.OttDimens
import com.ott.platform.domain.model.AppMenuItem
import kotlinx.coroutines.launch

data class OttMenuItem(
    val id: String,
    val label: String,
    val iconUrl: String? = null,
    val selectedIconUrl: String? = null,
    val focusedIconUrl: String? = null,
    val layoutId: String? = null,
    val children: List<AppMenuItem> = emptyList(),
)

/**
 * Top nav: supports dynamic CMS menu items, optional CMS icons, horizontal scrolling
 * when more menu items are added, and leanback focus ring.
 */
@Composable
fun OttTopMenu(
    items: List<OttMenuItem>,
    selectedId: String?,
    onItemSelected: (OttMenuItem) -> Unit,
    modifier: Modifier = Modifier,
    brand: String = "OTT",
    isTv: Boolean = false,
    onSearchClick: (() -> Unit)? = null,
) {
    val horizontal = if (isTv) OttDimens.ScreenPadding else OttDimens.PhoneScreenPadding
    val tabsState = rememberLazyListState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(OttColors.Background)
            .focusGroup()
            .focusRestorer()
            .padding(horizontal = horizontal, vertical = if (isTv) 16.dp else 10.dp)
            .testTag("ott_top_menu"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = brand,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("ott_brand"),
        )
        Spacer(Modifier.width(12.dp))
        LazyRow(
            state = tabsState,
            modifier = Modifier
                .weight(1f)
                .focusGroup()
                .focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(items, key = { it.id }) { item ->
                GoogleTvTab(
                    item = item,
                    selected = item.id == selectedId,
                    onClick = { onItemSelected(item) },
                    modifier = Modifier.testTag("ott_menu_${item.id}"),
                )
            }
        }
        if (onSearchClick != null) {
            var searchFocused by remember { mutableStateOf(false) }
            val interaction = remember { MutableInteractionSource() }
            val searchShape = RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .onFocusChanged { searchFocused = it.isFocused }
                    .clip(searchShape)
                    .background(if (searchFocused) OttColors.Focus else Color.Transparent, searchShape)
                    .border(1.dp, if (searchFocused) OttColors.Focus else Color.White.copy(alpha = 0.35f), searchShape)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onSearchClick,
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("ott_menu_search_icon"),
                contentAlignment = Alignment.Center,
            ) {
                Text("Search", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun GoogleTvTab(
    item: OttMenuItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val iconUrl = when {
        focused && !item.focusedIconUrl.isNullOrBlank() -> item.focusedIconUrl
        selected && !item.selectedIconUrl.isNullOrBlank() -> item.selectedIconUrl
        else -> item.iconUrl
    }
    Row(
        modifier = modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) {
                    scope.launch { bringIntoViewRequester.bringIntoView() }
                }
            }
            .clip(shape)
            .background(if (focused) OttColors.Focus else Color.Transparent, shape)
            .then(
                if (selected && !focused) {
                    Modifier.border(2.dp, OttColors.Focus, shape)
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!iconUrl.isNullOrBlank()) {
            RemoteImage(
                url = iconUrl,
                contentDescription = null,
                modifier = Modifier.size(18.dp).clip(CircleShape),
                contentScale = ContentScale.Fit,
            )
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = when {
                focused || selected -> FontWeight.SemiBold
                else -> FontWeight.Medium
            },
        )
    }
}
