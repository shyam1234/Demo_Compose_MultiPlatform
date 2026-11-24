package com.ott.platform.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ott.platform.domain.model.AppMenuItem
import com.ott.platform.domain.model.OttResult
import com.ott.platform.domain.usecase.GetMenuUseCase
import com.ott.platform.domain.usecase.InvalidateCatalogCacheUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppMenuUiState(
    val isLoading: Boolean = true,
    val items: List<AppMenuItem> = emptyList(),
    val error: String? = null,
)

class AppMenuViewModel(
    private val getMenu: GetMenuUseCase,
    private val invalidateCatalogCache: InvalidateCatalogCacheUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AppMenuUiState())
    val state: StateFlow<AppMenuUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val shouldForceNetwork = _state.value.error != null || _state.value.items.isEmpty()
            _state.update { it.copy(isLoading = true, error = null) }
            if (shouldForceNetwork) {
                runCatching { invalidateCatalogCache() }
            }
            when (val result = getMenu()) {
                is OttResult.Success -> _state.update {
                    it.copy(
                        isLoading = false,
                        items = result.data,
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
}
