package com.ott.platform.domain.repository

import com.ott.platform.domain.model.AppMenuItem
import com.ott.platform.domain.model.Category
import com.ott.platform.domain.model.Channel
import com.ott.platform.domain.model.ContentId
import com.ott.platform.domain.model.HomeFeed
import com.ott.platform.domain.model.OttResult

interface CatalogRepository {
    suspend fun getMenu(): OttResult<List<AppMenuItem>>
    suspend fun getCategories(): OttResult<List<Category>>
    suspend fun getHomeFeed(limitPerShelf: Int = 24): OttResult<HomeFeed>
    suspend fun getLayoutFeed(layoutId: String, limitPerShelf: Int = 24): OttResult<HomeFeed>
    suspend fun getChannelsByCategory(categoryId: String, limit: Int = 40): OttResult<List<Channel>>
    suspend fun search(query: String, limit: Int = 40): OttResult<List<Channel>>
    suspend fun getChannel(id: ContentId): OttResult<Channel>

    /** Drop in-memory catalog caches so the next load hits the network (e.g. after reconnect). */
    suspend fun invalidateCache()
}
