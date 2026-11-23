package com.ott.platform.data.cms

import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPad

/**
 * Resolves CMS `devicetype` for Apple mobile:
 * - iPad → [CmsDeviceType.ANDROID_TV] (same TV catalog as Android tablet)
 * - iPhone → [CmsDeviceType.IOS]
 */
actual fun platformCmsMenuDeviceType(): String =
    if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) {
        CmsDeviceType.ANDROID_TV
    } else {
        CmsDeviceType.IOS
    }
