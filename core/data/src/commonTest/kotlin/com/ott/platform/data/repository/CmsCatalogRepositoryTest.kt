package com.ott.platform.data.repository

import com.ott.platform.data.cms.CmsAuthManager
import com.ott.platform.data.cms.CmsCatalogRemoteDataSource
import com.ott.platform.data.cms.CmsConfig
import com.ott.platform.data.cms.CmsDeviceType
import com.ott.platform.data.cms.platformCmsMenuFallbackDeviceType
import com.ott.platform.database.CachedLayout
import com.ott.platform.database.CatalogLocalDataSource
import com.ott.platform.domain.model.AppMenuItem
import com.ott.platform.domain.model.Category
import com.ott.platform.domain.model.Channel
import com.ott.platform.domain.model.ContentId
import com.ott.platform.domain.model.HomeFeed
import com.ott.platform.domain.model.OttResult
import com.ott.platform.domain.model.RailContentType
import com.ott.platform.domain.model.ShelfCardStyle
import com.ott.platform.logging.OttLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class CmsCatalogRepositoryTest {

    private val noOpLogger = object : OttLogger {
        override fun d(tag: String, message: String) = Unit
        override fun i(tag: String, message: String) = Unit
        override fun w(tag: String, message: String, throwable: Throwable?) = Unit
        override fun e(tag: String, message: String, throwable: Throwable?) = Unit
    }

    @Test
    fun `logs in internally refreshes expired token on 401 and serves offline fallback`() = runTest {
        val local = InMemoryCatalogLocalDataSource()
        var currentTimeMs = 1_000L
        var loginCalls = 0
        var networkCalls = 0
        var isOffline = false
        var expiredFirstBearer = true

        val engine = MockEngine { request ->
            if (isOffline) {
                throw IllegalStateException("No internet connection")
            }
            networkCalls++
            val url = request.url.toString()
            val auth = request.headers[HttpHeaders.Authorization]
            when {
                url.contains("/v1/login") -> {
                    assertEquals(HttpMethod.Post, request.method)
                    loginCalls++
                    respond(
                        content = """{"refresh":"r-$loginCalls","access":"token-$loginCalls"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                url.contains("/v1/menu") -> {
                    if (expiredFirstBearer && auth == "Bearer token-1") {
                        expiredFirstBearer = false
                        respond(
                            content = """{"detail":"token expired"}""",
                            status = HttpStatusCode.Unauthorized,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    } else {
                        assertEquals("Bearer token-$loginCalls", auth)
                        respond(
                            content = """
                                {
                                  "count": 1,
                                  "results": [
                                    {
                                      "_id": "grp1",
                                      "publish_now": 1,
                                      "menuL1": [
                                        {
                                          "menuL1Id": "tv_home",
                                          "menuL1Title": "Home",
                                          "menuL1DeeplinkUrl": "http://layouts/layout_home"
                                        },
                                        {
                                          "menuL1Id": "movies",
                                          "menuL1Title": "Movies",
                                          "menuL1DeeplinkUrl": "layout_movies"
                                        }
                                      ]
                                    }
                                  ]
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
                url.endsWith("/v1/layouts/layout_home") -> {
                    assertEquals("Bearer token-$loginCalls", auth)
                    respond(
                        content = """
                            {
                              "_id": "layout_home",
                              "name": "Home",
                              "theme": { "theme_id": "t1", "theme_url": "https://cms.play-cms.com/theme.png" },
                              "sortrail": ["rail_new", "rail_top10"]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                url.endsWith("/v1/layouts/layout_movies") -> {
                    respond(
                        content = """
                            {
                              "_id": "layout_movies",
                              "name": "Movies",
                              "sortrail": ["rail_top10"]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                url.contains("id=rail_new") -> respond(
                    content = """
                        {
                          "count": 1,
                          "results": [
                            {
                              "_id": "rail_new",
                              "name": "New on Tata Elxsi",
                              "rail_content_type": "new",
                              "rail_display_type": "Landscape",
                              "existing_asset": {
                                "count": 1,
                                "existing_asset": [
                                  {
                                    "asset_id": "asset_rip",
                                    "title": "The RIP",
                                    "short_description": "Miami thriller.",
                                    "runtime": 7200,
                                    "release_date": "2026-01-16T00:00:00.000Z",
                                    "parental_rating": "16+",
                                    "playback_urls": { "hls_url": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" },
                                    "images": [
                                      { "imagetype": "Landscape", "imgurl": "https://cms.play-cms.com/rip.png", "imagewidth": 1920, "imageheight": 1080 }
                                    ]
                                  }
                                ]
                              }
                            }
                          ]
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                url.contains("id=rail_top10") -> respond(
                    content = """
                        {
                          "count": 1,
                          "results": [
                            {
                              "_id": "rail_top10",
                              "name": "Top 10 Today",
                              "rail_content_type": "top_10",
                              "rail_display_type": "Portrait",
                              "existing_asset": {
                                "count": 1,
                                "existing_asset": [
                                  {
                                    "asset_id": "asset_ali",
                                    "title": "Muhammad Ali",
                                    "genres": [{ "genre_id": "g1", "genre_name": "Documentary" }],
                                    "images": [
                                      { "imagetype": "Portrait", "imgurl": "https://cms.play-cms.com/ali.png", "imagewidth": 1000, "imageheight": 1333 }
                                    ]
                                  }
                                ]
                              }
                            }
                          ]
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respond(content = "{}", status = HttpStatusCode.NotFound)
            }
        }

        val config = CmsConfig()
        val client = mockHttpClient(engine)
        val auth = CmsAuthManager(client, noOpLogger, config)
        val remote = CmsCatalogRemoteDataSource(client, auth, noOpLogger, config)

        // 1. Online load: authenticates, retries 401 with token-2, loads menu + layout + rails
        val repo = CmsCatalogRepository(
            remote = remote,
            logger = noOpLogger,
            local = local,
            cacheTtlMs = 60_000L,
            nowMs = { currentTimeMs },
        )

        val menuRes = repo.getMenu()
        assertIs<OttResult.Success<List<AppMenuItem>>>(menuRes)
        assertEquals(listOf("Home", "Movies"), menuRes.data.map { it.title })
        assertEquals(2, loginCalls) // initial login + 401 refresh

        val homeRes = repo.getHomeFeed(limitPerShelf = 10)
        assertIs<OttResult.Success<HomeFeed>>(homeRes)
        val home = homeRes.data
        assertEquals("layout_home", home.layoutId)
        assertEquals("https://cms.play-cms.com/theme.png", home.themeUrl)
        assertEquals(2, home.shelves.size)
        assertEquals(ShelfCardStyle.Landscape, home.shelves[0].cardStyle)
        assertEquals(ShelfCardStyle.Portrait, home.shelves[1].cardStyle)
        assertEquals(RailContentType.Top10, home.shelves[1].contentType)
        // Hero is CMS-only (`rail_content_type=hero/featured`); these rails are ordinary shelves.
        assertEquals(emptyList(), home.heroes)
        assertEquals("The RIP", home.shelves[0].items.single().title)
        assertTrue(local.getChannelCount() == 2L)

        // Dynamic secondary layout switch
        val moviesRes = repo.getLayoutFeed("layout_movies", limitPerShelf = 10)
        assertIs<OttResult.Success<HomeFeed>>(moviesRes)
        assertEquals("Movies", moviesRes.data.layoutName)
        assertEquals(1, moviesRes.data.shelves.size)

        val callsAfterOnline = networkCalls

        // 2. Fresh repo within TTL reads local DB without network
        val freshRepo = CmsCatalogRepository(
            remote = remote,
            logger = noOpLogger,
            local = local,
            cacheTtlMs = 60_000L,
            nowMs = { currentTimeMs + 5_000L },
        )
        val cachedHome = freshRepo.getHomeFeed(limitPerShelf = 10)
        assertIs<OttResult.Success<HomeFeed>>(cachedHome)
        assertEquals(2, cachedHome.data.shelves.size)
        assertEquals(callsAfterOnline, networkCalls)

        // 3. After TTL expires AND network is offline, falls back to stale DB cache
        isOffline = true
        currentTimeMs += 120_000L
        val offlineRepo = CmsCatalogRepository(
            remote = remote,
            logger = noOpLogger,
            local = local,
            cacheTtlMs = 60_000L,
            nowMs = { currentTimeMs },
        )
        val offlineMenu = offlineRepo.getMenu()
        assertIs<OttResult.Success<List<AppMenuItem>>>(offlineMenu)
        assertEquals(2, offlineMenu.data.size)

        val offlineHome = offlineRepo.getHomeFeed(limitPerShelf = 10)
        assertIs<OttResult.Success<HomeFeed>>(offlineHome)
        assertEquals("The RIP", offlineHome.data.shelves.first().items.single().title)

        val offlineSearch = offlineRepo.search("Documentary", limit = 10)
        assertIs<OttResult.Success<List<Channel>>>(offlineSearch)
        assertEquals("Muhammad Ali", offlineSearch.data.single().title)
    }

    @Test
    fun `retries menu with platform fallback when primary is empty or errors`() = runTest {
        val primary = "web"
        val fallback = platformCmsMenuFallbackDeviceType()
        assertTrue(!CmsDeviceType.sameDevice(primary, fallback))

        val requestedDeviceTypes = mutableListOf<String>()
        var loginCalls = 0
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("/v1/login") -> {
                    loginCalls++
                    respond(
                        content = """{"refresh":"r","access":"token-1"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                url.contains("/v1/menu") -> {
                    val deviceType = request.url.parameters["devicetype"].orEmpty()
                    requestedDeviceTypes += deviceType
                    when {
                        CmsDeviceType.sameDevice(deviceType, primary) &&
                            requestedDeviceTypes.count { CmsDeviceType.sameDevice(it, primary) } == 1 -> {
                            // First primary attempt: empty (CMS 207)
                            respond(
                                content = """{"message":"Hamberger Menu not found"}""",
                                status = HttpStatusCode(207, "Multi-Status"),
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                        CmsDeviceType.sameDevice(deviceType, fallback) -> {
                            respond(
                                content = """
                                    {
                                      "count": 1,
                                      "results": [
                                        {
                                          "_id": "grp1",
                                          "publish_now": 1,
                                          "menuL1": [
                                            {
                                              "menuL1Id": "tv_home",
                                              "menuL1Title": "Fallback Home",
                                              "menuL1DeeplinkUrl": "layout_home"
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                """.trimIndent(),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                        else -> {
                            // Second scenario below uses an error for primary.
                            respond(
                                content = """{"detail":"boom"}""",
                                status = HttpStatusCode.InternalServerError,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                    }
                }
                else -> error("Unexpected URL: $url")
            }
        }

        val config = CmsConfig(menuDeviceType = primary, contentDeviceType = primary)
        val client = mockHttpClient(engine)
        val auth = CmsAuthManager(client, noOpLogger, config)
        val remote = CmsCatalogRemoteDataSource(client, auth, noOpLogger, config)
        val repo = CmsCatalogRepository(remote = remote, logger = noOpLogger)

        val emptyThenFallback = repo.getMenu()
        assertIs<OttResult.Success<List<AppMenuItem>>>(emptyThenFallback)
        assertEquals(listOf("Fallback Home"), emptyThenFallback.data.map { it.title })
        assertEquals(fallback, remote.effectiveDeviceType)
        assertEquals(listOf(primary, fallback), requestedDeviceTypes)

        // Error path: new remote so primary fails hard, then fallback succeeds.
        requestedDeviceTypes.clear()
        val errorEngine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("/v1/login") -> {
                    respond(
                        content = """{"refresh":"r","access":"token-1"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                url.contains("/v1/menu") -> {
                    val deviceType = request.url.parameters["devicetype"].orEmpty()
                    requestedDeviceTypes += deviceType
                    if (CmsDeviceType.sameDevice(deviceType, primary)) {
                        respond(
                            content = """{"detail":"boom"}""",
                            status = HttpStatusCode.InternalServerError,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    } else {
                        respond(
                            content = """
                                {
                                  "count": 1,
                                  "results": [
                                    {
                                      "_id": "grp1",
                                      "publish_now": 1,
                                      "menuL1": [
                                        {
                                          "menuL1Id": "tv_home",
                                          "menuL1Title": "Error Fallback Home",
                                          "menuL1DeeplinkUrl": "layout_home"
                                        }
                                      ]
                                    }
                                  ]
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
                else -> error("Unexpected URL: $url")
            }
        }
        val errorClient = mockHttpClient(errorEngine)
        val errorAuth = CmsAuthManager(errorClient, noOpLogger, config)
        val errorRemote = CmsCatalogRemoteDataSource(errorClient, errorAuth, noOpLogger, config)
        val errorRepo = CmsCatalogRepository(remote = errorRemote, logger = noOpLogger)
        val errorThenFallback = errorRepo.getMenu()
        assertIs<OttResult.Success<List<AppMenuItem>>>(errorThenFallback)
        assertEquals(listOf("Error Fallback Home"), errorThenFallback.data.map { it.title })
        assertEquals(fallback, errorRemote.effectiveDeviceType)
        assertEquals(listOf(primary, fallback), requestedDeviceTypes)
        assertTrue(loginCalls >= 1)
    }

    private fun mockHttpClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private class InMemoryCatalogLocalDataSource : CatalogLocalDataSource {
        private var menu: List<AppMenuItem> = emptyList()
        private var menuUpdatedAt: Long? = null
        private val layouts = LinkedHashMap<String, CachedLayout>()
        private val categories = LinkedHashMap<String, Category>()
        private var categoriesUpdatedAt: Long? = null
        private val channelsByCategory = LinkedHashMap<String, List<Channel>>()
        private val categoryUpdatedAt = LinkedHashMap<String, Long>()
        private var time = 1_000L

        override suspend fun getMenuItems(): List<AppMenuItem> = menu
        override suspend fun getMenuUpdatedAtMs(): Long? = menuUpdatedAt
        override suspend fun replaceMenuItems(items: List<AppMenuItem>) {
            menu = items
            menuUpdatedAt = time
        }

        override suspend fun getLayout(layoutId: String): CachedLayout? = layouts[layoutId]
        override suspend fun saveLayout(layout: CachedLayout) {
            layouts[layout.id] = layout.copy(updatedAtMs = time)
        }

        override suspend fun getCategories(): List<Category> = categories.values.toList()
        override suspend fun getCategory(categoryId: String): Category? = categories[categoryId]
        override suspend fun getCategoriesUpdatedAtMs(): Long? = categoriesUpdatedAt
        override suspend fun replaceCategories(categories: List<Category>) {
            this.categories.clear()
            categories.forEach { this.categories[it.id] = it }
            categoriesUpdatedAt = time
        }

        override suspend fun upsertCategory(category: Category, sortOrder: Int) {
            categories[category.id] = category
            if (categoriesUpdatedAt == null) categoriesUpdatedAt = time
        }

        override suspend fun getChannelsByCategory(categoryId: String, limit: Int): List<Channel> =
            channelsByCategory[categoryId].orEmpty().take(limit)

        override suspend fun getCategoryUpdatedAtMs(categoryId: String): Long? =
            categoryUpdatedAt[categoryId]

        override suspend fun replaceChannelsForCategory(categoryId: String, channels: List<Channel>) {
            channelsByCategory[categoryId] = channels
            categoryUpdatedAt[categoryId] = time
        }

        override suspend fun searchChannels(query: String, limit: Int): List<Channel> =
            channelsByCategory.values.flatten()
                .filter {
                    it.title.contains(query, ignoreCase = true) ||
                        it.categoryName.contains(query, ignoreCase = true) ||
                        it.genres.any { g -> g.contains(query, ignoreCase = true) }
                }
                .take(limit)

        override suspend fun getChannel(id: ContentId): Channel? =
            channelsByCategory.values.flatten().firstOrNull { it.id == id }

        override suspend fun getChannelCount(): Long =
            channelsByCategory.values.sumOf { it.size }.toLong()
    }
}
