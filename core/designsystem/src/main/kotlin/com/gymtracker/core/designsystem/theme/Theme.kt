package com.gymtracker.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * The app theme. Nothing here is a Material default: the type scale is ADR-0011's, the palette
 * is ADR-0019's mono-with-one-red-accent, and every corner is square per the same ADR — all of
 * it chosen for reading a phone at arm's length on a gym floor.
 *
 * The system light/dark setting is honoured for the same reason ADR-0011 kept sizes in `sp` —
 * a member who has already told the OS what they want should not be overridden by this app.
 * Dynamic colour is deliberately not used: it derives muted tones from the wallpaper and would
 * hand the app's one identity decision to whatever is behind the home screen.
 *
 * [LocalMascotBand] is provided here, not inside [MaterialTheme.colorScheme] (ADR-0035): Rep's
 * band is the one deliberate exception to the mono palette, and keeping it out of `ColorScheme`
 * is what lets `GymColorSchemeTest` keep asserting the *rendered* app is mono-plus-red without
 * carving out an exception inside that test.
 */
@Composable
fun GymTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) GymDarkColorScheme else GymLightColorScheme,
        shapes = GymShapes,
        typography = GymTypography,
    ) {
        CompositionLocalProvider(
            LocalMascotBand provides if (darkTheme) MascotColors.BandDark else MascotColors.BandLight,
            content = content,
        )
    }
}
