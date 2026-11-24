package com.ott.platform.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ott.platform.designsystem.OttColors

/**
 * Shared full-screen error page used on every platform when the CMS / network is unavailable.
 */
@Composable
fun OttErrorPage(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Unable to connect",
    message: String = "We couldn’t reach the server right now. Check your connection and try again.",
    retryLabel: String = "Try again",
    detail: String? = null,
    compact: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    val inputModeManager = LocalInputModeManager.current
    LaunchedEffect(Unit) {
        if (inputModeManager.inputMode == InputMode.Keyboard) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OttColors.Background)
            .padding(horizontal = if (compact) 24.dp else 48.dp, vertical = 32.dp)
            .testTag("ott_error_page"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 64.dp else 88.dp)
                    .background(OttColors.SurfaceElevated.copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "!",
                    color = OttColors.AccentSoft,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(if (compact) 20.dp else 28.dp))
            Text(
                text = title,
                color = OttColors.OnSurface,
                style = if (compact) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.headlineMedium
                },
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("ott_error_title"),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                color = OttColors.OnSurfaceMuted,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("ott_error_message"),
            )
            if (!detail.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = detail,
                    color = OttColors.OnSurfaceMuted.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("ott_error_detail"),
                )
            }
            Spacer(Modifier.height(if (compact) 24.dp else 32.dp))
            FocusablePillButton(
                label = retryLabel,
                onClick = onRetry,
                style = PillStyle.Primary,
                compact = compact,
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .testTag("ott_error_retry"),
            )
        }
    }
}

/** True when [message] looks like a network / CMS outage rather than empty content. */
fun isServerUnavailableMessage(message: String?): Boolean {
    if (message.isNullOrBlank()) return false
    val m = message.lowercase()
    return listOf(
        "unable to",
        "failed to connect",
        "connection",
        "connectexception",
        "socket",
        "timeout",
        "timed out",
        "unreachable",
        "unknownhost",
        "no internet",
        "network",
        "cms request failed",
        "cms catalog error",
        "502",
        "503",
        "504",
        "500",
        "ssl",
        "handshake",
        "host",
        "not available for device",
        "cms rail",
        "cms layout",
        "cms catalog",
    ).any { it in m }
}
