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
    /** Phone menu/content fallback when the primary phone deviceType menu is missing. */
    const val ANDROID_MOBILE = "androidmobile"
    const val IOS = "ios"
    const val DESKTOP = "desktop"

    fun normalize(raw: String?): String =
        raw.orEmpty().trim().lowercase().replace(Regex("[\\s_\\-]+"), "")

    fun sameDevice(a: String, b: String): Boolean =
        normalize(a) == normalize(b)

    /**
     * Menu API fallback `devicetype` when the primary request errors / is empty.
     * Phones → [ANDROID_MOBILE]; TV / tablet / iPad / web / other → [ANDROID_TV].
     */
    fun menuFallbackDeviceType(isPhone: Boolean): String =
        if (isPhone) ANDROID_MOBILE else ANDROID_TV

    /**
     * Ordered menu `devicetype` candidates to try until a non-empty menu is returned.
     *
     * Phones: primary → [ANDROID_MOBILE] → [ANDROID_TV] (last resort when mobile menu has no L1 items).
     * Other form factors: primary → [ANDROID_TV].
     */
    fun menuDeviceTypeCandidates(primary: String, isPhone: Boolean): List<String> {
        val ordered = LinkedHashSet<String>()
        val trimmed = primary.trim()
        if (trimmed.isNotEmpty()) ordered += trimmed
        ordered += menuFallbackDeviceType(isPhone)
        if (isPhone) ordered += ANDROID_TV
        return ordered.toList()
    }

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
        normalize(ANDROID), normalize(ANDROID_MOBILE) -> setOf(
            normalize(ANDROID),
            normalize(ANDROID_MOBILE),
        )
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
