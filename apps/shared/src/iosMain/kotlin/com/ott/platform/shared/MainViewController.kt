package com.ott.platform.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.ott.platform.platform.ios.iosPlayerModule
import com.ott.platform.shared.branding.BrandConfigLoader
import kotlinx.coroutines.runBlocking
import platform.UIKit.UIViewController

private var koinStarted = false

fun MainViewController(): UIViewController {
    if (!koinStarted) {
        runCatching {
            val customer = runBlocking { BrandConfigLoader.loadActiveCustomer() }
            initKoin(extraModules = listOf(iosPlayerModule), customer = customer)
        }
        koinStarted = true
    }
    return ComposeUIViewController {
        OttApp(isTv = false)
    }
}
