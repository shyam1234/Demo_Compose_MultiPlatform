package com.ott.platform.web

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.ott.platform.platform.web.webPlayerModule
import com.ott.platform.shared.OttApp
import com.ott.platform.shared.branding.BrandConfigLoader
import com.ott.platform.shared.initKoin
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    scope.launch {
        val customer = BrandConfigLoader.loadActiveCustomer()
        initKoin(extraModules = listOf(webPlayerModule), customer = customer)
        val container = document.getElementById("ott-compose-root") ?: document.body ?: return@launch
        ComposeViewport(container) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 840.dp
                OttApp(isTv = isWideScreen)
            }
        }
    }
}
