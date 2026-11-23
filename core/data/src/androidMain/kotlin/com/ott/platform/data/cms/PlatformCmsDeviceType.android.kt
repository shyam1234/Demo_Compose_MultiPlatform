package com.ott.platform.data.cms

/**
 * Default Android phone/tablet CMS device type.
 * TV app overrides [CmsConfig.menuDeviceType] to [CmsDeviceType.ANDROID_TV].
 */
actual fun platformCmsMenuDeviceType(): String = CmsDeviceType.ANDROID
