package com.gymtracker.core.designsystem.component

import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Modernist design system's grayscale treatment, from the imported bundle's stylesheet:
 * `filter: grayscale(1) contrast(1.08)`. "Wrap hero and inline images in the `.grayscale`
 * class — they print in pure black and white"; "Do not tint or colorize imagery."
 *
 * [GrayscaleColorMatrix] is one affine transform implementing both steps at once — a
 * luminance-weighted desaturation, then a contrast stretch pivoted on mid-grey — rather than
 * composing two `ColorMatrix` values, so the numbers here are checked directly instead of
 * through `ColorMatrix.timesAssign`'s multiplication order.
 */
class GymPhotoTest {
    @Test
    fun `the R, G and B output rows are identical, which is what makes it grayscale`() {
        val values = GrayscaleColorMatrix.values
        val outputRed = values.copyOfRange(0, ROW_LENGTH).toList()
        val outputGreen = values.copyOfRange(ROW_LENGTH, 2 * ROW_LENGTH).toList()
        val outputBlue = values.copyOfRange(2 * ROW_LENGTH, 3 * ROW_LENGTH).toList()
        assertEquals(outputRed, outputGreen, "the grayscale output isn't grayscale if R and G differ")
        assertEquals(outputGreen, outputBlue, "the grayscale output isn't grayscale if G and B differ")
    }

    @Test
    fun `alpha passes through untouched`() {
        val values = GrayscaleColorMatrix.values
        val alphaRow = values.copyOfRange(3 * ROW_LENGTH, 4 * ROW_LENGTH)
        assertEquals(listOf(0f, 0f, 0f, 1f, 0f), alphaRow.toList())
    }

    @Test
    fun `the r, g and b coefficients on one output row sum to the contrast gain`() {
        // Luminance weights (0.213, 0.715, 0.072) sum to 1.0 before the contrast multiplier;
        // scaling the whole row by contrast is what "contrast(1.08)" applies on top of the
        // grayscale conversion.
        val values = GrayscaleColorMatrix.values
        val gain = values[0] + values[1] + values[2]
        assertNear(GRAYSCALE_CONTRAST, gain)
    }

    @Test
    fun `the contrast stretch pivots on mid-grey, per grayscale(1) contrast(1_08)`() {
        // newValue = (oldValue - 127.5) * contrast + 127.5, rearranged to oldValue * contrast
        // + translate. A pivot at 0 (no translate) would darken every midtone along with the
        // shadows, which is not what the design system's filter does.
        val values = GrayscaleColorMatrix.values
        val expectedTranslate = MID_GREY * (1f - GRAYSCALE_CONTRAST)
        assertNear(expectedTranslate, values[4])
        assertNear(expectedTranslate, values[ROW_LENGTH + 4])
        assertNear(expectedTranslate, values[2 * ROW_LENGTH + 4])
    }

    private fun assertNear(
        expected: Float,
        actual: Float,
    ) {
        assertTrue(abs(expected - actual) < TOLERANCE, "expected $expected, was $actual")
    }

    private companion object {
        const val ROW_LENGTH = 5
        const val GRAYSCALE_CONTRAST = 1.08f
        const val MID_GREY = 127.5f
        const val TOLERANCE = 0.0001f
    }
}
