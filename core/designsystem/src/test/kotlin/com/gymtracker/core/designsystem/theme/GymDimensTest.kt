package com.gymtracker.core.designsystem.theme

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ADR-0016's dp tokens, plus the two ADR-0019 added and nothing pinned before now: divider
 * thickness and the sub-[GymDimens.TightGap] spacing step. Pinned the same way
 * [GymTypographyTest] pins sizes — a product decision, not a refactor.
 */
class GymDimensTest {
    @Test
    fun `nothing tappable is smaller than the accessibility floor`() {
        assertEquals(48.dp, GymDimens.MinTouchTarget)
        assertTrue(GymDimens.StepperTarget >= GymDimens.MinTouchTarget)
        assertTrue(GymDimens.PrimaryAction >= GymDimens.MinTouchTarget)
    }

    @Test
    fun `the primary action is 72dp, per the redesign's sweaty-hands constraint`() {
        // Pinned, not just floored: `>= MinTouchTarget` above already passed at 64dp, which is
        // how the mismatch between this file and the redesign's constraints shipped unnoticed.
        // Raised from 64dp (ADR-0016's original value) to 72dp so the drift can't recur silently.
        assertEquals(72.dp, GymDimens.PrimaryAction)
    }

    @Test
    fun `the divider reads as a rule, not a hairline`() {
        // ADR-0019: a 1dp hairline is the first thing to disappear in bad gym lighting — the
        // same reasoning that made SecondaryActionButton tonal instead of outlined.
        assertEquals(2.dp, GymDimens.DividerThickness)
    }

    @Test
    fun `HairGap is the one step below TightGap, not a rename of it`() {
        assertEquals(4.dp, GymDimens.HairGap)
        assertTrue(GymDimens.HairGap < GymDimens.TightGap)
    }

    @Test
    fun `the spacing scale only gets tighter as the names get shorter`() {
        assertTrue(GymDimens.HairGap < GymDimens.TightGap)
        assertTrue(GymDimens.TightGap < GymDimens.Gap)
        assertTrue(GymDimens.Gap < GymDimens.ScreenPadding)
    }

    @Test
    fun `ADR-0029's structural rule is the same thickness as a divider, not a different weight`() {
        // The two rules differ in colour (solid ink vs outlineVariant), not thickness — this
        // pins that the token exists for the call site's readability, not a hidden size change.
        assertEquals(GymDimens.DividerThickness, GymDimens.StructuralRuleThickness)
    }

    @Test
    fun `ADR-0029's segment bar is pinned to the design's values`() {
        assertEquals(6.dp, GymDimens.SegmentHeight)
        assertEquals(GymDimens.HairGap, GymDimens.SegmentGap)
    }

    @Test
    fun `ADR-0030's hand-built navigation bar is pinned to the design's values`() {
        assertEquals(76.dp, GymDimens.NavigationBarHeight)
        assertEquals(24.dp, GymDimens.NavigationBarIconSize)
        assertTrue(GymDimens.NavigationBarHeight >= GymDimens.MinTouchTarget)
    }
}
