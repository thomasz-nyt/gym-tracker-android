package com.gymtracker.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
    fun `every role is set in Archivo, not the platform default`() {
        // ADR-0019 replaces Roboto with Archivo. Setting it on one role and forgetting the rest
        // is the failure this catches — a typeface that applies to three quarters of a screen
        // looks like a rendering bug rather than a decision.
        roles.forEach { (role, styles) ->
            assertEquals(ArchivoFamily, styles.first.fontFamily, "$role is not set in Archivo")
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

    @Test
    fun `titles carry ExtraBold, so a screen reads with a real weight hierarchy`() {
        // Titles and section labels — pure words, never mixed with a number the way
        // titleMedium is — so raising the base weight costs nothing and gives the screen
        // contrast beyond size alone.
        assertEquals(FontWeight.ExtraBold, GymTypography.titleLarge.fontWeight)
        assertEquals(FontWeight.ExtraBold, GymTypography.titleSmall.fontWeight)
    }

    @Test
    fun `meta text carries Medium, one step below a title`() {
        assertEquals(FontWeight.Medium, GymTypography.bodySmall.fontWeight)
        assertEquals(FontWeight.Medium, GymTypography.bodyMedium.fontWeight)
        assertEquals(FontWeight.Medium, GymTypography.bodyLarge.fontWeight)
    }

    @Test
    fun `titleMedium stays off the ExtraBold hierarchy, so NumeralText's digit spans still read as bolder`() {
        // titleMedium is what LoggedSets and RestPanel's "Up next" render — lines that mix
        // words and a number in one string. NumeralText creates its contrast by bolding only
        // the digit runs within such a line; if the line's own base weight already matched
        // ExtraBold, that span would draw nothing extra and the two would be indistinguishable.
        assertNotEquals(FontWeight.ExtraBold, GymTypography.titleMedium.fontWeight)
    }

    @Test
    fun `ADR-0029's five new roles are pinned to the design's exact pixel values`() {
        assertEquals(104.sp, GymTypography.displayLarge.fontSize, "the rest countdown")
        assertEquals(FontWeight.ExtraBold, GymTypography.displayLarge.fontWeight)
        assertEquals(44.sp, GymTypography.headlineMedium.fontSize, "the rest banner's weight readout")
        assertEquals(FontWeight.ExtraBold, GymTypography.headlineMedium.fontWeight)
        assertEquals(27.sp, GymTypography.headlineSmall.fontSize, "a movement name")
        assertEquals(FontWeight.ExtraBold, GymTypography.headlineSmall.fontWeight)
        assertEquals(13.sp, GymTypography.labelMedium.fontSize, "SET n / NEXT row labels")
        assertEquals(FontWeight.Bold, GymTypography.labelMedium.fontWeight)
        assertEquals(12.sp, GymTypography.labelSmall.fontSize, "section eyebrows")
        assertEquals(FontWeight.Bold, GymTypography.labelSmall.fontWeight)
    }

    @Test
    fun `displayMedium stays at Material's size, because no role reads it`() {
        // ADR-0029 gave the countdown its own role (displayLarge) rather than resizing this one;
        // ADR-0033 later moved GuidedExerciseScreen's countdown to displayLarge too, so this slot
        // is unclaimed rather than protected. Left at Material's default rather than pre-committed
        // to a screen that does not exist, and still carries Archivo (below) so an accidental
        // future use does not silently fall back to Roboto.
        assertEquals(material.displayMedium.fontSize, GymTypography.displayMedium.fontSize)
        assertEquals(ArchivoFamily, GymTypography.displayMedium.fontFamily)
    }

    @Test
    fun `ADR-0029's new roles carry Archivo too`() {
        assertEquals(ArchivoFamily, GymTypography.displayLarge.fontFamily)
        assertEquals(ArchivoFamily, GymTypography.headlineMedium.fontFamily)
        assertEquals(ArchivoFamily, GymTypography.headlineSmall.fontFamily)
        assertEquals(ArchivoFamily, GymTypography.labelMedium.fontFamily)
        assertEquals(ArchivoFamily, GymTypography.labelSmall.fontFamily)
    }
}
