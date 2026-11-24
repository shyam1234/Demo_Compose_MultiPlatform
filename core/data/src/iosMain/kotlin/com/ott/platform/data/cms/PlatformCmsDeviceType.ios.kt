package com.ott.platform.data.cms

import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPad

/**
 * Resolves CMS `devicetype` for Apple mobile:
 * - iPad → [CmsDeviceType.IOS_TABLET]
 * - iPhone → [CmsDeviceType.IOS_MOBILE]
 */
private fun isIosPhoneFormFactor(): Boolean =
    UIDevice.currentDevice.userInterfaceIdiom != UIUserInterfaceIdiomPad

actual fun platformCmsMenuDeviceType(): String =
    if (isIosPhoneFormFactor()) CmsDeviceType.IOS_MOBILE else CmsDeviceType.IOS_TABLET

actual fun platformCmsMenuFallbackDeviceType(): String =
    CmsDeviceType.menuFallbackDeviceType(isPhone = isIosPhoneFormFactor())
