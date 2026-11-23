package com.ott.platform.data.cms

actual fun platformCmsMenuDeviceType(): String = CmsDeviceType.WEB

actual fun platformCmsMenuFallbackDeviceType(): String =
    CmsDeviceType.menuFallbackDeviceType(isPhone = false)
