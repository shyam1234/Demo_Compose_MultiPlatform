package com.ott.platform.data.cms

import com.ott.platform.domain.model.ImageSlot
import com.ott.platform.domain.model.ImageTypes
import com.ott.platform.domain.model.RailContentType
import com.ott.platform.domain.model.ShelfCardStyle
import com.ott.platform.network.OttJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CmsCatalogMapperTest {

    @Test
    fun `extractLayoutId handles both http deeplinks and raw layout ids`() {
        assertEquals(
            "6af4302c6368a4cce6863ca6",
            CmsCatalogMapper.extractLayoutId("http://layouts/6af4302c6368a4cce6863ca6"),
        )
        assertEquals(
            "6af4245f6368a4cce6863c73",
            CmsCatalogMapper.extractLayoutId("6af4245f6368a4cce6863c73"),
        )
        assertEquals(
            "6afe8ede2bbe7c443f8803c4",
            CmsCatalogMapper.extractLayoutId("https://cms.play-cms.com/layouts/6afe8ede2bbe7c443f8803c4/"),
        )
        assertNull(CmsCatalogMapper.extractLayoutId("   "))
    }

    @Test
    fun `maps dynamic menu with L1 and L2 children and icons`() {
        val json = """
            {
              "count": 2,
              "results": [
                {
                  "_id": "m1",
                  "menuGroupId": "tv_menu",
                  "publish_now": 1,
                  "menuGroupDeeplinkUrl": "http://layouts/6af4302c6368a4cce6863ca6",
                  "menuL1": [
                    {
                      "menuL1Id": "tv_home",
                      "menuL1Title": "Home",
                      "menuL1DeeplinkUrl": "http://layouts/6af4302c6368a4cce6863ca6",
                      "defaultImageIcon": "/icons/home.png",
                      "menuL2": [
                        {
                          "menuL2Id": "trending",
                          "menuL2Title": "Trending",
                          "menuL2DeeplinkUrl": "6af4245f6368a4cce6863c73"
                        }
                      ]
                    }
                  ]
                },
                {
                  "_id": "m2",
                  "publish_now": 0,
                  "menuL1": [
                    { "menuL1Id": "hidden", "menuL1Title": "Hidden", "menuL1DeeplinkUrl": "x" }
                  ]
                }
              ]
            }
        """.trimIndent()
        val dto = OttJson.instance.decodeFromString<CmsMenuResponseDto>(json)
        val items = CmsCatalogMapper.mapMenu(dto)

        assertEquals(1, items.size)
        val home = items.single()
        assertEquals("Home", home.title)
        assertEquals("6af4302c6368a4cce6863ca6", home.layoutId)
        assertEquals("https://cms.play-cms.com/icons/home.png", home.defaultIconUrl)
        assertEquals(1, home.children.size)
        assertEquals("6af4245f6368a4cce6863c73", home.children.single().layoutId)
    }

    @Test
    fun `maps layout prioritizing sortrail and theme url`() {
        val json = """
            {
              "_id": "6af4302c6368a4cce6863ca6",
              "name": "Home",
              "theme": {
                "theme_id": "t1",
                "theme_url": "https://cms.play-cms.com/v1/imageresize/theme.png"
              },
              "rail_id": ["r1", "r2"],
              "sortrail": ["r2", "r1", "r2"]
            }
        """.trimIndent()
        val dto = OttJson.instance.decodeFromString<CmsLayoutResponseDto>(json)
        val layout = CmsCatalogMapper.mapLayout(dto, fallbackLayoutId = "fallback")

        assertEquals("6af4302c6368a4cce6863ca6", layout.id)
        assertEquals("Home", layout.name)
        assertEquals("https://cms.play-cms.com/v1/imageresize/theme.png", layout.themeUrl)
        assertEquals(listOf("r2", "r1"), layout.railIds)
    }

    @Test
    fun `maps movie and tv show rail payloads with polymorphic fields and future imagetypes`() {
        val railJson = """
            {
              "count": 1,
              "results": [
                {
                  "_id": "66ea7796ebdca80012ede850",
                  "name": "Top 10 Today",
                  "rail_content_type": "top_10",
                  "rail_display_type": "Portrait",
                  "publish_now": 1,
                  "existing_asset": {
                    "count": 2,
                    "existing_asset": [
                      {
                        "asset_id": "movie_1",
                        "title": "The RIP",
                        "long_description": "Crime thriller feature.",
                        "runtime": 7200,
                        "release_date": "2026-01-16T00:00:00.000Z",
                        "parental_rating": "16+",
                        "imdb_rating": null,
                        "asset_type": "movies",
                        "is_hd": 1,
                        "Dolby": 1,
                        "genres": [
                          { "genre_id": "g1", "genre_name": "Crime" },
                          { "genre_id": "g2", "genre_name": "Thriller" }
                        ],
                        "playback_urls": {
                          "hls_url": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                        },
                        "images": [
                          {
                            "imagetype": "Portrait",
                            "imgurl": "https://cms.play-cms.com/portrait.png",
                            "imagewidth": 729,
                            "imageheight": 1080
                          },
                          {
                            "imagetype": "Landscape",
                            "imgurl": "https://cms.play-cms.com/landscape.png",
                            "imagewidth": 1920,
                            "imageheight": 1080
                          },
                          {
                            "imagetype": "Square",
                            "imgurl": "https://cms.play-cms.com/square.png",
                            "imagewidth": 800,
                            "imageheight": 800
                          }
                        ]
                      },
                      {
                        "asset_id": "show_1",
                        "show_id": "1795077199780",
                        "name": {},
                        "title": "Beyond Earth Colony",
                        "short_description": "Distant planet colony.",
                        "runtime": 0,
                        "imdb_rating": 8,
                        "assetType": "shows",
                        "is_hd": "0",
                        "genres": [
                          { "genre_id": "g3", "genre_name": "Sci-Fi" }
                        ],
                        "tags": ["ISG# 2025"],
                        "images": [
                          {
                            "imagetype": "Landscape",
                            "imageurl": "/mount/images/colony.jpeg",
                            "imagewidth": 1920,
                            "imageheight": 1080
                          }
                        ]
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val wrapper = OttJson.instance.decodeFromString<CmsRailsResponseDto>(railJson)
        val rail = CmsCatalogMapper.mapRail(wrapper.results.single(), fallbackRailId = "fallback")

        assertEquals("Top 10 Today", rail.title)
        assertEquals(ShelfCardStyle.Portrait, rail.cardStyle)
        assertEquals(RailContentType.Top10, rail.contentType)
        assertEquals(2, rail.items.size)

        val movie = rail.items[0]
        assertEquals("movie_1", movie.id.value)
        assertEquals("The RIP", movie.title)
        assertEquals("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", movie.streamUrl)
        assertTrue(movie.isPlayable)
        assertEquals("2026", movie.releaseYear)
        assertEquals(120, movie.runtimeMinutes)
        assertEquals("16+", movie.parentalRating)
        assertEquals("HD · Dolby", movie.quality)
        assertEquals(listOf("Crime", "Thriller"), movie.genres)
        assertEquals("https://cms.play-cms.com/portrait.png", movie.imageFor(ImageSlot.PortraitCard))
        assertEquals("https://cms.play-cms.com/landscape.png", movie.imageFor(ImageSlot.Hero))
        assertEquals("https://cms.play-cms.com/square.png", movie.imageForRail("Square"))

        val show = rail.items[1]
        assertEquals("show_1", show.id.value)
        assertEquals(8.0, show.imdbRating)
        assertEquals("shows", show.assetType)
        assertNotNull(show.logoUrl)
        assertTrue(show.logoUrl!!.startsWith("https://cms.play-cms.com/mount/images/colony.jpeg"))
        assertEquals(ImageTypes.LANDSCAPE, show.images.single().type)
    }

    @Test
    fun `maps thematic rail background image from CMS rail_images`() {
        val railJson = """
            {
              "count": 1,
              "results": [
                {
                  "_id": "thematic_1",
                  "name": "Premier League Highlights",
                  "rail_content_type": "new",
                  "rail_display_type": "Landscape",
                  "rail_background_image": true,
                  "publish_now": 1,
                  "rail_images": [
                    {
                      "imagetype": "Rail_Background_Image",
                      "imageurl": "/mount/images/rail-bg.png",
                      "imagewidth": 1840,
                      "imageheight": 346
                    }
                  ],
                  "existing_asset": {
                    "count": 1,
                    "existing_asset": [
                      {
                        "asset_id": "pl_1",
                        "title": "Arsenal Vs Manchester City",
                        "publish_now": 1,
                        "playback_urls": { "hls_url": "" },
                        "images": [
                          {
                            "imagetype": "Landscape",
                            "imgurl": "https://cms.play-cms.com/match.png",
                            "imagewidth": 1920,
                            "imageheight": 1080
                          }
                        ]
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        val rail = CmsCatalogMapper.mapRail(
            OttJson.instance.decodeFromString<CmsRailsResponseDto>(railJson).results.single(),
            fallbackRailId = "fallback",
        )
        val shelf = rail.toContentShelf()

        assertTrue(rail.hasBackgroundImage)
        assertEquals(
            "https://cms.play-cms.com/v1/imageresize/w_1920/h_400/original/%2Fmount%2Fimages%2Frail-bg.png",
            rail.backgroundImageUrl,
        )
        assertTrue(shelf.isThematic)
        assertEquals(1, shelf.items.size)

        // Flag alone enables thematic chrome even if artwork URL is absent.
        val flagOnlyJson = """
            {
              "count": 1,
              "results": [{
                "_id": "thematic_flag_only",
                "name": "On Air",
                "rail_content_type": "generic",
                "rail_display_type": "Landscape",
                "rail_background_image": true,
                "publish_now": 1,
                "rail_images": [],
                "existing_asset": { "count": 0, "existing_asset": [] }
              }]
            }
        """.trimIndent()
        val flagOnly = CmsCatalogMapper.mapRail(
            OttJson.instance.decodeFromString<CmsRailsResponseDto>(flagOnlyJson).results.single(),
            fallbackRailId = "fallback",
        )
        assertTrue(flagOnly.hasBackgroundImage)
        assertTrue(flagOnly.toContentShelf().isThematic)
    }

    @Test
    fun `maps discovery_dock tabs from nested rail response`() {
        val railJson = """
            {
              "count": 1,
              "results": [
                {
                  "_id": "dock_1",
                  "name": "Enjoy the Game",
                  "rail_content_type": "discovery_dock",
                  "rail_display_type": "Landscape",
                  "rail_background_image": false,
                  "publish_now": 1,
                  "discovery_dock_rail_ids": ["tab_a", "tab_b"],
                  "discovery_dock_rail_response": [
                    {
                      "_id": "tab_a",
                      "name": "FIFA World Cup Highlights",
                      "rail_content_type": "editorial",
                      "rail_display_type": "Portrait",
                      "publish_now": 1,
                      "existing_asset": {
                        "count": 1,
                        "existing_asset": [
                          {
                            "asset_id": "fifa_1",
                            "title": "Spain Vs Argentina",
                            "publish_now": 1,
                            "images": [
                              {
                                "imagetype": "Portrait",
                                "imgurl": "https://cms.play-cms.com/fifa.png",
                                "imagewidth": 729,
                                "imageheight": 1080
                              }
                            ]
                          }
                        ]
                      }
                    },
                    {
                      "_id": "tab_b",
                      "name": "UEFA Nations League Highlights",
                      "rail_content_type": "editorial",
                      "rail_display_type": "Landscape",
                      "publish_now": 1,
                      "existing_asset": {
                        "count": 1,
                        "existing_asset": [
                          {
                            "asset_id": "uefa_1",
                            "title": "Spain Vs England",
                            "publish_now": 1,
                            "playback_urls": {
                              "hls_url": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                            },
                            "images": [
                              {
                                "imagetype": "Landscape",
                                "imgurl": "https://cms.play-cms.com/uefa.png",
                                "imagewidth": 1920,
                                "imageheight": 1080
                              }
                            ]
                          }
                        ]
                      }
                    }
                  ],
                  "existing_asset": { "count": 0, "existing_asset": [] }
                }
              ]
            }
        """.trimIndent()
        val rail = CmsCatalogMapper.mapRail(
            OttJson.instance.decodeFromString<CmsRailsResponseDto>(railJson).results.single(),
            fallbackRailId = "fallback",
        )
        val shelf = rail.toContentShelf()

        assertEquals(RailContentType.DiscoveryDock, rail.contentType)
        assertTrue(shelf.isDiscoveryDock)
        assertEquals(2, shelf.discoveryDockTabs.size)
        assertEquals("FIFA World Cup Highlights", shelf.discoveryDockTabs[0].title)
        assertEquals("UEFA Nations League Highlights", shelf.discoveryDockTabs[1].title)
        assertEquals(ShelfCardStyle.Portrait, shelf.discoveryDockTabs[0].cardStyle)
        assertTrue(shelf.discoveryDockTabs[1].items.single().canPlay)
    }

    @Test
    fun `mapMenu trusts Menu API payload and skips unpublished groups only`() {
        val menuJson = """
            {
              "count": 2,
              "results": [
                {
                  "_id": "web_nav",
                  "publish_now": 1,
                  "devicetype": ["web"],
                  "menuGroupDeeplinkUrl": "http://layouts/web_home",
                  "menuL1": [
                    { "menuL1Id": "home", "menuL1Title": "HOME", "menuL1DeeplinkUrl": "6af4302c6368a4cce6863ca6" },
                    { "menuL1Id": "movies", "menuL1Title": "MOVIES", "menuL1DeeplinkUrl": "6af4245f6368a4cce6863c73" },
                    { "menuL1Id": "shows", "menuL1Title": "TV SHOWS", "menuL1DeeplinkUrl": "6afe8ede2bbe7c443f8803c4" }
                  ]
                },
                {
                  "_id": "hidden",
                  "publish_now": 0,
                  "menuL1": [
                    { "menuL1Id": "x", "menuL1Title": "Hidden", "menuL1DeeplinkUrl": "x" }
                  ]
                }
              ]
            }
        """.trimIndent()
        val menuDto = OttJson.instance.decodeFromString<CmsMenuResponseDto>(menuJson)
        val menu = CmsCatalogMapper.mapMenu(menuDto, deviceType = "web")
        assertEquals(listOf("HOME", "MOVIES", "TV SHOWS"), menu.map { it.title })
        assertEquals("6af4302c6368a4cce6863ca6", menu[0].layoutId)
        assertEquals("6af4245f6368a4cce6863c73", menu[1].layoutId)
        assertEquals("6afe8ede2bbe7c443f8803c4", menu[2].layoutId)

        assertTrue(CmsDeviceType.matches(listOf("web"), "web"))
        assertTrue(!CmsDeviceType.matches(listOf("androidtv", "atv_ott"), "web"))
        assertTrue(CmsDeviceType.matches(listOf("All"), "web"))
        assertTrue(CmsDeviceType.matches(emptyList(), "web"))
        assertEquals(CmsDeviceType.ANDROID_MOBILE, CmsDeviceType.menuFallbackDeviceType(isPhone = true))
        assertEquals(CmsDeviceType.ANDROID_TV, CmsDeviceType.menuFallbackDeviceType(isPhone = false))
        assertEquals(
            listOf(CmsDeviceType.ANDROID_MOBILE, CmsDeviceType.ANDROID_TV),
            CmsDeviceType.menuDeviceTypeCandidates(CmsDeviceType.ANDROID_MOBILE, isPhone = true),
        )
        assertEquals(
            listOf("ios", CmsDeviceType.ANDROID_MOBILE, CmsDeviceType.ANDROID_TV),
            CmsDeviceType.menuDeviceTypeCandidates("ios", isPhone = true),
        )
        assertEquals(
            listOf("web", CmsDeviceType.ANDROID_TV),
            CmsDeviceType.menuDeviceTypeCandidates("web", isPhone = false),
        )
        assertTrue(CmsDeviceType.matches(listOf("android"), CmsDeviceType.ANDROID_MOBILE))
        assertTrue(CmsDeviceType.matches(listOf(CmsDeviceType.ANDROID_MOBILE), "android"))
    }
}
