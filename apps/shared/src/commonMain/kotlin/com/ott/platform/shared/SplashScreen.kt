package com.ott.platform.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ott.platform.designsystem.OttColors
import com.ott.platform.shared.branding.LocalActiveCustomer
import com.ott.platform.shared.branding.SplashMediaType
import com.ott.platform.shared.branding.parseColorHex
import com.ott.platform.shared.generated.resources.Res
import com.ott.platform.shared.generated.resources.splash_ott
import com.ott.platform.ui.components.RemoteImage
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

@Composable
fun SplashRoute(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val customer = LocalActiveCustomer.current
    val holdFor = customer.splash.durationMs.milliseconds
    LaunchedEffect(customer.id, customer.splash.durationMs) {
        delay(holdFor)
        onFinished()
    }
    SplashScreen(modifier = modifier)
}

@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
) {
    val customer = LocalActiveCustomer.current
    val splash = customer.splash
    val background = parseColorHex(splash.backgroundColor, OttColors.Background)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            SplashMedia(
                type = splash.type,
                source = splash.source,
                posterSource = splash.posterSource,
                contentDescription = "${customer.brandName} splash",
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(0.7f)
                    .testTag("splash_image"),
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = splash.title,
                style = MaterialTheme.typography.headlineLarge,
                color = OttColors.OnSurface,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = splash.subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = parseColorHex(customer.theme.accentSoft, OttColors.AccentSoft),
            )
        }
    }
}

/**
 * Renders splash media.
 * - `image` / `gif`: remote URL via [RemoteImage], or bundled Compose drawable (`compose:name`)
 * - `video` (mp4/HLS): shows [posterSource] for now (non-disruptive); platforms can later
 *   swap in a real video surface without changing config shape
 */
@Composable
private fun SplashMedia(
    type: SplashMediaType,
    source: String,
    posterSource: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val effectiveSource = when (type) {
        SplashMediaType.Video -> posterSource.ifBlank { source }
        SplashMediaType.Image, SplashMediaType.Gif -> source
    }
    when {
        effectiveSource.startsWith("compose:", ignoreCase = true) -> {
            Image(
                painter = painterResource(Res.drawable.splash_ott),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = modifier,
            )
        }
        effectiveSource.startsWith("http://", ignoreCase = true) ||
            effectiveSource.startsWith("https://", ignoreCase = true) -> {
            Box(modifier = modifier.background(Color.Transparent)) {
                RemoteImage(
                    url = effectiveSource,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        else -> {
            Image(
                painter = painterResource(Res.drawable.splash_ott),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = modifier,
            )
        }
    }
}
