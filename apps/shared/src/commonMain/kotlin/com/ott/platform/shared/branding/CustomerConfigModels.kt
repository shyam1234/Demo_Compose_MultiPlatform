package com.ott.platform.shared.branding

import kotlinx.serialization.Serializable

/**
 * Root local branding / white-label config (`config/config.json`).
 *
 * Set [defaultCustomerId] to opt one customer into the running app.
 * Missing or invalid config falls back to [CustomerConfig.demoDefault] so
 * existing TE Play / OTT behavior is preserved.
 */
@Serializable
data class AppConfigFile(
    val schemaVersion: Int = 1,
    val defaultCustomerId: String = "teplay",
    val customers: List<CustomerConfigDto> = emptyList(),
)

@Serializable
data class CustomerConfigDto(
    val id: String,
    val displayName: String = id,
    val enabled: Boolean = true,
    /** Target application id for white-label builds, e.g. `com.cmsdemo.teplay`. */
    val packageName: String = "com.cmsdemo.$id",
    val branding: BrandingConfigDto = BrandingConfigDto(),
    val cms: CmsCustomerConfigDto = CmsCustomerConfigDto(),
    val features: FeatureFlagsDto = FeatureFlagsDto(),
    val debug: DebugConfigDto = DebugConfigDto(),
)

@Serializable
data class BrandingConfigDto(
    val brandName: String = "OTT",
    val appLogo: MediaAssetDto = MediaAssetDto(type = "image", source = "compose:splash_ott"),
    val splash: SplashConfigDto = SplashConfigDto(),
    val theme: ThemeConfigDto = ThemeConfigDto(),
)

/**
 * Splash media: [type] is `image` (static png/jpg/webp), `gif`, or `video` (mp4 / HLS).
 * [source] may be:
 * - `compose:<drawableName>` — bundled Compose resource (e.g. `compose:splash_ott`)
 * - absolute `http(s)://` URL
 * - relative CMS / CDN path
 */
@Serializable
data class SplashConfigDto(
    val type: String = "image",
    val source: String = "compose:splash_ott",
    /** Poster / fallback frame used while video loads or when video is unsupported. */
    val posterSource: String = "compose:splash_ott",
    val durationMs: Long = 3000,
    val backgroundColor: String = "#000000",
    val title: String = "OTT Platform",
    val subtitle: String = "Streaming demo",
)

@Serializable
data class MediaAssetDto(
    val type: String = "image",
    val source: String = "compose:splash_ott",
)

@Serializable
data class ThemeConfigDto(
    val dark: Boolean = true,
    val background: String = "#000000",
    val surface: String = "#121212",
    val accent: String = "#4285F4",
    val accentSoft: String = "#8AB4F8",
    val onSurface: String = "#FFFFFF",
    val focus: String = "#4285F4",
)

@Serializable
data class CmsCustomerConfigDto(
    val baseUrl: String? = null,
    val menuDeviceType: String? = null,
    /** When true, do not filter CMS rails/menus by device type. */
    val deviceTypeOptOut: Boolean = false,
    /** Force a CMS device type (e.g. `web`) instead of the platform default. */
    val deviceTypeOverride: String? = null,
    val fallbackLayoutId: String? = null,
)

@Serializable
data class FeatureFlagsDto(
    val search: Boolean = true,
    val watchlist: Boolean = true,
    val player: Boolean = true,
    val heroBanner: Boolean = true,
    val discoveryDock: Boolean = true,
    val thematicRails: Boolean = true,
)

@Serializable
data class DebugConfigDto(
    val enabled: Boolean = false,
    val verboseLogging: Boolean = false,
    /** Debug alias for CMS device-type opt-out. */
    val ignoreDeviceTypeFilter: Boolean = false,
)

enum class SplashMediaType {
    Image,
    Gif,
    Video,
    ;

    companion object {
        fun fromRaw(raw: String?): SplashMediaType = when (raw.orEmpty().trim().lowercase()) {
            "gif", "animated_gif", "animated-gif" -> Gif
            "video", "mp4", "hls", "m3u8" -> Video
            else -> Image
        }
    }
}

/** Resolved runtime customer profile used by UI / DI. */
data class ActiveCustomerConfig(
    val id: String,
    val displayName: String,
    val packageName: String,
    val brandName: String,
    val splash: ResolvedSplash,
    val theme: ThemeConfigDto,
    val appLogoSource: String,
    val features: FeatureFlagsDto,
    val debug: DebugConfigDto,
    val cmsBaseUrl: String?,
    val cmsMenuDeviceType: String?,
    val cmsDeviceTypeOptOut: Boolean,
    val cmsDeviceTypeOverride: String?,
    val cmsFallbackLayoutId: String?,
) {
    companion object {
        /** Bit-identical defaults to today's hardcoded OTT demo. */
        fun demoDefault(): ActiveCustomerConfig = ActiveCustomerConfig(
            id = "teplay",
            displayName = "TE Play",
            packageName = "com.cmsdemo.teplay",
            brandName = "OTT",
            splash = ResolvedSplash(
                type = SplashMediaType.Image,
                source = "compose:splash_ott",
                posterSource = "compose:splash_ott",
                durationMs = 3000,
                backgroundColor = "#000000",
                title = "OTT Platform",
                subtitle = "Streaming demo",
            ),
            theme = ThemeConfigDto(),
            appLogoSource = "compose:splash_ott",
            features = FeatureFlagsDto(),
            debug = DebugConfigDto(),
            cmsBaseUrl = null,
            cmsMenuDeviceType = null,
            cmsDeviceTypeOptOut = false,
            cmsDeviceTypeOverride = null,
            cmsFallbackLayoutId = null,
        )
    }
}

data class ResolvedSplash(
    val type: SplashMediaType,
    val source: String,
    val posterSource: String,
    val durationMs: Long,
    val backgroundColor: String,
    val title: String,
    val subtitle: String,
)

fun CustomerConfigDto.toActive(): ActiveCustomerConfig = ActiveCustomerConfig(
    id = id,
    displayName = displayName,
    packageName = packageName,
    brandName = branding.brandName,
    splash = ResolvedSplash(
        type = SplashMediaType.fromRaw(branding.splash.type),
        source = branding.splash.source,
        posterSource = branding.splash.posterSource.ifBlank { branding.splash.source },
        durationMs = branding.splash.durationMs.coerceAtLeast(500),
        backgroundColor = branding.splash.backgroundColor,
        title = branding.splash.title,
        subtitle = branding.splash.subtitle,
    ),
    theme = branding.theme,
    appLogoSource = branding.appLogo.source,
    features = features,
    debug = debug,
    cmsBaseUrl = cms.baseUrl,
    cmsMenuDeviceType = cms.menuDeviceType,
    cmsDeviceTypeOptOut = cms.deviceTypeOptOut || debug.ignoreDeviceTypeFilter,
    cmsDeviceTypeOverride = cms.deviceTypeOverride,
    cmsFallbackLayoutId = cms.fallbackLayoutId,
)
