package com.ott.platform.data.cms

/**
 * CMS `devicetype` matching helpers.
 *
 * Canonical values:
 * - Android TV: [ANDROID_TV] / [ATV_OTT]
 * - Android phone: [ANDROID_MOBILE]
 * - Android tablet: [ANDROID_TABLET]
 * - iPhone: [IOS_MOBILE]
 * - iPad: [IOS_TABLET]
 * - Web: [WEB]
 *
 * Matching is case-insensitive and treats blank / missing lists as "all devices".
 */
object CmsDeviceType {
    const val ALL = "all"
    const val WEB = "web"
    const val ANDROID_TV = "androidtv"
    const val ATV_OTT = "atv_ott"
    const val ANDROID = "android" // legacy phone alias
    const val ANDROID_MOBILE = "androidmobile"
    const val ANDROID_TABLET = "androidtablet"
    const val IOS = "ios" // legacy phone alias
    const val IOS_MOBILE = "iosmobile"
    const val IOS_TABLET = "iostablet"
    const val DESKTOP = "desktop"

    fun normalize(raw: String?): String =
        raw.orEmpty().trim().lowercase().replace(Regex("[\\s_\\-]+"), "")

    fun sameDevice(a: String, b: String): Boolean =
        normalize(a) == normalize(b)

    fun isPhoneDeviceType(deviceType: String): Boolean {
        val n = normalize(deviceType)
        return n in setOf(
            normalize(ANDROID_MOBILE),
            normalize(ANDROID),
            normalize(IOS_MOBILE),
            normalize(IOS),
        )
    }

    fun isTabletDeviceType(deviceType: String): Boolean {
        val n = normalize(deviceType)
        return n in setOf(normalize(ANDROID_TABLET), normalize(IOS_TABLET))
    }

    fun isTvDeviceType(deviceType: String): Boolean {
        val n = normalize(deviceType)
        return n in setOf(normalize(ANDROID_TV), normalize(ATV_OTT))
    }

    /**
     * Menu API fallback `devicetype` when the primary request errors / is empty.
     * Phones → [ANDROID_MOBILE]; TV / tablet / iPad / web / other → [ANDROID_TV].
     */
    fun menuFallbackDeviceType(isPhone: Boolean): String =
        if (isPhone) ANDROID_MOBILE else ANDROID_TV

    /**
     * Ordered menu `devicetype` candidates to try until a non-empty menu is returned.
     *
     * Phones: primary → [ANDROID_MOBILE] → [ANDROID_TV] (last resort).
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
        )
        normalize(WEB), normalize(DESKTOP) -> setOf(
            normalize(WEB),
            normalize(DESKTOP),
        )
        normalize(ANDROID), normalize(ANDROID_MOBILE) -> setOf(
            normalize(ANDROID),
            normalize(ANDROID_MOBILE),
        )
        normalize(IOS), normalize(IOS_MOBILE) -> setOf(
            normalize(IOS),
            normalize(IOS_MOBILE),
        )
        normalize(ANDROID_TABLET) -> setOf(normalize(ANDROID_TABLET))
        normalize(IOS_TABLET) -> setOf(normalize(IOS_TABLET))
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
