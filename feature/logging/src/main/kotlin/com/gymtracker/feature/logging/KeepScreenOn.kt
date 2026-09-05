package com.gymtracker.feature.logging

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Holds the screen on while [enabled] (US-59).
 *
 * Set through [android.view.View.setKeepScreenOn] on the Compose host view rather than the
 * window flag directly: the view system raises `FLAG_KEEP_SCREEN_ON` on the window while a view
 * asking for it is attached and drops it when that view goes away, which is exactly the
 * lifetime wanted — backgrounding the app, or this composable leaving composition when the
 * session ends, releases the hold without a second code path to forget. No wake lock, no
 * permission.
 */
@Composable
internal fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
