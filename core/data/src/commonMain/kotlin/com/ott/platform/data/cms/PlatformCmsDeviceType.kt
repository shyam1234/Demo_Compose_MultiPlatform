package com.ott.platform.data.cms

/** Platform default for CMS menu / content `devicetype` query & filtering. */
expect fun platformCmsMenuDeviceType(): String

/**
 * Fallback CMS `devicetype` when the primary menu request fails or returns empty.
 * Phones → [CmsDeviceType.ANDROID_MOBILE]; all other form factors → [CmsDeviceType.ANDROID_TV].
 */
expect fun platformCmsMenuFallbackDeviceType(): String
