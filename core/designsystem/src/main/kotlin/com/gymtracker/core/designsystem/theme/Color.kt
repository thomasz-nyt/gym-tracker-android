package com.gymtracker.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The app's palette (ADR-0016).
 *
 * One accent, high-visibility orange, in both schemes. Text on it is near-black rather than
 * white, which is how high-vis actually works — no orange bright enough to read as bright can
 * carry white text at AA contrast.
 *
 * Red belongs to destructive actions and errors only, so Delete can never dress like Save.
 *
 * `GymColorSchemeTest` asserts the contrast ratios, the accent's hue and brightness, and that
 * error stays red. Changing a value here means making that suite agree.
 */
private val Orange = Color(0xFFF26200)
private val OrangeBright = Color(0xFFFF6D00)
private val OnOrange = Color(0xFF1A0E00)

private val OrangeContainerLight = Color(0xFFFFDBC7)
private val OrangeContainerDark = Color(0xFF6B2A00)
private val OnOrangeContainerLight = Color(0xFF331200)

private val WarmNeutralLight = Color(0xFFEADDD0)
private val WarmNeutralDark = Color(0xFF453A2F)
private val OnWarmNeutralLight = Color(0xFF3B2E22)

private val SurfaceLight = Color(0xFFFFFBF7)
private val OnSurfaceLight = Color(0xFF1C1B1A)
private val SurfaceDark = Color(0xFF16130F)
private val OnSurfaceDark = Color(0xFFEDE0D4)

private val SurfaceVariantLight = Color(0xFFEFE0D5)
private val OnSurfaceVariantLight = Color(0xFF4F4539)
private val SurfaceVariantDark = Color(0xFF3A322B)
private val OnSurfaceVariantDark = Color(0xFFD8C7B8)

// The elevation ramp cards and the set-entry sheet are drawn on. Material's defaults for these
// are tinted violet, and overriding `surface` alone leaves them that way — which shipped as a
// lavender card on a warm-white screen and was only visible on a device. `GymColorSchemeTest`
// now asserts every surface is neutral or warm.
private val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
private val SurfaceContainerLowLight = Color(0xFFFBF4EC)
private val SurfaceContainerLight = Color(0xFFF5EEE5)
private val SurfaceContainerHighLight = Color(0xFFEFE8DF)
private val SurfaceContainerHighestLight = Color(0xFFE9E2D9)

private val SurfaceContainerLowestDark = Color(0xFF100D0A)
private val SurfaceContainerLowDark = Color(0xFF1E1A16)
private val SurfaceContainerDark = Color(0xFF221E19)
private val SurfaceContainerHighDark = Color(0xFF2D2822)
private val SurfaceContainerHighestDark = Color(0xFF38322B)

private val ErrorLight = Color(0xFFBA1A1A)
private val ErrorDark = Color(0xFFFFB4AB)
private val OnErrorDark = Color(0xFF690005)

val GymLightColorScheme =
    lightColorScheme(
        primary = Orange,
        onPrimary = OnOrange,
        primaryContainer = OrangeContainerLight,
        onPrimaryContainer = OnOrangeContainerLight,
        secondary = OnWarmNeutralLight,
        onSecondary = Color.White,
        secondaryContainer = WarmNeutralLight,
        onSecondaryContainer = OnWarmNeutralLight,
        tertiary = OnWarmNeutralLight,
        onTertiary = Color.White,
        background = SurfaceLight,
        onBackground = OnSurfaceLight,
        surface = SurfaceLight,
        onSurface = OnSurfaceLight,
        surfaceVariant = SurfaceVariantLight,
        onSurfaceVariant = OnSurfaceVariantLight,
        surfaceContainerLowest = SurfaceContainerLowestLight,
        surfaceContainerLow = SurfaceContainerLowLight,
        surfaceContainer = SurfaceContainerLight,
        surfaceContainerHigh = SurfaceContainerHighLight,
        surfaceContainerHighest = SurfaceContainerHighestLight,
        outline = Color(0xFF6B5F52),
        error = ErrorLight,
        onError = Color.White,
    )

val GymDarkColorScheme =
    darkColorScheme(
        primary = OrangeBright,
        onPrimary = OnOrange,
        primaryContainer = OrangeContainerDark,
        onPrimaryContainer = OrangeContainerLight,
        secondary = OnSurfaceVariantDark,
        onSecondary = Color(0xFF3B2E22),
        secondaryContainer = WarmNeutralDark,
        onSecondaryContainer = WarmNeutralLight,
        tertiary = OnSurfaceVariantDark,
        onTertiary = Color(0xFF3B2E22),
        background = SurfaceDark,
        onBackground = OnSurfaceDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        surfaceContainerLowest = SurfaceContainerLowestDark,
        surfaceContainerLow = SurfaceContainerLowDark,
        surfaceContainer = SurfaceContainerDark,
        surfaceContainerHigh = SurfaceContainerHighDark,
        surfaceContainerHighest = SurfaceContainerHighestDark,
        outline = Color(0xFF9C8C7C),
        error = ErrorDark,
        onError = OnErrorDark,
    )
