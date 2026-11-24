package com.ott.platform.testing

import com.ott.platform.domain.model.AppMenuItem
import com.ott.platform.domain.model.Category
import com.ott.platform.domain.model.Channel
import com.ott.platform.domain.model.ContentId
import com.ott.platform.domain.model.ContentShelf
import com.ott.platform.domain.model.HomeFeed
import com.ott.platform.domain.model.ImageTypes
import com.ott.platform.domain.model.MediaImage
import com.ott.platform.domain.model.OttResult
import com.ott.platform.domain.model.RailContentType
import com.ott.platform.domain.model.ShelfCardStyle
import com.ott.platform.domain.repository.CatalogRepository

class FakeCatalogRepository(
    private val channels: List<Channel> = sampleChannels(),
    private val menuItems: List<AppMenuItem> = sampleMenu(),
) : CatalogRepository {

    override suspend fun getMenu(): OttResult<List<AppMenuItem>> =
        OttResult.Success(menuItems)

    override suspend fun getCategories(): OttResult<List<Category>> =
        OttResult.Success(
            channels.map { Category(it.categoryId, it.categoryName) }.distinctBy { it.id },
        )

    override suspend fun getHomeFeed(limitPerShelf: Int): OttResult<HomeFeed> =
        getLayoutFeed(menuItems.firstOrNull()?.layoutId ?: "home_layout", limitPerShelf)

    override suspend fun getLayoutFeed(layoutId: String, limitPerShelf: Int): OttResult<HomeFeed> {
        val filtered = if (layoutId == "movies_layout") {
            channels.filter { it.assetType == "movies" || it.categoryId == "movies" }
        } else {
            channels
        }
        val shelves = filtered
            .groupBy { it.categoryId }
            .map { (id, items) ->
                val style = if (id == "movies" || id == "popular" || id == "top_10") {
                    ShelfCardStyle.Portrait
                } else {
                    ShelfCardStyle.Landscape
                }
                val contentType = if (id == "top_10") RailContentType.Top10 else RailContentType.Generic
                ContentShelf(
                    id = id,
                    title = items.first().categoryName,
                    items = items.take(limitPerShelf),
                    cardStyle = style,
                    contentType = contentType,
                )
            }
        return OttResult.Success(
            HomeFeed(
                heroes = filtered.filter { !it.logoUrl.isNullOrBlank() }.ifEmpty { filtered.take(1) },
                shelves = shelves,
                layoutId = layoutId,
                layoutName = menuItems.firstOrNull { it.layoutId == layoutId }?.title ?: "Home",
            ),
        )
    }

    override suspend fun getChannelsByCategory(categoryId: String, limit: Int): OttResult<List<Channel>> =
        OttResult.Success(channels.filter { it.categoryId == categoryId }.take(limit))

    override suspend fun search(query: String, limit: Int): OttResult<List<Channel>> =
        OttResult.Success(
            channels.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.genres.any { g -> g.contains(query, ignoreCase = true) } ||
                    it.synopsis.contains(query, ignoreCase = true)
            }.take(limit),
        )

    override suspend fun getChannel(id: ContentId): OttResult<Channel> =
        channels.firstOrNull { it.id == id }?.let { OttResult.Success(it) }
            ?: OttResult.Error("not found")

    override suspend fun invalidateCache() = Unit

    companion object {
        fun sampleMenu(): List<AppMenuItem> = listOf(
            AppMenuItem(id = "tv_home", title = "Home", layoutId = "home_layout"),
            AppMenuItem(id = "movies", title = "Movies", layoutId = "movies_layout"),
        )

        fun sampleChannels(): List<Channel> = listOf(
            Channel(
                id = ContentId("new:the-rip"),
                title = "The RIP",
                logoUrl = "https://example.com/rip-land.png",
                backdropUrl = "https://example.com/rip-land.png",
                streamUrl = "https://example.com/rip.m3u8",
                categoryId = "new_on_tata",
                categoryName = "New on Tata Elxsi",
                images = listOf(
                    MediaImage(ImageTypes.PORTRAIT, "https://example.com/rip-port.png", 729, 1080),
                    MediaImage(ImageTypes.LANDSCAPE, "https://example.com/rip-land.png", 1920, 1080),
                ),
                synopsis = "Miami police officers discover a stash of cash.",
                genres = listOf("Crime", "Thriller"),
                releaseYear = "2026",
                runtimeMinutes = 120,
                parentalRating = "16+",
                assetType = "movies",
            ),
            Channel(
                id = ContentId("movies:demo"),
                title = "Classic Movies",
                logoUrl = "https://example.com/movies.png",
                streamUrl = "https://example.com/movies.m3u8",
                categoryId = "movies",
                categoryName = "Popular with Viewers",
                images = listOf(
                    MediaImage(ImageTypes.PORTRAIT, "https://example.com/movies.png", 729, 1080),
                ),
                genres = listOf("Drama"),
                assetType = "movies",
            ),
            Channel(
                id = ContentId("shows:colony"),
                title = "Beyond Earth Colony",
                logoUrl = null,
                streamUrl = "",
                categoryId = "tv_shows",
                categoryName = "TV Shows",
                genres = listOf("Sci-Fi", "Fiction"),
                imdbRating = 8.0,
                assetType = "shows",
            ),
        )
    }
}
