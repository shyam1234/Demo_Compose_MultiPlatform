package com.ott.platform.domain.model

/**
 * Known CMS image types. Any unknown/future CMS imagetype string is still preserved
 * verbatim inside [MediaImage.type] and matched dynamically via [ImageSlot].
 */
object ImageTypes {
    const val LANDSCAPE = "Landscape"
    const val PORTRAIT = "Portrait"
    const val TILE_ARTWORK = "tile_artwork"
    const val LOGO = "Logo"
    const val HERO = "Hero"
    const val BANNER = "Banner"
    const val BACKDROP = "Backdrop"
    const val SQUARE = "Square"
    const val POSTER = "Poster"
    const val THUMBNAIL = "Thumbnail"
    const val RAIL_BACKGROUND_IMAGE = "Rail_Background_Image"
}

/**
 * Extensible artwork slot abstraction.
 * Each slot declares an ordered priority list of CMS imagetypes plus a fallback orientation
 * so newly introduced CMS imagetypes (e.g. "Square", "Banner", "Billboard", "4KPoster")
 * can be matched immediately without rewriting UI components.
 */
sealed interface ImageSlot {
    val preferredTypes: List<String>
    val preferLandscapeFallback: Boolean?
    val fallbackToAny: Boolean get() = true

    data object Hero : ImageSlot {
        override val preferredTypes = listOf(
            ImageTypes.HERO,
            ImageTypes.BANNER,
            ImageTypes.BACKDROP,
            ImageTypes.LANDSCAPE,
            ImageTypes.TILE_ARTWORK,
            ImageTypes.THUMBNAIL,
            ImageTypes.PORTRAIT,
        )
        override val preferLandscapeFallback: Boolean = true
    }

    data object LandscapeCard : ImageSlot {
        override val preferredTypes = listOf(
            ImageTypes.LANDSCAPE,
            ImageTypes.TILE_ARTWORK,
            ImageTypes.BACKDROP,
            ImageTypes.BANNER,
            ImageTypes.THUMBNAIL,
            ImageTypes.HERO,
            ImageTypes.PORTRAIT,
        )
        override val preferLandscapeFallback: Boolean = true
    }

    data object PortraitCard : ImageSlot {
        override val preferredTypes = listOf(
            ImageTypes.PORTRAIT,
            ImageTypes.POSTER,
            ImageTypes.TILE_ARTWORK,
            ImageTypes.LANDSCAPE,
        )
        override val preferLandscapeFallback: Boolean = false
    }

    data object SquareCard : ImageSlot {
        override val preferredTypes = listOf(
            ImageTypes.SQUARE,
            ImageTypes.TILE_ARTWORK,
            ImageTypes.PORTRAIT,
            ImageTypes.LANDSCAPE,
        )
        override val preferLandscapeFallback: Boolean? = null
    }

    data object BannerCard : ImageSlot {
        override val preferredTypes = listOf(
            ImageTypes.BANNER,
            ImageTypes.HERO,
            ImageTypes.LANDSCAPE,
            ImageTypes.TILE_ARTWORK,
            ImageTypes.BACKDROP,
            ImageTypes.PORTRAIT,
        )
        override val preferLandscapeFallback: Boolean = true
    }

    data object DetailsBackdrop : ImageSlot {
        override val preferredTypes = listOf(
            ImageTypes.BACKDROP,
            ImageTypes.LANDSCAPE,
            ImageTypes.HERO,
            ImageTypes.BANNER,
            ImageTypes.TILE_ARTWORK,
            ImageTypes.PORTRAIT,
        )
        override val preferLandscapeFallback: Boolean = true
    }

    data object Logo : ImageSlot {
        override val preferredTypes = listOf(ImageTypes.LOGO)
        override val preferLandscapeFallback: Boolean? = null
        override val fallbackToAny: Boolean = false
    }

    data class Custom(
        override val preferredTypes: List<String>,
        override val preferLandscapeFallback: Boolean? = null,
        override val fallbackToAny: Boolean = true,
    ) : ImageSlot

    companion object {
        /**
         * Resolves the best [ImageSlot] for a CMS rail's `rail_display_type` & `rail_content_type`.
         * If the CMS introduces a brand-new `rail_display_type` (e.g. "Square", "VerticalHero",
         * "Carousel16x9"), it prioritizes an exact `imagetype` match first before falling back.
         */
        fun forRail(
            displayType: String?,
            contentType: RailContentType = RailContentType.Generic,
        ): ImageSlot {
            val raw = displayType.orEmpty().trim()
            val cardStyle = ShelfCardStyle.fromCms(raw, contentType.name)
            val baseSlot = when (cardStyle) {
                ShelfCardStyle.Portrait -> PortraitCard
                ShelfCardStyle.Square -> SquareCard
                ShelfCardStyle.Banner -> BannerCard
                ShelfCardStyle.Landscape -> LandscapeCard
            }
            if (raw.isEmpty()) return baseSlot
            val normalizedRaw = normalizeImageTypeKey(raw)
            val alreadyFirst = baseSlot.preferredTypes.firstOrNull()
                ?.let { normalizeImageTypeKey(it) == normalizedRaw } == true
            return if (alreadyFirst) {
                baseSlot
            } else {
                Custom(
                    preferredTypes = listOf(raw) + baseSlot.preferredTypes,
                    preferLandscapeFallback = baseSlot.preferLandscapeFallback,
                    fallbackToAny = baseSlot.fallbackToAny,
                )
            }
        }
    }
}

fun List<MediaImage>.resolveUrl(
    slot: ImageSlot,
    fallbackUrl: String? = null,
): String? {
    val validImages = filter { it.url.isNotBlank() }
    if (validImages.isEmpty()) return fallbackUrl?.takeIf { it.isNotBlank() }

    val byNormalizedType = validImages.groupBy { normalizeImageTypeKey(it.type) }
    for (preferred in slot.preferredTypes) {
        val hit = byNormalizedType[normalizeImageTypeKey(preferred)]?.firstOrNull()
        if (hit != null) return hit.url
    }

    if (!slot.fallbackToAny) return null

    // Ignore tiny icons/logos when picking a general artwork fallback unless nothing else exists.
    val artworkCandidates = validImages.filterNot {
        normalizeImageTypeKey(it.type) == normalizeImageTypeKey(ImageTypes.LOGO)
    }.ifEmpty { validImages }

    val orientationMatch = when (slot.preferLandscapeFallback) {
        true -> artworkCandidates.firstOrNull { it.width > 0 && it.height > 0 && it.width >= it.height }
        false -> artworkCandidates.firstOrNull { it.width > 0 && it.height > 0 && it.height >= it.width }
        null -> null
    }
    return (orientationMatch ?: artworkCandidates.firstOrNull())?.url
        ?: fallbackUrl?.takeIf { it.isNotBlank() }
}

internal fun normalizeImageTypeKey(raw: String): String =
    raw.trim()
        .lowercase()
        .replace("_", "")
        .replace("-", "")
        .replace(" ", "")
