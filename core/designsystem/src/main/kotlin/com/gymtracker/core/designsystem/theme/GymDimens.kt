package com.gymtracker.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * The app's touch and spacing tokens (ADR-0016).
 *
 * The dp counterpart of ADR-0011's rule about `sp`: **feature code never hard-codes a touch
 * size.** Every screen had its own private `MIN_TOUCH_TARGET = 48.dp` before this file existed,
 * which meant four places to edit and four chances to disagree. M7's accessibility pass tunes
 * one file.
 */
object GymDimens {
    /** The accessibility floor. Nothing tappable is smaller than this, anywhere. */
    val MinTouchTarget = 48.dp

    /** A stepper's +/− button: pressed repeatedly, one-handed, so larger than the floor. */
    val StepperTarget = 56.dp

    /**
     * A screen's one primary action: full width, and this tall. Sized to be hit without looking.
     *
     * Raised from ADR-0016's original 64dp to 72dp (redesign audit, "sweaty hands, phone at
     * arm's length") — see `GymDimensTest`'s pinned-value test, added because `>= MinTouchTarget`
     * alone let 64dp and 72dp look identical to the suite.
     */
    val PrimaryAction = 72.dp

    val ScreenPadding = 24.dp
    val Gap = 12.dp
    val TightGap = 8.dp

    /** The one step below [TightGap]: a label and the row directly under it, nothing looser. */
    val HairGap = 4.dp

    /** Catalog thumbnails: big enough to recognise a machine from across the gym floor. */
    val Thumbnail = 72.dp

    /**
     * A divider (ADR-0019): thick enough to survive bad gym lighting. A 1dp Material hairline
     * is the first thing to disappear under it — the same reasoning that made
     * [com.gymtracker.core.designsystem.component.SecondaryActionButton] tonal instead of
     * outlined.
     */
    val DividerThickness = 2.dp

    /**
     * A structural rule (ADR-0029): the heavier of the two weights the session screen draws
     * with, under the header. [DividerThickness] is the same thickness deliberately — the two
     * differ in colour, not weight (solid ink here, [outlineVariant][ColorScheme.outlineVariant]
     * for a row rule), so this token exists for readability at the call site, not a different
     * number.
     */
    val StructuralRuleThickness = DividerThickness

    /** ADR-0029: one bar in the session header's segment indicator. */
    val SegmentHeight = 6.dp

    /**
     * The gap between segment-bar bars (ADR-0029). The design's own value is 3dp; this reuses
     * [HairGap] (4dp) rather than adding a token for a 1dp difference nothing else needs.
     */
    val SegmentGap = HairGap

    /** ADR-0029: the fixed-width label column a set row and a still-to-come row both align to. */
    val RowLabelWidth = 44.dp

    /**
     * The exercise detail screen's full-width hero photo (ADR-0007, ADR-0014). Redesign audit,
     * PR A finding 4: this and the four tokens below used to be private `= N.dp` vals in the
     * feature module that drew them, which is exactly the rule this file's own class doc names.
     */
    val PhotoHeight = 220.dp

    /** US-16's trend chart on the exercise progress screen. */
    val ChartHeight = 240.dp

    /** One muscle's bar in the weekly volume screen (US-17). */
    val VolumeBarHeight = 12.dp

    /** A result row on the catalog browse screen (US-12). */
    val CatalogRowHeight = 88.dp

    /** Room for the "Done · N added" floating button so the last browse result clears it. */
    val FabClearance = 88.dp
}
