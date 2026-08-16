package com.gymtracker.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Test
import kotlin.test.assertTrue

/**
 * ADR-0035: the mascot's band is the one place ADR-0019's "achromatic + red" claim gets a
 * deliberate exception, and that exception is gated by measurement, not by eye, the same way
 * ADR-0019 itself is gated by [GymColorSchemeTest].
 *
 * The launcher's single gold (`#D19A00`, ADR-0026) only ever read correctly because the
 * launcher tile is near-black — it measures 2.26:1 against the light-scheme ground, below
 * WCAG 1.4.11's 3:1 floor for non-text graphics. [MascotColors] pairs a darker light-mode gold
 * with the launcher's own gold for dark mode, and this test is what stops that pair from
 * quietly drifting back below the floor on either surface Rep is actually drawn on.
 *
 * Deliberately **not** part of [GymColorSchemeTest]: the mascot colours are never added to
 * `ColorScheme` (see [MascotColors]'s doc), so that suite's iteration over rendered pairs and
 * achromatic roles has nothing new to see, and ADR-0019's "no third choice" stays literally
 * true of every interactive and textual surface.
 */
class MascotColorsTest {
    @Test
    fun `the light-mode band clears the non-text floor on every surface Rep is drawn on`() {
        surfacesOf(GymLightColorScheme).forEach { (role, surface) ->
            val ratio = WcagContrast.ratio(MascotColors.BandLight, surface)
            assertTrue(
                ratio >= NON_TEXT_FLOOR,
                "MascotColors.BandLight on light $role is $ratio:1, below the ${NON_TEXT_FLOOR}:1 floor",
            )
        }
    }

    @Test
    fun `the dark-mode band clears the non-text floor on every surface Rep is drawn on`() {
        surfacesOf(GymDarkColorScheme).forEach { (role, surface) ->
            val ratio = WcagContrast.ratio(MascotColors.BandDark, surface)
            assertTrue(
                ratio >= NON_TEXT_FLOOR,
                "MascotColors.BandDark on dark $role is $ratio:1, below the ${NON_TEXT_FLOOR}:1 floor",
            )
        }
    }

    @Test
    fun `the dark-mode band is the launcher icon's own gold, unchanged`() {
        // ADR-0035's decision: dark mode keeps exactly what ADR-0026 already put on the
        // launcher tile. Only light mode needed a new value.
        assertTrue(MascotColors.BandDark == LauncherGold)
    }

    @Test
    fun `the two golds are not the same value`() {
        // A regression that set both to one colour would still pass the two floor tests above
        // whenever that colour happened to clear both schemes — this is what catches that.
        assertTrue(MascotColors.BandLight != MascotColors.BandDark)
    }

    /** The surfaces Rep is actually drawn on (US-43): the ground, cards, and the warm-up panel. */
    private fun surfacesOf(scheme: ColorScheme) =
        mapOf(
            "background" to scheme.background,
            "surfaceContainerLowest" to scheme.surfaceContainerLowest,
            "surfaceVariant" to scheme.surfaceVariant,
        )

    private companion object {
        /** WCAG 1.4.11: the minimum contrast for a non-text graphic to be perceivable. */
        const val NON_TEXT_FLOOR = 3.0

        /** ADR-0026's launcher-icon gold, `#D19A00` — repeated here only for the equality check. */
        val LauncherGold = Color(0xFFD19A00)
    }
}
