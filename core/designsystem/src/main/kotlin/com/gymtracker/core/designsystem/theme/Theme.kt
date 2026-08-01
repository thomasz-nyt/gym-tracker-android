package com.gymtracker.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * The app theme. Colour tokens are deliberately left at the Material 3 defaults for now;
 * real tokens arrive with the first screens that need them. The type scale is not a default —
 * see [GymTypography] and ADR-0011.
 */
@Composable
fun GymTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        typography = GymTypography,
        content = content,
    )
}
