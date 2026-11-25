package com.ott.platform.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ott.platform.domain.model.AppMenuItem
import com.ott.platform.domain.model.Category
import com.ott.platform.domain.model.Channel
import com.ott.platform.domain.model.ContentId
import com.ott.platform.domain.model.ImageTypes
import com.ott.platform.domain.model.MediaImage
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightDataSourcesTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: OttDatabase
    private lateinit var catalogLocal: SqlDelightCatalogLocalDataSource
    private lateinit var watchlistLocal: SqlDelightWatchlistLocalDataSource
    private var fakeClockMs = 1_000L

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OttDatabase.Schema.create(driver)
        database = OttDatabase(driver)
        catalogLocal = SqlDelightCatalogLocalDataSource(
            database = database,
            ioDispatcher = Dispatchers.Unconfined,
            nowMs = { fakeClockMs },
        )
        watchlistLocal = SqlDelightWatchlistLocalDataSource(
            database = database,
            ioDispatcher = Dispatchers.Unconfined,
            nowMs = { fakeClockMs },
        )
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `watchlist persists add toggle remove and metadata`() = runTest {
        val movie = sampleChannel(id = "movie:1", title = "The RIP", categoryId = "new")
        val show = sampleChannel(id = "show:1", title = "Reacher", categoryId = "tv_shows")

        assertTrue(watchlistLocal.observeAll().first().isEmpty())

        watchlistLocal.add(movie)
        watchlistLocal.add(movie) // duplicate add should be ignored
        fakeClockMs += 100L
        assertTrue(watchlistLocal.toggle(show))

        val saved = watchlistLocal.observeAll().first()
        assertEquals(listOf(movie.id, show.id), saved.map { it.id })
        assertEquals(movie.images, saved.first().images)
        assertEquals(movie.genres, saved.first().genres)
        assertTrue(watchlistLocal.exists(movie.id))
        assertTrue(watchlistLocal.exists(show.id))

        assertFalse(watchlistLocal.toggle(movie))
        assertFalse(watchlistLocal.exists(movie.id))

        watchlistLocal.remove(show.id)
        assertTrue(watchlistLocal.observeAll().first().isEmpty())
    }

    @Test
    fun `catalog stores menu layouts categories channels and searches rich metadata`() = runTest {
        val menu = listOf(
            AppMenuItem(
                id = "tv_home",
                title = "Home",
                layoutId = "6af4302c6368a4cce6863ca6",
                deeplinkUrl = "http://layouts/6af4302c6368a4cce6863ca6",
            ),
            AppMenuItem(
                id = "movies",
                title = "Movies",
                layoutId = "6af4245f6368a4cce6863c73",
                children = listOf(
                    AppMenuItem(id = "action", title = "Action", layoutId = "action_layout"),
                ),
            ),
        )
        catalogLocal.replaceMenuItems(menu)
        assertEquals(menu, catalogLocal.getMenuItems())
        assertEquals(1_000L, catalogLocal.getMenuUpdatedAtMs())

        val layout = CachedLayout(
            id = "6af4302c6368a4cce6863ca6",
            name = "Home",
            themeUrl = "https://cms.play-cms.com/theme.png",
            railIds = listOf("rail_1", "rail_2"),
        )
        catalogLocal.saveLayout(layout)
        val loadedLayout = catalogLocal.getLayout("6af4302c6368a4cce6863ca6")
        assertNotNull(loadedLayout)
        assertEquals(listOf("rail_1", "rail_2"), loadedLayout.railIds)
        assertEquals("https://cms.play-cms.com/theme.png", loadedLayout.themeUrl)

        val categories = listOf(
            Category(id = "rail_1", name = "New Releases", displayType = "Landscape", contentType = "new"),
            Category(id = "rail_2", name = "Top 10 Today", displayType = "Portrait", contentType = "top_10"),
        )
        catalogLocal.replaceCategories(categories)
        assertEquals(categories, catalogLocal.getCategories())
        assertEquals("top_10", catalogLocal.getCategory("rail_2")?.contentType)

        val rail1Items = listOf(
            sampleChannel(id = "m1", title = "The RIP", categoryId = "rail_1", logoUrl = "https://img/1.png"),
            sampleChannel(id = "m2", title = "Dead Man's Wire", categoryId = "rail_1", logoUrl = null),
        )
        val rail2Items = listOf(
            sampleChannel(id = "m1", title = "The RIP", categoryId = "rail_2"),
            sampleChannel(id = "m3", title = "Becoming The Beckhams", categoryId = "rail_2"),
        )

        catalogLocal.replaceChannelsForCategory("rail_1", rail1Items)
        catalogLocal.replaceChannelsForCategory("rail_2", rail2Items)

        assertEquals(rail1Items, catalogLocal.getChannelsByCategory("rail_1", limit = 10))
        assertEquals(rail2Items, catalogLocal.getChannelsByCategory("rail_2", limit = 10))
        assertEquals(3L, catalogLocal.getChannelCount()) // m1, m2, m3 distinct asset ids

        val byId = catalogLocal.getChannel(ContentId("m1"))
        assertNotNull(byId)
        assertEquals(listOf("Crime", "Thriller"), byId.genres)

        // Searches title AND metadata_json (genres/synopsis), deduplicated across rails
        val genreHits = catalogLocal.searchChannels("Thriller", limit = 10)
        assertEquals(3, genreHits.size)

        // Replacing one rail leaves other rails intact (including shared asset m1)
        val updatedRail1 = listOf(sampleChannel(id = "m4", title = "Test Asset", categoryId = "rail_1"))
        catalogLocal.replaceChannelsForCategory("rail_1", updatedRail1)
        assertEquals(updatedRail1, catalogLocal.getChannelsByCategory("rail_1", limit = 10))
        assertNotNull(catalogLocal.getChannel(ContentId("m1"))) // still present in rail_2
        assertNull(catalogLocal.getChannel(ContentId("m2")))
        assertNotNull(catalogLocal.getChannel(ContentId("m3")))

        // Deleting a category removes its channels; unrelated rails stay
        catalogLocal.deleteCategoryAndChannels("rail_2")
        assertEquals(null, catalogLocal.getCategory("rail_2"))
        assertEquals(emptyList(), catalogLocal.getChannelsByCategory("rail_2", limit = 10))
        assertEquals(updatedRail1, catalogLocal.getChannelsByCategory("rail_1", limit = 10))
        catalogLocal.deleteLayout("6af4302c6368a4cce6863ca6")
        assertEquals(null, catalogLocal.getLayout("6af4302c6368a4cce6863ca6"))
        assertEquals(emptySet(), catalogLocal.getAllLayoutIds())
    }

    private fun sampleChannel(
        id: String,
        title: String,
        categoryId: String,
        logoUrl: String? = "https://example.com/logo.png",
    ) = Channel(
        id = ContentId(id),
        title = title,
        logoUrl = logoUrl,
        streamUrl = "https://example.com/$id.m3u8",
        categoryId = categoryId,
        categoryName = categoryId.replaceFirstChar { it.uppercase() },
        images = listOf(
            MediaImage(ImageTypes.PORTRAIT, "https://example.com/$id-port.png", 729, 1080),
            MediaImage(ImageTypes.LANDSCAPE, "https://example.com/$id-land.png", 1920, 1080),
        ),
        backdropUrl = "https://example.com/$id-land.png",
        synopsis = "Sample synopsis for $title",
        genres = listOf("Crime", "Thriller"),
        releaseYear = "2026",
        runtimeMinutes = 120,
        parentalRating = "16+",
        imdbRating = 7.8,
        assetType = "movies",
    )
}
