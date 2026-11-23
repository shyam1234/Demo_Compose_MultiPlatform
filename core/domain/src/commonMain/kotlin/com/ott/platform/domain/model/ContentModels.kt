package com.ott.platform.domain.model

data class ContentId(val value: String)

data class MediaImage(
    val type: String,
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
    val langCode: String = "",
)

data class AppMenuItem(
    val id: String,
    val title: String,
    val layoutId: String?,
    val deeplinkUrl: String = "",
    val deeplinkType: String = "Internal",
    val defaultIconUrl: String? = null,
    val focusedIconUrl: String? = null,
    val selectedIconUrl: String? = null,
    val children: List<AppMenuItem> = emptyList(),
)

data class Category(
    val id: String,
    val name: String,
    val description: String = "",
    val displayType: String = "Landscape",
    val contentType: String = "generic",
    /** CMS `rail_background_image` — drives thematic rail chrome. */
    val hasBackgroundImage: Boolean = false,
    /** CMS `rail_images` URL with imagetype `Rail_Background_Image`. */
    val backgroundImageUrl: String? = null,
    /** CMS rail `devicetype` allow-list (empty = all). */
    val deviceTypes: List<String> = emptyList(),
)

data class Channel(
    val id: ContentId,
    val title: String,
    val logoUrl: String?,
    val streamUrl: String,
    val categoryId: String,
    val categoryName: String,
    val groupTitle: String = categoryName,
    val isGeoBlocked: Boolean = false,
    val isNot247: Boolean = false,
    val quality: String? = null,
    val tvgId: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val images: List<MediaImage> = emptyList(),
    val backdropUrl: String? = null,
    val synopsis: String = "",
    val genres: List<String> = emptyList(),
    val releaseYear: String? = null,
    val runtimeMinutes: Int? = null,
    val parentalRating: String? = null,
    val imdbRating: Double? = null,
    val assetType: String? = null,
    val cast: List<String> = emptyList(),
    val director: String? = null,
    val tags: List<String> = emptyList(),
    val trailerUrl: String? = null,
    val language: String? = null,
) {
    /** True when CMS provided a primary stream URL. */
    val isPlayable: Boolean
        get() = streamUrl.isNotBlank()

    /** True when the player can start (primary stream or trailer preview). */
    val canPlay: Boolean
        get() = streamUrl.isNotBlank() || !trailerUrl.isNullOrBlank()

    fun imageFor(slot: ImageSlot): String? =
        images.resolveUrl(slot = slot, fallbackUrl = backdropUrl ?: logoUrl)

    fun imageForRail(
        rawDisplayType: String?,
        contentType: RailContentType = RailContentType.Generic,
    ): String? {
        val slot = ImageSlot.forRail(displayType = rawDisplayType, contentType = contentType)
        return images.resolveUrl(slot = slot, fallbackUrl = logoUrl ?: backdropUrl)
    }
}

enum class ShelfCardStyle {
    /** 16:9 landscape tiles. */
    Landscape,
    /** 2:3 vertical poster tiles. */
    Portrait,
    /** 1:1 square artwork tiles (for future Square rails). */
    Square,
    /** Wide 21:9 banner/editorial tiles. */
    Banner,
    ;

    companion object {
        fun fromCms(
            displayType: String?,
            contentType: String? = null,
        ): ShelfCardStyle {
            val normalizedDisplay = displayType.orEmpty().trim().lowercase()
            val normalizedContent = contentType.orEmpty().trim().lowercase()
            return when {
                normalizedDisplay in setOf("portrait", "vertical", "poster") -> Portrait
                normalizedDisplay in setOf("square", "1:1") -> Square
                normalizedDisplay in setOf("banner", "hero", "billboard", "editorial_banner") -> Banner
                normalizedDisplay == "landscape" && normalizedContent == "editorial" -> Banner
                else -> Landscape
            }
        }
    }
}

enum class RailContentType {
    Hero,
    Editorial,
    Top10,
    New,
    Mosaic,
    Grid,
    DiscoveryDock,
    Generic,
    ;

    companion object {
        fun fromCms(raw: String?): RailContentType = when (raw.orEmpty().trim().lowercase()) {
            "hero", "featured", "hero_banner", "herobanner" -> Hero
            "editorial" -> Editorial
            "top_10", "top10", "trending_top10" -> Top10
            "new", "new_releases" -> New
            "mosaic" -> Mosaic
            "grid" -> Grid
            "discovery_dock", "discovery-dock", "discoverydock" -> DiscoveryDock
            else -> Generic
        }
    }
}

data class ContentShelf(
    val id: String,
    val title: String,
    val items: List<Channel>,
    val cardStyle: ShelfCardStyle = ShelfCardStyle.Landscape,
    val contentType: RailContentType = RailContentType.Generic,
    val rawDisplayType: String = cardStyle.name,
    val rawContentType: String = contentType.name.lowercase(),
    /** CMS `rail_background_image` — enables thematic rail chrome. */
    val hasBackgroundImage: Boolean = false,
    /** Resolved URL from CMS `rail_images` with imagetype `Rail_Background_Image`. */
    val backgroundImageUrl: String? = null,
    /**
     * CMS discovery-dock tabs (`discovery_dock_rail_response` / `discovery_dock_rail_ids`).
     * Each tab is itself a [ContentShelf] whose title is the subtitle tab label.
     */
    val discoveryDockTabs: List<ContentShelf> = emptyList(),
    /** CMS rail `devicetype` allow-list (empty = all devices). */
    val deviceTypes: List<String> = emptyList(),
) {
    val isDiscoveryDock: Boolean
        get() = contentType == RailContentType.DiscoveryDock || discoveryDockTabs.isNotEmpty()

    /** Thematic chrome is driven by CMS `rail_background_image` (artwork URL is optional). */
    val isThematic: Boolean
        get() = hasBackgroundImage

    /**
     * Hero banner rails are only those CMS explicitly marks as hero/featured,
     * or with a Hero/Billboard display type — never inferred from generic shelves.
     */
    val isHeroRail: Boolean
        get() {
            if (contentType == RailContentType.Hero) return true
            val display = rawDisplayType.trim().lowercase()
            return display in setOf("hero", "billboard", "hero_banner", "herobanner")
        }
}

data class HomeFeed(
    val heroes: List<Channel> = emptyList(),
    val shelves: List<ContentShelf> = emptyList(),
    val layoutId: String = "",
    val layoutName: String = "Home",
    val themeUrl: String? = null,
) {
    /** First featured item — kept for older call sites/tests. */
    val hero: Channel? get() = heroes.firstOrNull()

    constructor(hero: Channel?, shelves: List<ContentShelf>) : this(
        heroes = listOfNotNull(hero),
        shelves = shelves,
    )
}

sealed class OttResult<out T> {
    data class Success<T>(val data: T) : OttResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : OttResult<Nothing>()
    data object Loading : OttResult<Nothing>()
}
