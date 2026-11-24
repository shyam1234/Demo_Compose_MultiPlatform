package com.ott.platform.data.cms

import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPad

/**
 * Resolves CMS `devicetype` for Apple mobile:
 * - iPad → [CmsDeviceType.ANDROID_TV] (same TV catalog as Android tablet)
 * - iPhone → [CmsDeviceType.ANDROID_MOBILE] (CMS has no `ios` menu; phone catalog is androidmobile)
 */
private fun isIosPhoneFormFactor(): Boolean =
    UIDevice.currentDevice.userInterfaceIdiom != UIUserInterfaceIdiomPad

actual fun platformCmsMenuDeviceType(): String =
    if (isIosPhoneFormFactor()) CmsDeviceType.ANDROID_MOBILE else CmsDeviceType.ANDROID_TV

actual fun platformCmsMenuFallbackDeviceType(): String =
    CmsDeviceType.menuFallbackDeviceType(isPhone = isIosPhoneFormFactor())

