package com.ott.platform.shared.branding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalActiveCustomer = staticCompositionLocalOf { ActiveCustomerConfig.demoDefault() }

fun parseColorHex(raw: String?, fallback: Color): Color {
    val value = raw.orEmpty().trim().removePrefix("#")
    if (value.length != 6 && value.length != 8) return fallback
    return runCatching {
        val long = value.toLong(16)
        if (value.length == 8) {
            Color(
                alpha = ((long shr 24) and 0xFF) / 255f,
                red = ((long shr 16) and 0xFF) / 255f,
                green = ((long shr 8) and 0xFF) / 255f,
                blue = (long and 0xFF) / 255f,
            )
        } else {
            Color(
                red = ((long shr 16) and 0xFF) / 255f,
                green = ((long shr 8) and 0xFF) / 255f,
                blue = (long and 0xFF) / 255f,
            )
        }
    }.getOrDefault(fallback)
}

@Composable
fun ProvideCustomerBrand(
    customer: ActiveCustomerConfig,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalActiveCustomer provides customer, content = content)
}
