package com.ott.platform.data.cms

import android.annotation.SuppressLint
import android.content.Context

/**
 * Resolves CMS `devicetype` for Android:
 * - tablets (`smallestScreenWidthDp >= 600`) → [CmsDeviceType.ANDROID_TV]
 * - phones → [CmsDeviceType.ANDROID]
 *
 * Call [installAndroidCmsDeviceTypeContext] from `Application.onCreate` before reading CMS config.
 * The TV app still overrides to [CmsDeviceType.ANDROID_TV] explicitly.
 */
@SuppressLint("StaticFieldLeak") // Application context only.
private var androidCmsAppContext: Context? = null

fun installAndroidCmsDeviceTypeContext(context: Context) {
    androidCmsAppContext = context.applicationContext
}

actual fun platformCmsMenuDeviceType(): String {
    val sw = androidCmsAppContext
        ?.resources
        ?.configuration
        ?.smallestScreenWidthDp
        ?: 0
    return if (sw >= 600) CmsDeviceType.ANDROID_TV else CmsDeviceType.ANDROID
}
