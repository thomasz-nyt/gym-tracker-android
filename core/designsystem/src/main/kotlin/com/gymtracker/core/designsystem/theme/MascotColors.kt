package com.gymtracker.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Rep's sweatband, ADR-0035's deliberate exception to ADR-0019's "achromatic + red, no third
 * choice." Scoped as narrowly as the ADR allows: **illustration-only**, never text, a control,
 * or app state, and never added to [ColorScheme][androidx.compose.material3.ColorScheme] — that
 * is what keeps `GymColorSchemeTest`'s claim that the rendered app is mono-plus-red true by
 * construction. Consumed only by `RepMascot`, via [LocalMascotBand].
 *
 * One value cannot serve both schemes: ADR-0026's launcher gold, `#D19A00`, measures only
 * 2.26:1 against the light-scheme ground — it only ever read correctly on the launcher's own
 * near-black tile. So this is a pair, the same shape as [Red]/[RedBright] two files over:
 * [BandDark] keeps the launcher's exact gold, and [BandLight] is a darkened value chosen to
 * clear WCAG 1.4.11's 3:1 non-text floor on every surface Rep is drawn on — see
 * `MascotColorsTest`, which is the gate, not this comment.
 */
object MascotColors {
    /** Measures ≥3.37:1 against every light-scheme surface Rep is drawn on. */
    val BandLight = Color(0xFF9C7100)

    /** ADR-0026's launcher-icon gold, unchanged: ≥4.63:1 against every dark-scheme surface. */
    val BandDark = Color(0xFFD19A00)
}

/**
 * Resolved by [GymTrackerTheme] alongside the colour scheme. No default: a mascot drawn outside
 * the theme is a mistake worth failing loudly on, not silently drawing the wrong gold.
 */
internal val LocalMascotBand =
    staticCompositionLocalOf<Color> {
        error("LocalMascotBand has no value outside GymTrackerTheme")
    }
