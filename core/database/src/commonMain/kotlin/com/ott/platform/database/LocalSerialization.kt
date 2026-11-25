package com.ott.platform.database

import com.ott.platform.domain.model.AppMenuItem
import com.ott.platform.domain.model.Category
import com.ott.platform.domain.model.Channel
import com.ott.platform.domain.model.MediaImage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal val localDbJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Thematic rail metadata persisted inside SQLDelight `categories.description`
 * (avoids a schema bump while keeping CMS `rail_background_image` offline).
 */
@Serializable
internal data class StoredCategoryRailMeta(
    val hasBackgroundImage: Boolean = false,
    val backgroundImageUrl: String? = null,
    val backgroundImageAspectRatio: Float? = null,
    val deviceTypes: List<String> = emptyList(),
    val note: String = "",
)

internal fun encodeCategoryDescription(
    hasBackgroundImage: Boolean,
    backgroundImageUrl: String?,
    backgroundImageAspectRatio: Float? = null,
    deviceTypes: List<String> = emptyList(),
    note: String = "",
): String {
    if (!hasBackgroundImage &&
        backgroundImageUrl.isNullOrBlank() &&
        backgroundImageAspectRatio == null &&
        deviceTypes.isEmpty() &&
        note.isBlank()
    ) {
        return ""
    }
    return localDbJson.encodeToString(
        StoredCategoryRailMeta(
            hasBackgroundImage = hasBackgroundImage,
            backgroundImageUrl = backgroundImageUrl,
            backgroundImageAspectRatio = backgroundImageAspectRatio,
            deviceTypes = deviceTypes,
            note = note,
        ),
    )
}

internal fun decodeCategoryRailMeta(raw: String): StoredCategoryRailMeta {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return StoredCategoryRailMeta()
    return runCatching { localDbJson.decodeFromString<StoredCategoryRailMeta>(trimmed) }
        .getOrElse { StoredCategoryRailMeta(note = raw) }
}

@Serializable
internal data class StoredMediaImage(
    val type: String,
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
    val langCode: String = "",
)

@Serializable
internal data class StoredChannelMetadata(
    val images: List<StoredMediaImage> = emptyList(),
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
)

@Serializable
internal data class StoredMenuItem(
    val id: String,
    val title: String,
    val layoutId: String? = null,
    val deeplinkUrl: String = "",
    val deeplinkType: String = "Internal",
    val defaultIconUrl: String? = null,
    val focusedIconUrl: String? = null,
    val selectedIconUrl: String? = null,
    val children: List<StoredMenuItem> = emptyList(),
)

internal fun Channel.encodeMetadataJson(): String {
    val meta = StoredChannelMetadata(
        images = images.map { StoredMediaImage(it.type, it.url, it.width, it.height, it.langCode) },
        backdropUrl = backdropUrl,
        synopsis = synopsis,
        genres = genres,
        releaseYear = releaseYear,
        runtimeMinutes = runtimeMinutes,
        parentalRating = parentalRating,
        imdbRating = imdbRating,
        assetType = assetType,
        cast = cast,
        director = director,
        tags = tags,
        trailerUrl = trailerUrl,
        language = language,
    )
    return localDbJson.encodeToString(meta)
}

internal fun decodeChannelMetadata(raw: String): StoredChannelMetadata =
    if (raw.isBlank()) {
        StoredChannelMetadata()
    } else {
        runCatching { localDbJson.decodeFromString<StoredChannelMetadata>(raw) }
            .getOrDefault(StoredChannelMetadata())
    }

internal fun StoredMediaImage.toDomain(): MediaImage =
    MediaImage(type = type, url = url, width = width, height = height, langCode = langCode)

internal fun AppMenuItem.toStored(): StoredMenuItem = StoredMenuItem(
    id = id,
    title = title,
    layoutId = layoutId,
    deeplinkUrl = deeplinkUrl,
    deeplinkType = deeplinkType,
    defaultIconUrl = defaultIconUrl,
    focusedIconUrl = focusedIconUrl,
    selectedIconUrl = selectedIconUrl,
    children = children.map { it.toStored() },
)

internal fun StoredMenuItem.toDomain(): AppMenuItem = AppMenuItem(
    id = id,
    title = title,
    layoutId = layoutId,
    deeplinkUrl = deeplinkUrl,
    deeplinkType = deeplinkType,
    defaultIconUrl = defaultIconUrl,
    focusedIconUrl = focusedIconUrl,
    selectedIconUrl = selectedIconUrl,
    children = children.map { it.toDomain() },
)
