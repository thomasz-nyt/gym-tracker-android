package com.gymtracker.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Test
import kotlin.math.pow
import kotlin.test.assertTrue

/**
 * ADR-0016: the palette is gated by this test, not by eye.
 *
 * The rules it encodes: every pair the app renders as text-on-fill meets WCAG AA; the accent
 * is a bright orange in both schemes, so the "bright color" the maintainer asked for cannot
 * quietly drift muted; and error stays red, so a destructive control can never be mistaken
 * for the primary action it sits next to.
 */
class GymColorSchemeTest {
    private val schemes: Map<String, ColorScheme> =
        mapOf("light" to GymLightColorScheme, "dark" to GymDarkColorScheme)

    /** The pairs the app actually renders text on, per ADR-0016. */
    private fun ColorScheme.renderedPairs(): Map<String, Pair<Color, Color>> =
        mapOf(
            "primary" to (primary to onPrimary),
            "primaryContainer" to (primaryContainer to onPrimaryContainer),
            "secondaryContainer" to (secondaryContainer to onSecondaryContainer),
            "surface" to (surface to onSurface),
            "surfaceVariant" to (surfaceVariant to onSurfaceVariant),
            // Cards and the set-entry sheet sit on these, and they carry onSurface text.
            "surfaceContainerLow" to (surfaceContainerLow to onSurface),
            "surfaceContainer" to (surfaceContainer to onSurface),
            "surfaceContainerHigh" to (surfaceContainerHigh to onSurface),
            "error" to (error to onError),
        )

    /** Every surface the app renders, paired with the name the failure message should use. */
    private fun ColorScheme.surfaces(): Map<String, Color> =
        mapOf(
            "surface" to surface,
            "surfaceContainerLowest" to surfaceContainerLowest,
            "surfaceContainerLow" to surfaceContainerLow,
            "surfaceContainer" to surfaceContainer,
            "surfaceContainerHigh" to surfaceContainerHigh,
            "surfaceContainerHighest" to surfaceContainerHighest,
            "surfaceVariant" to surfaceVariant,
        )

    @Test
    fun `every rendered pair meets WCAG AA in both schemes`() {
        schemes.forEach { (name, scheme) ->
            scheme.renderedPairs().forEach { (role, pair) ->
                val ratio = contrastRatio(pair.first, pair.second)
                assertTrue(
                    ratio >= WCAG_AA,
                    "$name $role is $ratio:1, below the ${WCAG_AA}:1 the ADR commits to",
                )
            }
        }
    }

    @Test
    fun `the accent is orange in both schemes, not a hue that drifted`() {
        schemes.forEach { (name, scheme) ->
            val hue = hueDegrees(scheme.primary)
            assertTrue(
                hue in ORANGE_BAND,
                "$name primary has hue $hue°, outside the orange band $ORANGE_BAND",
            )
        }
    }

    @Test
    fun `the accent is bright, which was the request`() {
        // High-vis works as a bright fill with dark text on it, so the fill itself must stay
        // luminous — this is the assertion that stops "orange" becoming "brown".
        schemes.forEach { (name, scheme) ->
            assertTrue(
                relativeLuminance(scheme.primary) >= BRIGHT_FLOOR,
                "$name primary has luminance ${relativeLuminance(scheme.primary)}, " +
                    "too dark to read as a bright accent",
            )
        }
    }

    @Test
    fun `no surface keeps Material's purple tint`() {
        // The bug this pins: overriding `surface` alone leaves surfaceContainer* at Material's
        // defaults, which are tinted violet. It shipped as a lavender card on a warm-white
        // screen, and it was only visible on a device. A surface is near-neutral, and what
        // little tint it has leans warm like the rest of the palette — never blue or violet.
        schemes.forEach { (name, scheme) ->
            scheme.surfaces().forEach { (role, color) ->
                val hue = hueDegrees(color)
                val neutral = saturation(color) <= NEUTRAL_SATURATION
                assertTrue(
                    neutral || hue in WARM_BAND,
                    "$name $role has hue $hue° at saturation ${saturation(color)} — " +
                        "surfaces stay neutral or warm, and this one does not",
                )
            }
        }
    }

    @Test
    fun `error stays red, so Delete can never dress like Save`() {
        schemes.forEach { (name, scheme) ->
            val hue = hueDegrees(scheme.error)
            assertTrue(
                hue <= RED_BAND_END || hue >= RED_BAND_START,
                "$name error has hue $hue°, which is not red — ADR-0016 reserves red for " +
                    "destructive actions and orange for emphasis",
            )
        }
    }

    /** WCAG 2.x contrast ratio: (lighter + 0.05) / (darker + 0.05). */
    private fun contrastRatio(
        a: Color,
        b: Color,
    ): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** WCAG relative luminance from sRGB channels — the spec formula, kept in the test. */
    private fun relativeLuminance(color: Color): Double {
        fun linear(channel: Float): Double {
            val c = channel.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }

    /** HSV saturation: 0 is a pure grey, where hue means nothing. */
    private fun saturation(color: Color): Double {
        val max = maxOf(color.red, color.green, color.blue).toDouble()
        val min = minOf(color.red, color.green, color.blue).toDouble()
        return if (max == 0.0) 0.0 else (max - min) / max
    }

    /** Hue in degrees, 0..360, from sRGB. */
    private fun hueDegrees(color: Color): Double {
        val r = color.red.toDouble()
        val g = color.green.toDouble()
        val b = color.blue.toDouble()
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        if (delta == 0.0) return 0.0
        val hue =
            when (max) {
                r -> DEGREES_PER_SEXTANT * (((g - b) / delta).mod(SEXTANTS))
                g -> DEGREES_PER_SEXTANT * ((b - r) / delta + 2)
                else -> DEGREES_PER_SEXTANT * ((r - g) / delta + 4)
            }
        return hue.mod(FULL_CIRCLE)
    }

    private companion object {
        const val WCAG_AA = 4.5
        val ORANGE_BAND = 15.0..45.0

        /** Warm: reds through yellows. A violet surface lands nowhere near this. */
        val WARM_BAND = 0.0..60.0

        /** Below this a colour reads as grey and its hue is not worth arguing about. */
        const val NEUTRAL_SATURATION = 0.10
        const val RED_BAND_START = 345.0
        const val RED_BAND_END = 15.0
        const val BRIGHT_FLOOR = 0.25
        const val DEGREES_PER_SEXTANT = 60.0
        const val SEXTANTS = 6.0
        const val FULL_CIRCLE = 360.0
    }
}
