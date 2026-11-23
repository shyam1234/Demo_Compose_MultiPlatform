package com.ott.platform.data.cms

/**
 * CMS `devicetype` matching helpers.
 *
 * Payloads use values like `web`, `androidtv`, `atv_ott`, and image-level `All`.
 * Matching is case-insensitive and treats blank / missing lists as "all devices".
 */
object CmsDeviceType {
    const val ALL = "all"
    const val WEB = "web"
    const val ANDROID_TV = "androidtv"
    const val ATV_OTT = "atv_ott"
    const val ANDROID = "android"
    const val IOS = "ios"
    const val DESKTOP = "desktop"

    fun normalize(raw: String?): String =
        raw.orEmpty().trim().lowercase().replace(Regex("[\\s_\\-]+"), "")

    /**
     * Returns true when [currentDevice] is allowed by CMS [allowedDevices].
     * Empty allowed list = unrestricted. `All` = unrestricted.
     */
    fun matches(allowedDevices: List<String>, currentDevice: String): Boolean {
        if (allowedDevices.isEmpty()) return true
        val allowed = allowedDevices.map(::normalize).filter { it.isNotEmpty() }.toSet()
        if (allowed.isEmpty() || ALL in allowed) return true
        val current = normalize(currentDevice)
        if (current.isEmpty()) return true
        if (current in allowed) return true
        return aliases(current).any { it in allowed }
    }

    fun aliases(normalizedDevice: String): Set<String> = when (normalizedDevice) {
        normalize(ANDROID_TV), normalize(ATV_OTT) -> setOf(
            normalize(ANDROID_TV),
            normalize(ATV_OTT),
            "androidtv",
            "atvott",
        )
        normalize(WEB), normalize(DESKTOP) -> setOf(normalize(WEB), normalize(DESKTOP))
        else -> setOf(normalizedDevice)
    }

    fun filterImagesForDevice(
        images: List<CmsAssetImageDto>,
        currentDevice: String,
    ): List<CmsAssetImageDto> {
        if (images.isEmpty()) return images
        val matched = images.filter { matches(it.deviceType, currentDevice) }
        return matched.ifEmpty { images }
    }
}
