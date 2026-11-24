package com.ott.platform.shared

import com.ott.platform.domain.usecase.GetMenuUseCase
import com.ott.platform.domain.usecase.InvalidateCatalogCacheUseCase
import com.ott.platform.testing.FakeCatalogRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class AppMenuViewModelTest {

    @Test
    fun `loads dynamic cms menu items on startup`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repo = FakeCatalogRepository()
            val vm = AppMenuViewModel(
                getMenu = GetMenuUseCase(repo),
                invalidateCatalogCache = InvalidateCatalogCacheUseCase(repo),
            )
            advanceUntilIdle()
            val state = vm.state.value
            assertFalse(state.isLoading)
            assertEquals(listOf("Home", "Movies"), state.items.map { it.title })
            assertEquals("home_layout", state.items.first().layoutId)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
