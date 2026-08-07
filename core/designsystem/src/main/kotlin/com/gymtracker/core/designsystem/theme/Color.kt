package com.gymtracker.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The app's palette (ADR-0019, superseding ADR-0016's orange).
 *
 * A mono system: everything on screen is achromatic except one accent, red. The light scheme
 * fills with a deep red under a pale label; the dark scheme fills with a brighter red under a
 * near-black one. Both directions are gated at WCAG AA by `GymColorSchemeTest`.
 *
 * Red is *not* reserved for destructive actions any more — it cannot be, it is the accent.
 * ADR-0019 replaced that guarantee with a layout rule: a destructive control never shares a
 * surface with a save, and is outlined rather than filled. Colour no longer carries it.
 *
 * Changing a value here means making the contrast suite agree.
 */
private val Red = Color(0xFFAE1800)
private val RedBright = Color(0xFFFF563C)
private val OnRed = Color(0xFFF3F2F2)
private val OnRedBright = Color(0xFF2A0500)

private val RedContainerLight = Color(0xFFFFDAD4)
private val RedContainerDark = Color(0xFF7C1405)
private val OnRedContainerLight = Color(0xFF410000)

// ── The ground ────────────────────────────────────────────────────────────────────────
// Achromatic by measurement, not by intention: `GymColorSchemeTest` asserts saturation on
// every one of these. Material's own surface ramp is violet-tinted, and overriding `surface`
// alone leaves the rest of it that way — the bug ADR-0016 caught in the cards and finding 08
// caught one token further on, in `outlineVariant`.
private val GroundLight = Color(0xFFF3F2F2)
private val InkLight = Color(0xFF201E1D)
private val GroundDark = Color(0xFF131212)
private val InkDark = Color(0xFFEDEBEA)

private val MutedInkLight = Color(0xFF444141)
private val MutedInkDark = Color(0xFFC6C4C3)

private val GreyLight = Color(0xFFE3E1E0)
private val GreyDark = Color(0xFF3A3837)

private val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
private val SurfaceContainerLowLight = Color(0xFFEDECEC)
private val SurfaceContainerLight = Color(0xFFE8E7E6)
private val SurfaceContainerHighLight = Color(0xFFE2E1E0)
private val SurfaceContainerHighestLight = Color(0xFFDCDBDA)

private val SurfaceContainerLowestDark = Color(0xFF0D0C0C)
private val SurfaceContainerLowDark = Color(0xFF1C1B1A)
private val SurfaceContainerDark = Color(0xFF201E1D)
private val SurfaceContainerHighDark = Color(0xFF2A2827)
private val SurfaceContainerHighestDark = Color(0xFF353332)

private val OutlineLight = Color(0xFF605D5D)
private val OutlineDark = Color(0xFF8F8C8B)

// The token `HorizontalDivider` reads. Never set before ADR-0019, so it drew Material's
// #CAC4D0 lavender on browse, history and workout detail — redesign audit, finding 08.
private val OutlineVariantLight = Color(0xFFC6C4C3)
private val OutlineVariantDark = Color(0xFF4A4847)

private val ErrorLight = Color(0xFFBA1A1A)
private val ErrorDark = Color(0xFFFFB4AB)
private val OnErrorDark = Color(0xFF690005)

val GymLightColorScheme =
    lightColorScheme(
        primary = Red,
        onPrimary = OnRed,
        primaryContainer = RedContainerLight,
        onPrimaryContainer = OnRedContainerLight,
        secondary = InkLight,
        onSecondary = GroundLight,
        secondaryContainer = GreyLight,
        onSecondaryContainer = InkLight,
        tertiary = InkLight,
        onTertiary = GroundLight,
        background = GroundLight,
        onBackground = InkLight,
        surface = GroundLight,
        onSurface = InkLight,
        surfaceVariant = GreyLight,
        onSurfaceVariant = MutedInkLight,
        surfaceContainerLowest = SurfaceContainerLowestLight,
        surfaceContainerLow = SurfaceContainerLowLight,
        surfaceContainer = SurfaceContainerLight,
        surfaceContainerHigh = SurfaceContainerHighLight,
        surfaceContainerHighest = SurfaceContainerHighestLight,
        outline = OutlineLight,
        outlineVariant = OutlineVariantLight,
        error = ErrorLight,
        onError = Color.White,
    )

val GymDarkColorScheme =
    darkColorScheme(
        primary = RedBright,
        onPrimary = OnRedBright,
        primaryContainer = RedContainerDark,
        onPrimaryContainer = RedContainerLight,
        secondary = InkDark,
        onSecondary = GroundDark,
        secondaryContainer = GreyDark,
        onSecondaryContainer = GreyLight,
        tertiary = InkDark,
        onTertiary = GroundDark,
        background = GroundDark,
        onBackground = InkDark,
        surface = GroundDark,
        onSurface = InkDark,
        surfaceVariant = GreyDark,
        onSurfaceVariant = MutedInkDark,
        surfaceContainerLowest = SurfaceContainerLowestDark,
        surfaceContainerLow = SurfaceContainerLowDark,
        surfaceContainer = SurfaceContainerDark,
        surfaceContainerHigh = SurfaceContainerHighDark,
        surfaceContainerHighest = SurfaceContainerHighestDark,
        outline = OutlineDark,
        outlineVariant = OutlineVariantDark,
        error = ErrorDark,
        onError = OnErrorDark,
    )
