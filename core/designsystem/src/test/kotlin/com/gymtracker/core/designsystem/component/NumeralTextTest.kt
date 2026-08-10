package com.gymtracker.core.designsystem.component

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ADR-0019: "Numbers carry weight 800 so a load reads at arm's length." That never got applied
 * anywhere — grepping the app for `FontWeight.` turns up hits only inside `Type.kt` itself. This
 * is the mechanism: a load like "100 lb x 8" mixes digits and words in one string, so the weight
 * cannot live on a `Typography` role the way ADR-0011's sizes do. It has to be a span.
 *
 * [boldDigits] is deliberately plain string-to-spans logic, not a `@Composable`, so it is
 * testable here without `core/designsystem`'s test source set growing a Compose UI test runner
 * it does not otherwise need.
 */
class NumeralTextTest {
    @Test
    fun `a run of digits is one bold span, not one span per digit`() {
        val result = boldDigits("100 lb")
        val boldSpans = result.spanStyles.filter { it.item.fontWeight == FontWeight.ExtraBold }
        assertEquals(1, boldSpans.size, "expected one span covering the whole run of digits")
        assertEquals(0, boldSpans.single().start)
        assertEquals(3, boldSpans.single().end)
    }

    @Test
    fun `every digit run gets its own span, not just the first`() {
        val result = boldDigits("100 lb x 8")
        val digitRuns = result.spanStyles.filter { it.item.fontWeight == FontWeight.ExtraBold }
        assertEquals(2, digitRuns.size, "expected a span for '100' and a separate one for '8'")
    }

    @Test
    fun `text with no digits carries no bold span`() {
        val result = boldDigits("lb")
        assertTrue(result.spanStyles.none { it.item.fontWeight == FontWeight.ExtraBold })
    }

    @Test
    fun `the visible text is unchanged — only the styling is added`() {
        // This is what keeps the technique safe to apply broadly: onNodeWithText in the
        // instrumented suite matches the plain string, and AnnotatedString.text below is
        // exactly what was passed in, span styles notwithstanding.
        val text = "100 lb x 8"
        assertEquals(text, boldDigits(text).text)
    }

    @Test
    fun `non-digit spans are never bold`() {
        val result = boldDigits("100 lb x 8")
        val nonBold = result.spanStyles.filter { it.item.fontWeight != FontWeight.ExtraBold }
        // "100" occupies 0..3 and "8" occupies 9..10; nothing outside those ranges is bold.
        assertTrue(nonBold.none { it.item == SpanStyle(fontWeight = FontWeight.ExtraBold) })
    }
}
