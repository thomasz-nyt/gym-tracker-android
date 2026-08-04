package com.gymtracker.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The app theme. Nothing here is a Material default: the type scale is ADR-0011's and the
 * palette is ADR-0016's high-visibility orange, both chosen for reading a phone at arm's
 * length on a gym floor.
 *
 * The system light/dark setting is honoured for the same reason ADR-0011 kept sizes in `sp` —
 * a member who has already told the OS what they want should not be overridden by this app.
 * Dynamic colour is deliberately not used: it derives muted tones from the wallpaper and would
 * hand the app's one identity decision to whatever is behind the home screen.
 */
@Composable
fun GymTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) GymDarkColorScheme else GymLightColorScheme,
        typography = GymTypography,
        content = content,
    )
}
