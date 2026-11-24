package com.ott.platform.feature.home.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ott.platform.domain.model.HomeFeed
import com.ott.platform.domain.model.OttResult
import com.ott.platform.domain.usecase.GetHomeFeedUseCase
import com.ott.platform.domain.usecase.GetLayoutFeedUseCase
import com.ott.platform.domain.usecase.InvalidateCatalogCacheUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val activeLayoutId: String? = null,
    val feed: HomeFeed? = null,
    val error: String? = null,
)

class HomeViewModel(
    private val initialLayoutId: String? = null,
    private val getHomeFeed: GetHomeFeedUseCase,
    private val getLayoutFeed: GetLayoutFeedUseCase,
    private val invalidateCatalogCache: InvalidateCatalogCacheUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(activeLayoutId = initialLayoutId))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh(initialLayoutId)
    }

    fun selectLayout(layoutId: String?) {
        if (layoutId == _state.value.activeLayoutId && _state.value.feed != null) return
        refresh(layoutId)
    }

    fun refresh(layoutId: String? = _state.value.activeLayoutId) {
        viewModelScope.launch {
            val recoveringFromError = _state.value.error != null
            _state.update { it.copy(isLoading = true, activeLayoutId = layoutId, error = null) }
            if (recoveringFromError) {
                runCatching { invalidateCatalogCache() }
            }
            val result = if (!layoutId.isNullOrBlank()) {
                getLayoutFeed(layoutId, DEFAULT_SHELF_LIMIT)
            } else {
                getHomeFeed(DEFAULT_SHELF_LIMIT)
            }
            when (result) {
                is OttResult.Success -> _state.update {
                    it.copy(
                        isLoading = false,
                        activeLayoutId = result.data.layoutId.ifBlank { layoutId },
                        feed = result.data,
                        error = null,
                    )
                }
                is OttResult.Error -> _state.update {
                    it.copy(isLoading = false, error = result.message)
                }
                OttResult.Loading -> _state.update { it.copy(isLoading = true) }
            }
        }
    }

    private companion object {
        const val DEFAULT_SHELF_LIMIT = 24
    }
}
