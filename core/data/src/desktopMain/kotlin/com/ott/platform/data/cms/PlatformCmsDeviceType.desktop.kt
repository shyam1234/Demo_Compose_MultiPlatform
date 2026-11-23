package com.ott.platform.data.cms

/** Desktop has no dedicated CMS menu today; use web catalog targeting. */
actual fun platformCmsMenuDeviceType(): String = CmsDeviceType.WEB
