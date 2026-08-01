package com.gymtracker.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ADR-0011: the type scale is raised app-wide so a set is readable at arm's length on a gym
 * floor. The sizes are a product decision, so they are asserted rather than left to drift.
 */
class GymTypographyTest {
    private val material = Typography()

    /** The roles the app actually renders, paired with the Material 3 default each replaces. */
    private val roles: Map<String, Pair<TextStyle, TextStyle>> =
        mapOf(
            "bodySmall" to (GymTypography.bodySmall to material.bodySmall),
            "bodyMedium" to (GymTypography.bodyMedium to material.bodyMedium),
            "bodyLarge" to (GymTypography.bodyLarge to material.bodyLarge),
            "labelLarge" to (GymTypography.labelLarge to material.labelLarge),
            "titleSmall" to (GymTypography.titleSmall to material.titleSmall),
            "titleMedium" to (GymTypography.titleMedium to material.titleMedium),
            "titleLarge" to (GymTypography.titleLarge to material.titleLarge),
        )

    @Test
    fun `every role the app uses is larger than the Material default it replaces`() {
        roles.forEach { (role, styles) ->
            val (ours, default) = styles
            assertTrue(
                ours.fontSize.value > default.fontSize.value,
                "$role is ${ours.fontSize} but Material's default is already ${default.fontSize}",
            )
        }
    }

    @Test
    fun `nothing the app renders is smaller than sixteen sp`() {
        // The floor, and the reason for the ADR: the completed-set line was a 12sp bodySmall,
        // which is what you are squinting at between sets.
        roles.forEach { (role, styles) ->
            assertTrue(styles.first.fontSize.value >= 16f, "$role is ${styles.first.fontSize}")
        }
    }

    @Test
    fun `the set list role is the size the ADR settled on`() {
        // ADR-0011's table. Changing these is a product decision, not a refactor.
        assertEquals(22.sp, GymTypography.titleMedium.fontSize, "the logged-set line")
        assertEquals(16.sp, GymTypography.bodySmall.fontSize)
        assertEquals(18.sp, GymTypography.labelLarge.fontSize, "button text")
        assertEquals(28.sp, GymTypography.titleLarge.fontSize)
    }

    @Test
    fun `sizes are in sp, so the system font-size setting still multiplies them`() {
        // A member who has already turned system text up gets larger text still. Expressing a
        // size in dp would silently cap them, which is the accessibility bug M7 exists to
        // catch — this scale must not introduce it.
        // isSp is a member of TextUnit, not a top-level function — importing it does not compile.
        roles.forEach { (role, styles) ->
            assertTrue(styles.first.fontSize.isSp, "$role is not expressed in sp")
        }
    }

    @Test
    fun `line height leaves room for the text it holds`() {
        roles.forEach { (role, styles) ->
            val style = styles.first
            assertTrue(
                style.lineHeight.value >= style.fontSize.value,
                "$role would clip: ${style.fontSize} text in a ${style.lineHeight} line",
            )
        }
    }
}
