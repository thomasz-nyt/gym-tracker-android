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

    @Test
    fun `the five sizes that used to be raw dp in feature code are now named tokens`() {
        // Redesign audit, PR A finding 4: ADR-0011's "feature code never names a raw sp" rule
        // has a dp counterpart (this file's own class doc), and five call sites broke it —
        // ExerciseDetailScreen's photo, ExerciseProgressScreen's chart, WeeklyVolumeScreen's
        // bar, and BrowseScreen's row height and FAB clearance. Pinned here so a future private
        // `= 88.dp` in feature code has somewhere it should have looked first.
        assertEquals(220.dp, GymDimens.PhotoHeight)
        assertEquals(240.dp, GymDimens.ChartHeight)
        assertEquals(12.dp, GymDimens.VolumeBarHeight)
        assertEquals(88.dp, GymDimens.FabClearance)
    }

    @Test
    fun `the catalog row is 80dp, retuned by ADR-0011's Turn 4 amendment from 88dp`() {
        // BrowseScreen is the sole reader of this token (grep-verified), so lowering it here
        // cannot reflow a screen this pass does not touch — the same reasoning that keeps the
        // other four Turn 4 tokens below as new, additive names rather than repointed old ones.
        assertEquals(80.dp, GymDimens.CatalogRowHeight)
    }

    @Test
    fun `a ruled list row is at least as tall as the accessibility floor`() {
        assertEquals(72.dp, GymDimens.MinListRowHeight)
        assertTrue(GymDimens.MinListRowHeight >= GymDimens.MinTouchTarget)
    }

    @Test
    fun `US-43's mascot has a hero size on Train home and a larger-than-Thumbnail inline size`() {
        // Retuned 2026-08-17 (ADR-0035's Turn 3 amendment): both tokens now size RepMascot by
        // height, not by a square box with empty margin baked in, so a value carried over
        // unchanged from before the viewBox crop would draw Rep noticeably bigger on screen than
        // it used to for the same number. 140dp/88dp were the pre-crop box sizes; 128dp/80dp are
        // what those settled to on device — see the roadmap's Turn 3 entry.
        assertEquals(128.dp, GymDimens.MascotHome)
        assertEquals(80.dp, GymDimens.MascotInline)
        // Deliberately bigger than a catalog thumbnail now: Rep shares his rows with a short
        // label or a single-line name, not a photo grid, so there is headroom to spare.
        assertTrue(GymDimens.MascotInline > GymDimens.Thumbnail)
        assertTrue(GymDimens.MascotHome > GymDimens.MascotInline)
    }

    @Test
    fun `ADR-0011's Turn 4 amendment adds five tokens, all new names rather than repointed old ones`() {
        // Each backs a value the redesign's frames pin, scoped to the one or two migrated call
        // sites that read it — see the amendment's "An additive scale, not a value change" for
        // why these are new tokens (CompactScreenPadding, CatalogThumbnail) rather than
        // ScreenPadding/Thumbnail repointed, which would reflow the fourteen and two other
        // files (respectively) that read those two tokens unchanged.
        assertEquals(20.dp, GymDimens.CompactScreenPadding)
        assertTrue(GymDimens.CompactScreenPadding < GymDimens.ScreenPadding)
        assertEquals(56.dp, GymDimens.CatalogThumbnail)
        assertEquals(54.dp, GymDimens.AddExerciseCellWidth)
        assertEquals(44.dp, GymDimens.AddExerciseButtonHeight)
        assertTrue(GymDimens.AddExerciseButtonHeight < GymDimens.AddExerciseCellWidth)
        assertEquals(64.dp, GymDimens.LogRowHeight)
        assertTrue(GymDimens.LogRowHeight < GymDimens.PrimaryAction)
        // The frame's own number is 44dp; MinTouchTarget (48dp) wins, per this file's own
        // "nothing tappable is smaller than this, anywhere" rule.
        assertEquals(GymDimens.MinTouchTarget, GymDimens.WarmUpRowHeight)
        assertEquals(340.dp, GymDimens.StackedButtonsBreakpoint)
        assertEquals(14.dp, GymDimens.MetricFlowRowGapHorizontal)
        assertEquals(6.dp, GymDimens.MetricFlowRowGapVertical)
    }
}
