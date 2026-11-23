package com.ott.platform.data.cms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class CmsLoginRequestDto(
    val username: String,
    val password: String,
)

@Serializable
data class CmsLoginResponseDto(
    val refresh: String = "",
    val access: String = "",
)

@Serializable
data class CmsMenuResponseDto(
    val count: Int? = null,
    val results: List<CmsMenuGroupDto> = emptyList(),
)

@Serializable
data class CmsMenuGroupDto(
    @SerialName("_id") val id: String = "",
    val menuGroupId: String = "",
    @SerialName("publish_now") val publishNow: Int = 1,
    val menuGroupDeeplinkUrl: String = "",
    val menuGroupTitle: String = "",
    @SerialName("devicetype") val deviceType: List<String> = emptyList(),
    val menuL1: List<CmsMenuL1Dto> = emptyList(),
)

@Serializable
data class CmsMenuL1Dto(
    val menuL1Id: String = "",
    val menuL1Title: String = "",
    val menuL1DeeplinkUrl: String = "",
    val defaultImageIcon: String = "",
    val focusedImageIcon: String = "",
    val selectedImageIcon: String = "",
    val disabledImageIcon: String = "",
    @SerialName("deeplink_type") val deeplinkType: String = "Internal",
    val menuL2: List<CmsMenuL2Dto> = emptyList(),
)

@Serializable
data class CmsMenuL2Dto(
    val menuL2Id: String = "",
    val menuL2Title: String = "",
    val menuL2DeeplinkUrl: String = "",
    val defaultImageIcon: String = "",
    val focusedImageIcon: String = "",
    val selectedImageIcon: String = "",
    val disabledImageIcon: String = "",
    @SerialName("deeplink_type") val deeplinkType: String = "Internal",
)

@Serializable
data class CmsLayoutResponseDto(
    @SerialName("_id") val id: String = "",
    val name: String = "",
    val theme: CmsLayoutThemeDto? = null,
    @SerialName("rail_id") val railIds: List<String> = emptyList(),
    val sortrail: List<String> = emptyList(),
    @SerialName("layout_type") val layoutType: String = "",
    @SerialName("selected_icon") val selectedIcon: String = "",
    @SerialName("default_icon") val defaultIcon: String = "",
    @SerialName("devicetype") val deviceType: List<String> = emptyList(),
)

@Serializable
data class CmsLayoutThemeDto(
    @SerialName("theme_id") val themeId: String = "",
    @SerialName("theme_url") val themeUrl: String = "",
)

@Serializable
data class CmsRailsResponseDto(
    val count: Int? = null,
    val results: List<CmsRailDto> = emptyList(),
)

@Serializable
data class CmsRailDto(
    @SerialName("_id") val id: String = "",
    @SerialName("rail_id") val railCode: String = "",
    val name: String = "",
    @SerialName("rail_content_type") val railContentType: String = "generic",
    @SerialName("rail_type") val railType: String = "",
    @SerialName("rail_display_type") val railDisplayType: String = "Landscape",
    @SerialName("existing_asset_type") val existingAssetType: List<String> = emptyList(),
    @SerialName("total_asset_count") val totalAssetCount: Int = 0,
    @SerialName("publish_now") val publishNow: Int = 1,
    @SerialName("existing_asset") val existingAsset: CmsExistingAssetPageDto? = null,
    @SerialName("rail_images") val railImages: List<CmsAssetImageDto> = emptyList(),
    /** When true, render the thematic rail chrome using [railImages] background artwork. */
    @SerialName("rail_background_image") val railBackgroundImage: Boolean = false,
    /** Ordered linked sub-rail IDs for `rail_content_type = discovery_dock`. */
    @SerialName("discovery_dock_rail_ids") val discoveryDockRailIds: List<String> = emptyList(),
    /** Nested rail payloads for discovery-dock tabs (may omit assets; fetch by id if empty). */
    @SerialName("discovery_dock_rail_response") val discoveryDockRailResponse: List<CmsRailDto> = emptyList(),
    @SerialName("is_support_playback") val isSupportPlayback: Boolean = false,
    @SerialName("devicetype") val deviceType: List<String> = emptyList(),
)

@Serializable
data class CmsExistingAssetPageDto(
    val count: Int = 0,
    @SerialName("existing_asset") val assets: List<CmsAssetDto> = emptyList(),
)

@Serializable
data class CmsAssetDto(
    @SerialName("asset_id") val assetId: String = "",
    @SerialName("show_id") val showId: String = "",
    @SerialName("content_id") val contentId: String = "",
    @SerialName("external_id") val externalId: String = "",
    val title: String = "",
    val name: JsonElement? = null,
    @SerialName("regional_title") val regionalTitle: String = "",
    @SerialName("long_description") val longDescription: String = "",
    @SerialName("short_description") val shortDescription: String = "",
    val synopsis: String = "",
    val description: String = "",
    val images: List<CmsAssetImageDto> = emptyList(),
    val runtime: Int = 0,
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("parental_rating") val parentalRating: String = "",
    @SerialName("imdb_rating") val imdbRating: Double? = null,
    @SerialName("asset_type") val assetTypeSnake: String? = null,
    val assetType: String? = null,
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("original_language") val originalLanguage: String = "",
    @SerialName("available_language") val availableLanguage: List<String> = emptyList(),
    val genres: List<CmsGenreDto> = emptyList(),
    val cast: List<CmsCastDto> = emptyList(),
    val director: String = "",
    val tags: List<String> = emptyList(),
    @SerialName("playback_urls") val playbackUrls: CmsPlaybackUrlsDto? = null,
    @SerialName("trailer_urls") val trailerUrls: CmsPlaybackUrlsDto? = null,
    @SerialName("hls_url") val hlsUrl: String = "",
    @SerialName("dash_url") val dashUrl: String = "",
    @SerialName("smooth_url") val smoothUrl: String = "",
    @SerialName("channel_url") val channelUrl: String = "",
    @SerialName("preview_hls_url") val previewHlsUrl: String = "",
    @SerialName("preview_dash_url") val previewDashUrl: String = "",
    @SerialName("is_hd") val isHd: JsonPrimitive? = null,
    val dolby: JsonPrimitive? = null,
    @SerialName("Dolby") val dolbyUpper: JsonPrimitive? = null,
    @SerialName("publish_now") val publishNow: Int = 1,
)

@Serializable
data class CmsAssetImageDto(
    @SerialName("lang_code") val langCode: String = "",
    @SerialName("group_key") val groupKey: String = "",
    @SerialName("devicetype") val deviceType: List<String> = emptyList(),
    @SerialName("image_ratio") val imageRatio: String = "",
    val imagetype: String = "",
    val imageurl: String = "",
    val imagewidth: Int = 0,
    val imageheight: Int = 0,
    val imgurl: String = "",
)

@Serializable
data class CmsGenreDto(
    @SerialName("genre_id") val genreId: String = "",
    @SerialName("genre_name") val genreName: String = "",
    val description: String = "",
)

@Serializable
data class CmsCastDto(
    val name: String = "",
)

@Serializable
data class CmsPlaybackUrlsDto(
    @SerialName("dash_url") val dashUrl: String = "",
    @SerialName("smooth_url") val smoothUrl: String = "",
    @SerialName("hls_url") val hlsUrl: String = "",
    @SerialName("drm_url") val drmUrl: String = "",
)
