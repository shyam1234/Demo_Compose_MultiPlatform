package com.ott.platform.data.cms

import com.ott.platform.domain.model.AppMenuItem
import com.ott.platform.domain.model.Channel
import com.ott.platform.domain.model.ContentId
import com.ott.platform.domain.model.ContentShelf
import com.ott.platform.domain.model.ImageSlot
import com.ott.platform.domain.model.ImageTypes
import com.ott.platform.domain.model.MediaImage
import com.ott.platform.domain.model.RailContentType
import com.ott.platform.domain.model.ShelfCardStyle
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

data class CmsLayout(
    val id: String,
    val name: String,
    val themeUrl: String?,
    val railIds: List<String>,
    val deviceTypes: List<String> = emptyList(),
)

data class CmsRail(
    val id: String,
    val title: String,
    val cardStyle: ShelfCardStyle,
    val contentType: RailContentType,
    val rawDisplayType: String,
    val rawContentType: String,
    val items: List<Channel>,
    val hasBackgroundImage: Boolean = false,
    val backgroundImageUrl: String? = null,
    val backgroundImageAspectRatio: Float? = null,
    val discoveryDockTabs: List<CmsRail> = emptyList(),
    val discoveryDockRailIds: List<String> = emptyList(),
    val deviceTypes: List<String> = emptyList(),
) {
    fun toContentShelf(limit: Int = items.size): ContentShelf = ContentShelf(
        id = id,
        title = title,
        items = items.take(limit),
        cardStyle = cardStyle,
        contentType = contentType,
        rawDisplayType = rawDisplayType,
        rawContentType = rawContentType,
        hasBackgroundImage = hasBackgroundImage,
        backgroundImageUrl = backgroundImageUrl,
        backgroundImageAspectRatio = backgroundImageAspectRatio,
        discoveryDockTabs = discoveryDockTabs.map { it.toContentShelf(limit = it.items.size) },
        deviceTypes = deviceTypes,
    )
}

object CmsCatalogMapper {

    fun extractLayoutId(deeplinkUrl: String?): String? {
        val trimmed = deeplinkUrl.orEmpty().trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val candidate = if (trimmed.contains('/')) {
            trimmed.substringAfterLast('/')
        } else {
            trimmed
        }
        return candidate.takeIf { it.isNotBlank() }
    }

    /**
     * Maps the CMS Menu API payload as-is.
     *
     * Device scoping is owned by `GET /v1/menu?devicetype=…` — this mapper does **not**
     * re-filter groups client-side. Only unpublished groups (`publish_now == 0`) are skipped.
     * Titles, icons, deeplinks, and L2 children come exclusively from the response.
     */
    fun mapMenu(
        response: CmsMenuResponseDto,
        baseUrl: String = CmsConfig.DEFAULT_BASE_URL,
        @Suppress("UNUSED_PARAMETER") deviceType: String = platformCmsMenuDeviceType(),
    ): List<AppMenuItem> {
        val groups = response.results.filter { it.publishNow != 0 }
        return groups.flatMap { group ->
            group.menuL1.mapNotNull { l1 ->
                val title = l1.menuL1Title.trim()
                if (title.isEmpty()) return@mapNotNull null
                val children = l1.menuL2.mapNotNull { l2 ->
                    val childTitle = l2.menuL2Title.trim()
                    if (childTitle.isEmpty()) return@mapNotNull null
                    AppMenuItem(
                        id = l2.menuL2Id.ifBlank { childTitle.lowercase().replace(" ", "_") },
                        title = childTitle,
                        layoutId = extractLayoutId(l2.menuL2DeeplinkUrl),
                        deeplinkUrl = l2.menuL2DeeplinkUrl,
                        deeplinkType = l2.deeplinkType,
                        defaultIconUrl = normalizeAssetUrl(l2.defaultImageIcon, baseUrl),
                        focusedIconUrl = normalizeAssetUrl(l2.focusedImageIcon, baseUrl),
                        selectedIconUrl = normalizeAssetUrl(l2.selectedImageIcon, baseUrl),
                    )
                }
                // Prefer the L1 deeplink; fall back to the menu-group deeplink only when L1 is blank.
                val layoutId = extractLayoutId(l1.menuL1DeeplinkUrl)
                    ?: extractLayoutId(group.menuGroupDeeplinkUrl)
                AppMenuItem(
                    id = l1.menuL1Id.ifBlank { title.lowercase().replace(" ", "_") },
                    title = title,
                    layoutId = layoutId,
                    deeplinkUrl = l1.menuL1DeeplinkUrl.ifBlank { group.menuGroupDeeplinkUrl },
                    deeplinkType = l1.deeplinkType,
                    defaultIconUrl = normalizeAssetUrl(l1.defaultImageIcon, baseUrl),
                    focusedIconUrl = normalizeAssetUrl(l1.focusedImageIcon, baseUrl),
                    selectedIconUrl = normalizeAssetUrl(l1.selectedImageIcon, baseUrl),
                    children = children,
                )
            }
        }
    }

    fun mapLayout(
        dto: CmsLayoutResponseDto,
        fallbackLayoutId: String,
        baseUrl: String = CmsConfig.DEFAULT_BASE_URL,
    ): CmsLayout {
        val orderedRails = dto.sortrail.ifEmpty { dto.railIds }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return CmsLayout(
            id = dto.id.ifBlank { fallbackLayoutId },
            name = dto.name.trim().ifEmpty { "Home" },
            themeUrl = normalizeAssetUrl(dto.theme?.themeUrl, baseUrl),
            railIds = orderedRails,
            deviceTypes = dto.deviceType,
        )
    }

    fun mapRail(
        dto: CmsRailDto,
        fallbackRailId: String,
        baseUrl: String = CmsConfig.DEFAULT_BASE_URL,
        mapDiscoveryDockChildren: Boolean = true,
        deviceType: String = platformCmsMenuDeviceType(),
        fallbackStreamUrl: String = CmsConfig.DEFAULT_PLAYBACK_URL,
    ): CmsRail {
        val railId = dto.id.ifBlank { fallbackRailId }
        val railTitle = dto.name.trim().ifEmpty { "Featured" }
        val rawDisplayType = dto.railDisplayType.trim().ifEmpty { "Landscape" }
        val rawContentType = dto.railContentType.trim().ifEmpty { "generic" }
        val contentType = RailContentType.fromCms(rawContentType)
        val cardStyle = ShelfCardStyle.fromCms(rawDisplayType, rawContentType)
        val deviceTypes = dto.deviceType
        val assets = dto.existingAsset?.assets.orEmpty()
            .filter { it.publishNow != 0 }
            .map { asset ->
                mapAsset(
                    dto = asset,
                    railId = railId,
                    railTitle = railTitle,
                    rawDisplayType = rawDisplayType,
                    contentType = contentType,
                    baseUrl = baseUrl,
                    deviceType = deviceType,
                    fallbackStreamUrl = fallbackStreamUrl,
                )
            }
        val backgroundImages = CmsDeviceType.filterImagesForDevice(dto.railImages, deviceType)
        val backgroundArt = resolveRailBackgroundArt(backgroundImages, baseUrl)
        val backgroundImageUrl = backgroundArt?.url
        val backgroundImageAspectRatio = backgroundArt?.aspectRatio
        // CMS boolean is authoritative; artwork may 502 / be delayed but chrome must still apply.
        val hasBackgroundImage = dto.railBackgroundImage
        val dockIds = dto.discoveryDockRailIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val dockTabs = if (mapDiscoveryDockChildren) {
            val fromResponse = dto.discoveryDockRailResponse
                .filter { it.publishNow != 0 }
                .filter { CmsDeviceType.matches(it.deviceType, deviceType) }
                .map { child ->
                    mapRail(
                        dto = child,
                        fallbackRailId = child.id.ifBlank { child.railCode },
                        baseUrl = baseUrl,
                        mapDiscoveryDockChildren = false,
                        deviceType = deviceType,
                        fallbackStreamUrl = fallbackStreamUrl,
                    )
                }
            if (fromResponse.isNotEmpty()) {
                fromResponse
            } else {
                dockIds.map { id ->
                    CmsRail(
                        id = id,
                        title = id,
                        cardStyle = ShelfCardStyle.Landscape,
                        contentType = RailContentType.Generic,
                        rawDisplayType = "Landscape",
                        rawContentType = "generic",
                        items = emptyList(),
                    )
                }
            }
        } else {
            emptyList()
        }
        return CmsRail(
            id = railId,
            title = railTitle,
            cardStyle = cardStyle,
            contentType = contentType,
            rawDisplayType = rawDisplayType,
            rawContentType = rawContentType,
            items = assets,
            hasBackgroundImage = hasBackgroundImage,
            backgroundImageUrl = backgroundImageUrl,
            backgroundImageAspectRatio = backgroundImageAspectRatio,
            discoveryDockTabs = dockTabs,
            discoveryDockRailIds = dockIds,
            deviceTypes = deviceTypes,
        )
    }

    data class RailBackgroundArt(
        val url: String,
        /** width / height from CMS `imagewidth` / `imageheight`, when both > 0. */
        val aspectRatio: Float? = null,
    )

    fun resolveRailBackgroundImageUrl(
        images: List<CmsAssetImageDto>,
        baseUrl: String = CmsConfig.DEFAULT_BASE_URL,
    ): String? = resolveRailBackgroundArt(images, baseUrl)?.url

    fun resolveRailBackgroundArt(
        images: List<CmsAssetImageDto>,
        baseUrl: String = CmsConfig.DEFAULT_BASE_URL,
    ): RailBackgroundArt? {
        if (images.isEmpty()) return null
        val preferredType = normalizeImageType(ImageTypes.RAIL_BACKGROUND_IMAGE)
        val ordered = images.sortedByDescending { img ->
            normalizeImageType(img.imagetype) == preferredType
        }
        for (img in ordered) {
            val url = normalizeRailBackgroundUrl(img.imgurl, baseUrl)
                ?: normalizeRailBackgroundUrl(img.imageurl, baseUrl)
            if (url != null) {
                val aspect = if (img.imagewidth > 0 && img.imageheight > 0) {
                    img.imagewidth.toFloat() / img.imageheight.toFloat()
                } else {
                    null
                }
                return RailBackgroundArt(url = url, aspectRatio = aspect)
            }
        }
        return null
    }

    /**
     * Rail backgrounds often live under `/mount/...` and 502 when requested directly.
     * Prefer Play-CMS `/v1/imageresize` (same path used by customer branding assets).
     */
    internal fun normalizeRailBackgroundUrl(
        rawUrl: String?,
        baseUrl: String,
        width: Int = 1920,
        height: Int = 400,
    ): String? {
        val trimmed = rawUrl.orEmpty().trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.contains(".svg", ignoreCase = true)) return null

        val mountPath = when {
            trimmed.contains("/mount/") -> {
                val idx = trimmed.indexOf("/mount/")
                trimmed.substring(idx).substringBefore('?')
            }
            trimmed.startsWith("/") -> trimmed.substringBefore('?')
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> {
                // Already absolute non-mount CDN URL — use as-is.
                return trimmed.replace(" ", "%20")
            }
            else -> "/$trimmed".substringBefore('?')
        }

        val encodedPath = mountPath
            .split('/')
            .filter { it.isNotEmpty() }
            .joinToString(separator = "%2F", prefix = "%2F") { segment ->
                segment.replace(" ", "%20")
            }
        return "${baseUrl.trimEnd('/')}/v1/imageresize/w_${width}/h_${height}/original/$encodedPath"
    }

    private fun normalizeImageType(raw: String): String =
        raw.trim().lowercase().replace(Regex("[\\s_\\-]+"), "")

    fun mapAsset(
        dto: CmsAssetDto,
        railId: String,
        railTitle: String,
        rawDisplayType: String,
        contentType: RailContentType,
        baseUrl: String = CmsConfig.DEFAULT_BASE_URL,
        deviceType: String = platformCmsMenuDeviceType(),
        fallbackStreamUrl: String = CmsConfig.DEFAULT_PLAYBACK_URL,
    ): Channel {
        val fallbackName = (dto.name as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty().trim()
        val title = dto.title.trim()
            .ifEmpty { fallbackName }
            .ifEmpty { dto.regionalTitle.trim() }
            .ifEmpty { "Untitled" }

        val idRaw = dto.assetId.trim()
            .ifEmpty { dto.showId.trim() }
            .ifEmpty { dto.contentId.trim() }
            .ifEmpty { dto.externalId.trim() }
            .ifEmpty { "${railId}:${title.lowercase().replace(Regex("[^a-z0-9]+"), "-")}" }

        val mappedImages = CmsDeviceType.filterImagesForDevice(dto.images, deviceType).mapNotNull { img ->
            val resolved = normalizeAssetUrl(img.imgurl, baseUrl)
                ?: normalizeAssetUrl(img.imageurl, baseUrl)
                ?: return@mapNotNull null
            MediaImage(
                type = img.imagetype.trim().ifEmpty { "Landscape" },
                url = resolved,
                width = img.imagewidth,
                height = img.imageheight,
                langCode = img.langCode,
            )
        }

        val railSlot = ImageSlot.forRail(rawDisplayType, contentType)
        val primaryCardUrl = mappedImages.firstOrNull()?.let {
            with(Channel(ContentId(idRaw), title, null, "", railId, railTitle, images = mappedImages)) {
                imageFor(railSlot)
            }
        }
        val backdropUrl = Channel(
            id = ContentId(idRaw),
            title = title,
            logoUrl = primaryCardUrl,
            streamUrl = "",
            categoryId = railId,
            categoryName = railTitle,
            images = mappedImages,
        ).imageFor(ImageSlot.DetailsBackdrop)

        val streamUrl = firstNonBlank(
            dto.playbackUrls?.hlsUrl,
            dto.hlsUrl,
            dto.playbackUrls?.dashUrl,
            dto.dashUrl,
            dto.playbackUrls?.smoothUrl,
            dto.smoothUrl,
            dto.channelUrl,
        ).orEmpty().ifBlank { fallbackStreamUrl.trim() }

        val trailerUrl = firstNonBlank(
            dto.trailerUrls?.hlsUrl,
            dto.previewHlsUrl,
            dto.trailerUrls?.dashUrl,
            dto.previewDashUrl,
        )

        val synopsis = firstNonBlank(
            dto.longDescription,
            dto.synopsis,
            dto.shortDescription,
            dto.description,
        ).orEmpty()

        val year = dto.releaseDate.trim().take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }
        val runtimeMinutes = if (dto.runtime > 0) (dto.runtime / 60).coerceAtLeast(1) else null
        val isHd = dto.isHd?.content?.let { it == "1" || it.equals("true", ignoreCase = true) } == true
        val hasDolby = (dto.dolby?.content ?: dto.dolbyUpper?.content)
            ?.let { it == "1" || it.equals("true", ignoreCase = true) } == true
        val quality = listOfNotNull(
            if (isHd) "HD" else null,
            if (hasDolby) "Dolby" else null,
        ).joinToString(" · ").takeIf { it.isNotEmpty() }

        return Channel(
            id = ContentId(idRaw),
            title = title,
            logoUrl = primaryCardUrl,
            streamUrl = streamUrl,
            categoryId = railId,
            categoryName = railTitle,
            groupTitle = railTitle,
            quality = quality,
            images = mappedImages,
            backdropUrl = backdropUrl,
            synopsis = synopsis,
            genres = dto.genres.mapNotNull { it.genreName.trim().takeIf(String::isNotEmpty) },
            releaseYear = year,
            runtimeMinutes = runtimeMinutes,
            parentalRating = dto.parentalRating.trim().takeIf { it.isNotEmpty() },
            imdbRating = dto.imdbRating?.takeIf { it > 0.0 },
            assetType = firstNonBlank(dto.assetTypeSnake, dto.assetType, dto.contentType),
            cast = dto.cast.mapNotNull { it.name.trim().takeIf(String::isNotEmpty) },
            director = dto.director.trim().takeIf { it.isNotEmpty() },
            tags = dto.tags.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
            trailerUrl = trailerUrl,
            language = dto.originalLanguage.trim().takeIf { it.isNotEmpty() }
                ?: dto.availableLanguage.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    internal fun normalizeAssetUrl(rawUrl: String?, baseUrl: String): String? {
        val trimmed = rawUrl.orEmpty().trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.contains(".svg", ignoreCase = true)) return null
        val absolute = when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("/") -> "${baseUrl.trimEnd('/')}$trimmed"
            else -> "${baseUrl.trimEnd('/')}/$trimmed"
        }
        return absolute.replace(" ", "%20")
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()
}
