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

    /** A screen's one primary action: full width, and this tall. Sized to be hit without looking. */
    val PrimaryAction = 64.dp

    val ScreenPadding = 24.dp
    val Gap = 12.dp
    val TightGap = 8.dp

    /** Catalog thumbnails: big enough to recognise a machine from across the gym floor. */
    val Thumbnail = 72.dp
}
