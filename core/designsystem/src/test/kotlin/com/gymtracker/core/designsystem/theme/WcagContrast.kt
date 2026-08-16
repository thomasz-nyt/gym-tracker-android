package com.gymtracker.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * The WCAG 2.x contrast maths, shared by [GymColorSchemeTest] (ADR-0019's AA gate on every
 * rendered text pair) and `MascotColorsTest` (ADR-0035's 3:1 non-text floor on the mascot's
 * band). Extracted so the second gate reuses the first's formula instead of a second
 * transcription of the spec's luminance weights.
 */
internal object WcagContrast {
    /** (lighter + 0.05) / (darker + 0.05). */
    fun ratio(
        a: Color,
        b: Color,
    ): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** WCAG relative luminance from sRGB channels — the spec formula. */
    private fun relativeLuminance(color: Color): Double {
        fun linear(channel: Float): Double {
            val c = channel.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }
}
