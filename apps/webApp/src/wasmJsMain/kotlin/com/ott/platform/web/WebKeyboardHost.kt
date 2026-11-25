package com.ott.platform.web

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import kotlinx.coroutines.delay

/**
 * Web-only arrow-key focus host.
 *
 * Seeds focus once after the first layout settles (avoids the measure-time
 * `requestFocus` loop that caused "layout state is not idle"). Directional
 * keys then move Compose focus for menu + rails.
 */
@Composable
fun WebKeyboardHost(
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val inputModeManager = LocalInputModeManager.current
    val rootFocusRequester = remember { FocusRequester() }
    var childHasFocus by remember { mutableStateOf(false) }
    var seedAttempted by remember { mutableStateOf(false) }

    // One-shot only — never re-request on every focus change (that races layout).
    LaunchedEffect(Unit) {
        delay(600)
        inputModeManager.requestInputMode(InputMode.Keyboard)
        if (!childHasFocus && !seedAttempted) {
            seedAttempted = true
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val direction = when (event.key) {
                    Key.DirectionUp -> FocusDirection.Up
                    Key.DirectionDown -> FocusDirection.Down
                    Key.DirectionLeft -> FocusDirection.Left
                    Key.DirectionRight -> FocusDirection.Right
                    else -> null
                } ?: return@onKeyEvent false

                inputModeManager.requestInputMode(InputMode.Keyboard)
                // Single moveFocus per key — avoids skipping items / jumpy web focus.
                if (!childHasFocus) {
                    val entered = focusManager.moveFocus(direction) ||
                        focusManager.moveFocus(FocusDirection.Next)
                    return@onKeyEvent entered
                }
                focusManager.moveFocus(direction)
            }
            .focusRequester(rootFocusRequester)
            .focusProperties { canFocus = !childHasFocus }
            .onFocusChanged { state ->
                childHasFocus = state.hasFocus && !state.isFocused
            }
            .focusable(),
    ) {
        content()
    }
}
