package com.ott.platform.domain.usecase

import com.ott.platform.domain.model.AppMenuItem
import com.ott.platform.domain.model.Category
import com.ott.platform.domain.model.Channel
import com.ott.platform.domain.model.ContentId
import com.ott.platform.domain.model.HomeFeed
import com.ott.platform.domain.model.OttResult
import com.ott.platform.domain.repository.CatalogRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class GetMenuAndLayoutFeedUseCaseTest {

    private val menu = listOf(
        AppMenuItem(id = "tv_home", title = "Home", layoutId = "6af4302c6368a4cce6863ca6"),
        AppMenuItem(id = "movies", title = "Movies", layoutId = "6af4245f6368a4cce6863c73"),
    )

    private val repo = object : CatalogRepository {
        override suspend fun getMenu(): OttResult<List<AppMenuItem>> = OttResult.Success(menu)
        override suspend fun getCategories(): OttResult<List<Category>> = OttResult.Success(emptyList())
        override suspend fun getHomeFeed(limitPerShelf: Int): OttResult<HomeFeed> =
            OttResult.Success(HomeFeed(layoutId = "6af4302c6368a4cce6863ca6", layoutName = "Home"))
        override suspend fun getLayoutFeed(layoutId: String, limitPerShelf: Int): OttResult<HomeFeed> =
            OttResult.Success(HomeFeed(layoutId = layoutId, layoutName = "Movies"))
        override suspend fun getChannelsByCategory(categoryId: String, limit: Int): OttResult<List<Channel>> =
            OttResult.Success(emptyList())
        override suspend fun search(query: String, limit: Int): OttResult<List<Channel>> =
            OttResult.Success(emptyList())
        override suspend fun getChannel(id: ContentId): OttResult<Channel> =
            OttResult.Error("unused")
        override suspend fun invalidateCache() = Unit
    }

    @Test
    fun `returns dynamic menu items from repository`() = runTest {
        val result = GetMenuUseCase(repo)()
        assertTrue(result is OttResult.Success)
        assertEquals(2, (result as OttResult.Success).data.size)
        assertEquals("Home", result.data.first().title)
    }

    @Test
    fun `validates layoutId and shelf limit`() = runTest {
        val useCase = GetLayoutFeedUseCase(repo)
        assertFailsWith<IllegalArgumentException> { useCase("", 10) }
        assertFailsWith<IllegalArgumentException> { useCase("6af4245f6368a4cce6863c73", 0) }
        val res = useCase("6af4245f6368a4cce6863c73", 12)
        assertTrue(res is OttResult.Success)
        assertEquals("6af4245f6368a4cce6863c73", (res as OttResult.Success).data.layoutId)
    }
}
