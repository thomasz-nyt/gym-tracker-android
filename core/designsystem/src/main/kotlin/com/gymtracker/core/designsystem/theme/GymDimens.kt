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

    /** ADR-0030: the hand-built bottom bar's height — taller than [MinTouchTarget] on purpose. */
    val NavigationBarHeight = 76.dp

    /** ADR-0030: each tab's icon in the hand-built bottom bar. */
    val NavigationBarIconSize = 24.dp

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

    /**
     * A result row on the catalog browse screen (US-12). Retuned 88dp → 80dp by ADR-0011's
     * Turn 4 amendment (frame `4a`) — safe to lower here rather than as a new token, because
     * `BrowseScreen.kt` is this token's only reader.
     */
    val CatalogRowHeight = 80.dp

    /** Room for the "Done · N added" floating button so the last browse result clears it. */
    val FabClearance = 88.dp

    /**
     * A ruled list row's floor (US-38): big enough for a two-line row plus its badge to sit
     * comfortably above [MinTouchTarget], matching the design's own workout-log and exercise-log
     * row rhythm.
     */
    val MinListRowHeight = 72.dp

    /**
     * `RepMascot`'s height on Train home (US-43, ADR-0035): a hero size, not an inline mark —
     * this is the one screen where Rep is the only thing in the frame. `NoSession`'s weighted
     * middle band has room for this even on CI's 320x640 emulator (`testing-strategy.md`): the
     * top button row and bottom action stack together claim well under half that height, and
     * this shares the remainder with a single line of "next up" text, not competing with it for
     * a fixed budget. It must still not push the action stack up (ADR-0016's bottom-weighting) —
     * `NoSession`'s own weighted `Column` is what guarantees that, not this value.
     *
     * Retuned 2026-08-17, from 140dp to 128dp (ADR-0035's Turn 3 amendment): `RepMascotGeometry`'s
     * viewBox is now cropped to the figure's own ink, and `RepMascot` sizes its `Canvas` by this
     * height alone (width follows from the crop's aspect ratio) rather than a square box with
     * margin baked in — so an unchanged 140dp would have drawn Rep taller on screen than before,
     * not the same size in a tighter box. 128dp keeps the hero comfortably the largest placement
     * in the app while landing close to what 140dp's old box actually drew.
     */
    val MascotHome = 128.dp

    /**
     * `RepMascot`'s height next to other content — exercise detail, and the guided screen's
     * rest/complete states. Bigger than [Thumbnail] on purpose (a "mark beside the name" should
     * read as more than a catalog icon), but capped well below [MascotHome].
     *
     * Retuned 2026-08-17, from 88dp to 80dp (ADR-0035's Turn 3 amendment, alongside
     * [MascotHome]): this token now sizes `RepMascot` by height directly rather than a square
     * box most of which drew nothing, so the same numeral would have drawn Rep bigger than
     * before, not merely tighter. **The warm-up panel no longer reads this token** — Turn 3's
     * `3a` gives Rep his own `StepperTarget`-height row beside "Done" instead, which is the fix
     * for the overflow this token's old doc described ("104dp measured on device with not
     * enough width left for 'Done' to stay on one line"); that finding no longer applies to
     * this value.
     */
    val MascotInline = 80.dp

    /**
     * ADR-0011's Turn 4 amendment: five tokens for the six screens that pass moves to the new
     * [GymTextRoles] scale, each a **new name**, not [ScreenPadding] or [Thumbnail] repointed —
     * both of those are read by files this pass does not touch, and lowering them in place
     * would reflow every one as a side effect. See the amendment's "An additive scale, not a
     * value change" for the full reasoning; it applies to dp the same way it applies to sp.
     */
    val CompactScreenPadding = 20.dp

    /** The picker row's thumbnail (frame `4a`), replacing [Thumbnail] there only. */
    val CatalogThumbnail = 56.dp

    /**
     * The picker row's fixed-width trailing cell (frame `4a`): a `tag.caps` "ADDED" label or a
     * `+` button, whichever the row needs, always the same width — so adding an exercise never
     * changes the name column's width and never reflows the row.
     */
    val AddExerciseCellWidth = 54.dp

    /** The `+` button inside [AddExerciseCellWidth] (frame `4a`). */
    val AddExerciseButtonHeight = 44.dp

    /**
     * The one-tap log button's floor once its eyebrow/detail overload becomes two fixed lines
     * (frame `4b`/`4c`) rather than a variable-height sentence — [PrimaryAction] (72dp) is
     * unchanged and still the floor for every single-line primary button elsewhere in the app.
     */
    val LogRowHeight = 64.dp

    /**
     * The warm-up row, once it stops floating as loose text (frame `4c`).
     *
     * The frame's own number is 44dp; [MinTouchTarget] (48dp) wins here instead, per this
     * file's own class doc ("nothing tappable is smaller than this, anywhere") — the same
     * accessibility floor over frame-literal-value trade [PrimaryAction]'s doc already made
     * once. Same value as [MinTouchTarget], own name for readability at the call site, the
     * [StructuralRuleThickness] precedent below.
     */
    val WarmUpRowHeight = MinTouchTarget

    /** Below this width, the finish dialog's two buttons stack rather than shrink (frame `4e`). */
    val StackedButtonsBreakpoint = 340.dp
}
