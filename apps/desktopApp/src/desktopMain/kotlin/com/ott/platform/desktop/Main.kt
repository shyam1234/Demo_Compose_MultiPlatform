package com.ott.platform.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ott.platform.shared.OttApp
import com.ott.platform.shared.branding.BrandConfigLoader
import com.ott.platform.shared.initKoin
import kotlinx.coroutines.runBlocking

fun main() {
    val customer = runBlocking { BrandConfigLoader.loadActiveCustomer() }
    initKoin(customer = customer)
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = customer.brandName,
        ) {
            OttApp(isTv = false)
        }
    }
}
