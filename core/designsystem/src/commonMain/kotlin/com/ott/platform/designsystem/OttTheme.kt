package com.ott.platform.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Google Android TV / Google TV inspired leanback palette.
 * Pure black canvas, white focus rings, soft grey surfaces, blue accent sparingly.
 */
object OttColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF121212)
    val SurfaceElevated = Color(0xFF1C1C1C)
    val OnSurface = Color(0xFFFFFFFF)
    val OnSurfaceMuted = Color(0xFFB3B3B3)
    val Accent = Color(0xFF4285F4) // Google Blue for selected/actions
    val AccentSoft = Color(0xFF8AB4F8)
    val Cta = Color(0xFFFFFFFF)
    val CtaOn = Color(0xFF000000)
    val Focus = Color(0xFF4285F4) // Blue focus/highlighter ring
    val Success = Color(0xFF34A853)
    val Warning = Color(0xFFFBBC04)
}

object OttDimens {
    val ScreenPadding = 48.dp
    val PhoneScreenPadding = 16.dp
    val ShelfSpacing = 28.dp
    val PhoneShelfSpacing = 18.dp
    // Landscape (16:9)
    val CardWidth = 160.dp
    val CardHeight = 90.dp
    val TvCardWidth = 256.dp
    val TvCardHeight = 144.dp
    // Portrait / vertical poster (2:3)
    val PortraitCardWidth = 116.dp
    val PortraitCardHeight = 174.dp
    val TvPortraitCardWidth = 168.dp
    val TvPortraitCardHeight = 252.dp
    // Square (1:1) for future Square rails
    val SquareCardSize = 136.dp
    val TvSquareCardSize = 196.dp
    // Wide Banner / Editorial (21:9)
    val BannerCardWidth = 240.dp
    val BannerCardHeight = 108.dp
    val TvBannerCardWidth = 360.dp
    val TvBannerCardHeight = 162.dp
    val CardCorner = 12.dp
    val CardTitleHeight = 38.dp
    val HeroHeight = 230.dp
    val TvHeroHeight = 400.dp
    val FocusBorder = 4.dp
    val RowGap = 20.dp
    val PhoneRowGap = 12.dp
    /**
     * Left inset for thematic / branded rails so CMS `rail_background_image`
     * logo artwork stays fully visible; cards start after this gutter.
     */
    val ThematicLogoGutter = 240.dp
    val PhoneThematicLogoGutter = 148.dp
    val ThematicRailCorner = 20.dp
    val PhoneThematicRailCorner = 16.dp
    val ThematicRailInnerPadding = 22.dp
    val PhoneThematicRailInnerPadding = 14.dp
}

private val OttDarkColors = darkColorScheme(
    primary = OttColors.Accent,
    onPrimary = Color.White,
    secondary = OttColors.AccentSoft,
    background = OttColors.Background,
    surface = OttColors.Surface,
    onBackground = OttColors.OnSurface,
    onSurface = OttColors.OnSurface,
    surfaceVariant = OttColors.SurfaceElevated,
    onSurfaceVariant = OttColors.OnSurfaceMuted,
)

private val OttLightColors = lightColorScheme(
    primary = OttColors.Accent,
    onPrimary = Color.White,
    background = Color(0xFFF7F8FA),
    surface = Color.White,
    onBackground = Color(0xFF121417),
    onSurface = Color(0xFF121417),
)

@Composable
fun OttTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val useDark = darkTheme || isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (useDark) OttDarkColors else OttLightColors,
        typography = OttTypography,
        content = content,
    )
}
