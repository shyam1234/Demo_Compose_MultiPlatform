package com.ott.platform.feature.player.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ott.platform.designsystem.OttColors
import com.ott.platform.domain.playback.PlayerStatus
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val ControlsHideDelay = 4.seconds
private const val SkipMs = 10_000L

@Composable
fun PlayerRoute(
    contentId: String,
    onBack: () -> Unit,
    isTv: Boolean = false,
    viewModel: PlayerViewModel = koinViewModel(parameters = { parametersOf(contentId) }),
) {
    val state by viewModel.playerState.collectAsState()
    PlayerScreen(
        title = state.media?.title ?: "Player",
        status = state.status,
        positionMs = state.positionMs,
        durationMs = state.durationMs,
        error = state.errorMessage,
        streamUrl = state.media?.streamUrl,
        onBack = onBack,
        onToggle = viewModel::togglePlayPause,
        onSeek = viewModel::seekTo,
        isTv = isTv,
    )
}

@Composable
fun PlayerScreen(
    title: String,
    status: PlayerStatus,
    positionMs: Long,
    durationMs: Long,
    error: String?,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    streamUrl: String? = null,
    modifier: Modifier = Modifier,
    isTv: Boolean = false,
) {
    val chromePad = if (isTv) 36.dp else 16.dp
    val isLive = durationMs <= 0L && status == PlayerStatus.Playing
    val showVodControls = durationMs > 0L
    val isPlaying = status == PlayerStatus.Playing || status == PlayerStatus.Buffering
    val keepControlsPinned = status == PlayerStatus.Error ||
        status == PlayerStatus.Paused ||
        status == PlayerStatus.Ended ||
        status == PlayerStatus.Idle

    var controlsVisible by remember { mutableStateOf(true) }
    var interactionEpoch by remember { mutableIntStateOf(0) }
    val playFocusRequester = remember { FocusRequester() }

    fun revealControls() {
        controlsVisible = true
        interactionEpoch += 1
    }

    fun seekBy(deltaMs: Long) {
        if (!showVodControls) return
        revealControls()
        val target = (positionMs + deltaMs).coerceIn(0L, durationMs)
        onSeek(target)
    }

    LaunchedEffect(interactionEpoch, status, keepControlsPinned) {
        if (keepControlsPinned) {
            controlsVisible = true
            return@LaunchedEffect
        }
        delay(ControlsHideDelay)
        controlsVisible = false
    }

    val showChrome = controlsVisible || keepControlsPinned

    // When chrome appears, land focus on Play so D-pad Left/Right move among transport.
    LaunchedEffect(showChrome, status) {
        if (showChrome && status != PlayerStatus.Error) {
            runCatching { playFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Spacebar, Key.MediaPlayPause, Key.Enter, Key.DirectionCenter -> {
                        revealControls()
                        onToggle()
                        true
                    }
                    // Seek only when chrome is hidden — otherwise Left/Right move focus.
                    Key.DirectionLeft -> {
                        if (showChrome) return@onKeyEvent false
                        seekBy(-SkipMs)
                        true
                    }
                    Key.DirectionRight -> {
                        if (showChrome) return@onKeyEvent false
                        seekBy(SkipMs)
                        true
                    }
                    Key.Escape, Key.Backspace, Key.Back -> {
                        onBack()
                        true
                    }
                    else -> false
                }
            }
            .testTag("player_screen"),
    ) {
        PlatformPlayerSurface(modifier = Modifier.fillMaxSize())

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (showChrome && !keepControlsPinned) {
                        controlsVisible = false
                    } else {
                        revealControls()
                    }
                },
        )

        // Top chrome — title + back + LIVE
        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent),
                        ),
                    )
                    .statusBarsPadding()
                    .padding(
                        start = chromePad,
                        end = chromePad,
                        top = chromePad,
                        bottom = if (isTv) 56.dp else 32.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerIconButton(
                    onClick = {
                        revealControls()
                        onBack()
                    },
                    size = if (isTv) 52.dp else 40.dp,
                    focusedRing = isTv,
                    testTag = "player_close",
                ) {
                    BackChevron(color = Color.White, stroke = if (isTv) 3.5f else 2.8f)
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = title,
                    color = Color.White,
                    style = if (isTv) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isLive && status != PlayerStatus.Error) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "LIVE",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE53935))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // Center transport — skip / play / skip
        AnimatedVisibility(
            visible = showChrome && status != PlayerStatus.Error,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 36.dp else 28.dp),
            ) {
                if (showVodControls) {
                    PlayerIconButton(
                        onClick = { seekBy(-SkipMs) },
                        size = if (isTv) 64.dp else 52.dp,
                        focusedRing = isTv,
                        testTag = "player_skip_back",
                    ) {
                        SkipGlyph(seconds = 10, forward = false, color = Color.White)
                    }
                }

                PlayerIconButton(
                    onClick = {
                        revealControls()
                        onToggle()
                    },
                    size = if (isTv) 88.dp else 72.dp,
                    filled = true,
                    focusedRing = isTv,
                    testTag = "player_toggle",
                    modifier = Modifier.focusRequester(playFocusRequester),
                ) {
                    if (isPlaying) {
                        PauseGlyph(color = Color.White)
                    } else {
                        PlayGlyph(color = Color.White)
                    }
                }

                if (showVodControls) {
                    PlayerIconButton(
                        onClick = { seekBy(SkipMs) },
                        size = if (isTv) 64.dp else 52.dp,
                        focusedRing = isTv,
                        testTag = "player_skip_forward",
                    ) {
                        SkipGlyph(seconds = 10, forward = true, color = Color.White)
                    }
                }
            }
        }

        // Bottom chrome — slim scrubber
        AnimatedVisibility(
            visible = showChrome && status != PlayerStatus.Error && showVodControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                        ),
                    )
                    .navigationBarsPadding()
                    .padding(
                        start = chromePad,
                        end = chromePad,
                        top = if (isTv) 48.dp else 28.dp,
                        bottom = chromePad,
                    ),
            ) {
                Slider(
                    value = positionMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                    onValueChange = {
                        revealControls()
                        onSeek(it.toLong())
                    },
                    valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = OttColors.Accent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("player_seek"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime(positionMs),
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = formatTime(durationMs),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        if (status == PlayerStatus.Buffering && !showChrome) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(if (isTv) 56.dp else 40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("…", color = Color.White, fontSize = if (isTv) 28.sp else 22.sp)
            }
        }

        if (status == PlayerStatus.Error) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.86f))
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Playback failed", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error ?: "Stream unavailable",
                    color = OttColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!streamUrl.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = streamUrl,
                        color = OttColors.OnSurfaceMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onBack) {
                    Text("Back to catalog", color = OttColors.AccentSoft)
                }
            }
        }
    }
}

@Composable
private fun PlayerIconButton(
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    focusedRing: Boolean = false,
    testTag: String,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(size)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                when {
                    filled -> Color.White.copy(alpha = 0.18f)
                    else -> Color.Black.copy(alpha = 0.35f)
                },
            )
            .then(
                if (focusedRing && focused) {
                    Modifier.border(2.5.dp, OttColors.Focus, shape)
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun BackChevron(color: Color, stroke: Float) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        drawPath(
            path = Path().apply {
                moveTo(w * 0.62f, h * 0.18f)
                lineTo(w * 0.32f, h * 0.5f)
                lineTo(w * 0.62f, h * 0.82f)
            },
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun PlayGlyph(color: Color) {
    Canvas(Modifier.size(28.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.32f, size.height * 0.18f)
            lineTo(size.width * 0.82f, size.height * 0.5f)
            lineTo(size.width * 0.32f, size.height * 0.82f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun PauseGlyph(color: Color) {
    Canvas(Modifier.size(26.dp)) {
        val barW = size.width * 0.22f
        val gap = size.width * 0.18f
        val left = size.width * 0.28f
        val top = size.height * 0.18f
        val barH = size.height * 0.64f
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(barW / 2f, barW / 2f),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(left + barW + gap, top),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(barW / 2f, barW / 2f),
        )
    }
}

@Composable
private fun SkipGlyph(seconds: Int, forward: Boolean, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(26.dp)) {
            val stroke = Stroke(width = 2.4f, cap = StrokeCap.Round)
            // Circular arrow hint
            drawArc(
                color = color,
                startAngle = if (forward) -40f else 140f,
                sweepAngle = 250f,
                useCenter = false,
                topLeft = Offset(size.width * 0.12f, size.height * 0.12f),
                size = Size(size.width * 0.76f, size.height * 0.76f),
                style = stroke,
            )
            val tipX = if (forward) size.width * 0.78f else size.width * 0.22f
            val tipY = size.height * 0.22f
            drawPath(
                path = Path().apply {
                    if (forward) {
                        moveTo(tipX - 6f, tipY - 2f)
                        lineTo(tipX + 2f, tipY + 4f)
                        lineTo(tipX - 8f, tipY + 8f)
                    } else {
                        moveTo(tipX + 6f, tipY - 2f)
                        lineTo(tipX - 2f, tipY + 4f)
                        lineTo(tipX + 8f, tipY + 8f)
                    }
                },
                color = color,
            )
        }
        Text(
            text = "${seconds}s",
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 10.sp,
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    val mm = m.toString().padStart(2, '0')
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:$mm:$ss" else "$m:$ss"
}
