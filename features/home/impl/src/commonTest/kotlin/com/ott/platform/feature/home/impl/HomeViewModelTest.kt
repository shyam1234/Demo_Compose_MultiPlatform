package com.ott.platform.feature.home.impl

import com.ott.platform.domain.usecase.GetHomeFeedUseCase
import com.ott.platform.domain.usecase.GetLayoutFeedUseCase
import com.ott.platform.domain.usecase.InvalidateCatalogCacheUseCase
import com.ott.platform.testing.FakeCatalogRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @Test
    fun `loads home feed and switches dynamic layout`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repo = FakeCatalogRepository()
            val vm = HomeViewModel(
                initialLayoutId = null,
                getHomeFeed = GetHomeFeedUseCase(repo),
                getLayoutFeed = GetLayoutFeedUseCase(repo),
                invalidateCatalogCache = InvalidateCatalogCacheUseCase(repo),
            )
            advanceUntilIdle()
            val state = vm.state.value
            assertFalse(state.isLoading)
            val feed = state.feed
            assertNotNull(feed)
            assertEquals("The RIP", feed.hero?.title)
            assertEquals(3, feed.shelves.size)

            vm.selectLayout("movies_layout")
            advanceUntilIdle()
            val moviesState = vm.state.value
            assertEquals("movies_layout", moviesState.activeLayoutId)
            val moviesFeed = moviesState.feed
            assertNotNull(moviesFeed)
            assertEquals("Movies", moviesFeed.layoutName)
            assertEquals(2, moviesFeed.shelves.size)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
