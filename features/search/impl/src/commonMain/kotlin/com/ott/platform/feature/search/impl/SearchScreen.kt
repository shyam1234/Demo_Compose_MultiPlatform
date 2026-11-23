package com.ott.platform.feature.search.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ott.platform.designsystem.OttDimens
import com.ott.platform.domain.model.Channel
import com.ott.platform.domain.model.ShelfCardStyle
import com.ott.platform.ui.components.ChannelCard
import com.ott.platform.ui.components.OttErrorPage
import com.ott.platform.ui.components.OttLoadingIndicator
import com.ott.platform.ui.components.isServerUnavailableMessage
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SearchRoute(
    onOpenDetails: (Channel) -> Unit,
    isTv: Boolean = false,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    SearchScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onRetry = { viewModel.onQueryChange(state.query) },
        onOpenDetails = onOpenDetails,
        isTv = isTv,
    )
}

@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onOpenDetails: (Channel) -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
    isTv: Boolean = false,
) {
    if (!state.isLoading && state.results.isEmpty() && isServerUnavailableMessage(state.error)) {
        OttErrorPage(
            onRetry = onRetry,
            compact = !isTv,
            detail = state.error,
            modifier = modifier,
        )
        return
    }
    val edge = if (isTv) OttDimens.ScreenPadding else OttDimens.PhoneScreenPadding
    Column(modifier.fillMaxSize().padding(edge)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().testTag("search_field"),
            singleLine = true,
            label = { Text("Search movies, shows & genres") },
            placeholder = { Text("The RIP, Sci-Fi, Reacher…") },
        )
        if (state.isLoading) {
            OttLoadingIndicator(modifier = Modifier.padding(16.dp).testTag("search_loading"))
        }
        state.error?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (isTv) 220.dp else 156.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().testTag("search_results"),
        ) {
            items(state.results, key = { it.id.value }) { channel ->
                ChannelCard(
                    channel = channel,
                    onClick = { onOpenDetails(channel) },
                    style = ShelfCardStyle.Landscape,
                    isTv = isTv,
                )
            }
        }
    }
}
