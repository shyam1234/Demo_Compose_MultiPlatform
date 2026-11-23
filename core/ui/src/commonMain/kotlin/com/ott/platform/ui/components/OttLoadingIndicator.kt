package com.ott.platform.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ott.platform.designsystem.OttColors

/**
 * Cross-platform indeterminate loader.
 *
 * Uses an explicit infinite Compose animation (not Material3's CircularProgressIndicator),
 * which can appear frozen on iOS / Compose Multiplatform.
 */
@Composable
fun OttLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    strokeWidth: Dp = 3.dp,
) {
    val transition = rememberInfiniteTransition(label = "ott_loading")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ott_loading_angle",
    )
    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = angle }
            .testTag("ott_loading"),
    ) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        drawArc(
            color = OttColors.Accent,
            startAngle = 0f,
            sweepAngle = 270f,
            useCenter = false,
            style = stroke,
        )
        drawArc(
            color = OttColors.AccentSoft.copy(alpha = 0.25f),
            startAngle = 270f,
            sweepAngle = 90f,
            useCenter = false,
            style = stroke,
        )
    }
}
