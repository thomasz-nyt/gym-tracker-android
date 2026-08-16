package com.gymtracker.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Test
import kotlin.test.assertTrue

/**
 * ADR-0019: the palette is gated by this test, not by eye. Inherited from ADR-0016, which set
 * that rule for an orange palette; the rule outlived the colour.
 *
 * What it encodes now: every pair the app renders as text-on-fill meets WCAG AA; the accent is
 * a saturated red in both schemes, so the one identity decision cannot quietly drift to brown;
 * every surface is achromatic, because the system is mono and a tinted surface is a bug.
 *
 * What it deliberately no longer encodes: that a destructive control looks different from a
 * save. ADR-0016 got that from the palette — red meant Delete, orange meant Save. The accent is
 * red now, so colour cannot carry it, and ADR-0019 replaced it with a **layout** invariant: a
 * destructive control never shares a surface with a save, and is outlined rather than filled.
 * That is asserted by Compose UI tests over the screens that have both. If those tests are
 * deleted, nothing here will catch it.
 */
class GymColorSchemeTest {
    private val schemes: Map<String, ColorScheme> =
        mapOf("light" to GymLightColorScheme, "dark" to GymDarkColorScheme)

    /** The pairs the app actually renders text on, per ADR-0019. */
    private fun ColorScheme.renderedPairs(): Map<String, Pair<Color, Color>> =
        mapOf(
            "primary" to (primary to onPrimary),
            "primaryContainer" to (primaryContainer to onPrimaryContainer),
            "secondary" to (secondary to onSecondary),
            "secondaryContainer" to (secondaryContainer to onSecondaryContainer),
            "background" to (background to onBackground),
            "surface" to (surface to onSurface),
            "surfaceVariant" to (surfaceVariant to onSurfaceVariant),
            // Cards and the set-entry sheet sit on these, and they carry onSurface text.
            "surfaceContainerLowest" to (surfaceContainerLowest to onSurface),
            "surfaceContainerLow" to (surfaceContainerLow to onSurface),
            "surfaceContainer" to (surfaceContainer to onSurface),
            "surfaceContainerHigh" to (surfaceContainerHigh to onSurface),
            "surfaceContainerHighest" to (surfaceContainerHighest to onSurface),
            "error" to (error to onError),
            // Redesign audit, PR A finding 3: these three were never in the suite, so they
            // still inherited Material's baseline pairs — not necessarily illegible, but
            // ungated, which is exactly how outlineVariant shipped as lavender first time.
            "errorContainer" to (errorContainer to onErrorContainer),
            "tertiaryContainer" to (tertiaryContainer to onTertiaryContainer),
            // Standard-library Snackbar reads this pair.
            "inverseSurface" to (inverseSurface to inverseOnSurface),
        )

    /**
     * Every achromatic role the app renders. `outlineVariant` is in here for a reason: it is
     * the token `HorizontalDivider` reads, it was never overridden, and it shipped as Material's
     * lavender #CAC4D0 on three screens. Same class of bug ADR-0016 caught in the card
     * surfaces, one token over.
     */
    private fun ColorScheme.achromaticRoles(): Map<String, Color> =
        mapOf(
            "surface" to surface,
            "surfaceContainerLowest" to surfaceContainerLowest,
            "surfaceContainerLow" to surfaceContainerLow,
            "surfaceContainer" to surfaceContainer,
            "surfaceContainerHigh" to surfaceContainerHigh,
            "surfaceContainerHighest" to surfaceContainerHighest,
            "surfaceVariant" to surfaceVariant,
            "outline" to outline,
            "outlineVariant" to outlineVariant,
            // Redesign audit, PR A finding 3: tertiary already mirrors secondary (both are ink,
            // not a third hue), so its container should measure grey the same way
            // secondaryContainer does — left unset, it would inherit Material's tinted default.
            "tertiaryContainer" to tertiaryContainer,
            "onTertiaryContainer" to onTertiaryContainer,
            // The "other" scheme's ground, for Snackbar. Still mono — inverting light/dark
            // does not introduce a second hue.
            "inverseSurface" to inverseSurface,
            "inverseOnSurface" to inverseOnSurface,
            "scrim" to scrim,
        )

    @Test
    fun `every rendered pair meets WCAG AA in both schemes`() {
        schemes.forEach { (name, scheme) ->
            scheme.renderedPairs().forEach { (role, pair) ->
                val ratio = WcagContrast.ratio(pair.first, pair.second)
                assertTrue(
                    ratio >= WCAG_AA,
                    "$name $role is $ratio:1, below the ${WCAG_AA}:1 the ADR commits to",
                )
            }
        }
    }

    @Test
    fun `the accent is red in both schemes, not a hue that drifted`() {
        schemes.forEach { (name, scheme) ->
            val hue = hueDegrees(scheme.primary)
            assertTrue(
                hue <= RED_BAND_END || hue >= RED_BAND_START,
                "$name primary has hue $hue°, outside the red band ADR-0019 chose",
            )
        }
    }

    @Test
    fun `the accent is saturated, so it cannot decay into a brown`() {
        // ADR-0016 asserted luminance here, because high-vis orange had to stay bright under
        // dark text. ADR-0019 inverts that on the light scheme — a deep red fill under a pale
        // label — so brightness is the wrong gate and saturation is the right one. A red that
        // loses its saturation is a brown, and a brown is not an identity.
        schemes.forEach { (name, scheme) ->
            assertTrue(
                saturation(scheme.primary) >= ACCENT_SATURATION_FLOOR,
                "$name primary has saturation ${saturation(scheme.primary)}, " +
                    "too washed out to read as the accent",
            )
        }
    }

    @Test
    fun `surfaceTint and inversePrimary carry the accent, not Material's default violet`() {
        // Redesign audit, PR A finding 3. `surfaceTint` and `inversePrimary` are not derived
        // from `primary` by the `lightColorScheme()`/`darkColorScheme()` constructors — they
        // are separate parameters that default to Material's baseline violet if the caller
        // does not pass them, same trap as `outlineVariant` (finding 08).
        schemes.forEach { (name, scheme) ->
            mapOf("surfaceTint" to scheme.surfaceTint, "inversePrimary" to scheme.inversePrimary)
                .forEach { (role, color) ->
                    val hue = hueDegrees(color)
                    assertTrue(
                        hue <= RED_BAND_END || hue >= RED_BAND_START,
                        "$name $role has hue $hue°, outside the red band ADR-0019 chose",
                    )
                    assertTrue(
                        saturation(color) >= ACCENT_SATURATION_FLOOR,
                        "$name $role has saturation ${saturation(color)}, too washed out to read as the accent",
                    )
                }
        }
    }

    @Test
    fun `no surface or outline carries a tint`() {
        // The system is mono: the accent is the only colour on screen. Anything that is
        // structurally grey must measure grey, which is what stops Material's violet-tinted
        // defaults from leaking back in through a role nobody overrode.
        schemes.forEach { (name, scheme) ->
            scheme.achromaticRoles().forEach { (role, color) ->
                assertTrue(
                    saturation(color) <= NEUTRAL_SATURATION,
                    "$name $role has saturation ${saturation(color)} at hue " +
                        "${hueDegrees(color)}° — mono means this role measures grey",
                )
            }
        }
    }

    @Test
    fun `outlineVariant is not Material's lavender default`() {
        // Finding 08 of the redesign audit, pinned. The three HorizontalDivider() calls in the
        // app read this token and drew #CAC4D0 because Color.kt never set it.
        schemes.forEach { (name, scheme) ->
            assertTrue(
                scheme.outlineVariant != MATERIAL_DEFAULT_OUTLINE_VARIANT,
                "$name outlineVariant is still Material's #CAC4D0 lavender",
            )
        }
    }

    @Test
    fun `outlineVariant is legible against the ground, not just different from lavender`() {
        // The bug the test above missed: overriding outlineVariant away from #CAC4D0 to
        // #C6C4C3 changed the value without fixing the problem, because #C6C4C3 measures only
        // ~1.3:1 against the ground — a rule nobody can see, still passing "isn't lavender".
        // `Color.kt` now sets outlineVariant to ink at 40% opacity, deliberately *not* the WCAG
        // 1.4.11 non-text 3:1 minimum: `GymDivider` is used everywhere from list rows to a
        // screen's one structural rule (a heavier, solid-ink rule some screens draw directly
        // rather than through this token), and this floor gates the lighter, more common case.
        // Comfortably above the ~1.3:1 that shipped, so this test fails the moment the value
        // regresses toward invisible again.
        schemes.forEach { (name, scheme) ->
            val ratio = WcagContrast.ratio(scheme.outlineVariant, scheme.background)
            assertTrue(
                ratio >= MINIMUM_DIVIDER_CONTRAST,
                "$name outlineVariant is $ratio:1 against the ground, below the " +
                    "${MINIMUM_DIVIDER_CONTRAST}:1 floor a visible row rule needs",
            )
        }
    }

    @Test
    fun `error stays red`() {
        // Note this no longer separates Delete from Save — see the class comment. It survives
        // because error red is a convention worth keeping, not because it guarantees anything
        // about the primary next to it.
        schemes.forEach { (name, scheme) ->
            val hue = hueDegrees(scheme.error)
            assertTrue(
                hue <= RED_BAND_END || hue >= RED_BAND_START,
                "$name error has hue $hue°, which is not red",
            )
        }
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

        /** Below this a colour reads as grey and its hue is not worth arguing about. */
        const val NEUTRAL_SATURATION = 0.10

        /** The accent measures 1.00 light and 0.76 dark; this leaves room without inviting a brown. */
        const val ACCENT_SATURATION_FLOOR = 0.60

        /**
         * Comfortably above the ~1.3:1 the pre-ADR-0029 value measured, comfortably below the
         * 3:1 WCAG 1.4.11 non-text minimum the structural rule (solid ink) clears instead. Ink
         * at 40% measures ~2.4:1 light / ~3.4:1 dark against the ground; this floor is set below
         * both so a legitimate future adjustment to the exact opacity doesn't require touching
         * the test, while a regression back toward invisible still fails it.
         */
        const val MINIMUM_DIVIDER_CONTRAST = 2.0

        const val RED_BAND_START = 345.0
        const val RED_BAND_END = 15.0

        /** `ColorScheme`'s unoverridden `outlineVariant`, the colour finding 08 is about. */
        val MATERIAL_DEFAULT_OUTLINE_VARIANT = Color(0xFFCAC4D0)

        const val DEGREES_PER_SEXTANT = 60.0
        const val SEXTANTS = 6.0
        const val FULL_CIRCLE = 360.0
    }
}
