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

private const val TABLET_SMALLEST_WIDTH_DP = 600

fun installAndroidCmsDeviceTypeContext(context: Context) {
    androidCmsAppContext = context.applicationContext
}

private fun androidSmallestWidthDp(): Int =
    androidCmsAppContext
        ?.resources
        ?.configuration
        ?.smallestScreenWidthDp
        ?: 0

private fun isAndroidPhoneFormFactor(): Boolean =
    androidSmallestWidthDp() < TABLET_SMALLEST_WIDTH_DP

actual fun platformCmsMenuDeviceType(): String =
    if (isAndroidPhoneFormFactor()) CmsDeviceType.ANDROID else CmsDeviceType.ANDROID_TV

actual fun platformCmsMenuFallbackDeviceType(): String =
    CmsDeviceType.menuFallbackDeviceType(isPhone = isAndroidPhoneFormFactor())

