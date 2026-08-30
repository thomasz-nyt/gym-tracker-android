package com.gymtracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.gymtracker.core.designsystem.component.PrimaryActionButton
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * Reported live, testing Turn 5 file `03`'s sub-piece 1: `GuidedExerciseScreen.kt`'s
 * `"Next: ${next.exercise.name}"` button, for a long exercise name ("Next: Incline Dumbbell"),
 * wrapped to two lines and then hard-cut the rest of the string — "Bench With Palms Facing In"
 * gone with no ellipsis, no sign anything was truncated. Reproduced on-device before this test
 * was written, not assumed. Traced to [PrimaryActionButton]'s single-string overload: its
 * `Button` is a **fixed** `.height(GymDimens.PrimaryAction)`, and the private `ButtonLabel` it
 * draws through set no `maxLines`, so the `Text` free-wraps to as many lines as the string needs
 * and the fixed-height container clips whatever doesn't fit. Every call site of this overload
 * was exposed to the same bug, not just the one that happened to get tapped — a shared-component
 * fix, which is why this test mounts the button directly (`createComposeRule`, no
 * `MainActivity`, no Hilt) rather than reproducing the exact guided-mode flow that surfaced it.
 *
 * The assertion compares a long label's rendered height against a short one's, rather than a
 * fixed dp value: proving both render at the *same* (single-line) height is what "truncates,
 * doesn't wrap" actually means, and stays correct regardless of exactly how tall one line is.
 *
 * No Hilt / no [com.gymtracker.app.MainActivity]: [PrimaryActionButton] needs nothing injected,
 * only [GymTrackerTheme] — the same minimal harness a `@Preview` gives it.
 *
 * **Width matters, and the first version of this test missed it.** Given the full screen width
 * (a bare `Column`, no sibling), the same long string doesn't wrap at all — nothing reproduces.
 * `GuidedExerciseScreen.kt`'s real layout puts this button at `Modifier.weight(1f)` inside a
 * `Row` beside a square "Stop here" button, which is the actual width constraint that forces the
 * wrap; [nextAndStop] copies that shape rather than a bare full-width button.
 */
class PrimaryActionButtonTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aLongLabelRendersAtTheSameHeightAsAShortOne() {
        // Both mounted in the same setContent call — a ComposeTestRule's Activity accepts only
        // one setContent per test, so this is a single tree with two rows rather than two
        // separate renders.
        compose.setContent {
            GymTrackerTheme {
                // GymDimens.ScreenPadding: ExerciseSummary's own root padding (GuidedExerciseScreen.kt)
                // — omitting it understates how narrow the Row really is and hid this bug once already.
                Column(modifier = Modifier.padding(GymDimens.ScreenPadding)) {
                    nextAndStop("Short")
                    nextAndStop("Next: Incline Dumbbell Bench With Palms Facing In")
                }
            }
        }

        // useUnmergedTree: Button's clickable semantics merge its children into one node by
        // default, so onNodeWithText without it resolves to the BUTTON's own (fixed, 64dp)
        // bounds in both cases regardless of internal wrapping — which is exactly why the first
        // two versions of this test passed against the unfixed, visibly-broken component.
        val shortBounds = compose.onNodeWithText("Short", useUnmergedTree = true).getBoundsInRoot()
        val shortHeight = (shortBounds.bottom - shortBounds.top).value
        val longBounds =
            compose.onNodeWithText("Next:", substring = true, useUnmergedTree = true).getBoundsInRoot()
        val longHeight = (longBounds.bottom - longBounds.top).value

        // Sub-pixel float tolerance, not exact equality — two independently measured single
        // lines of the same text style land within thousandths of a dp of each other, not
        // bit-identical.
        assertTrue(
            abs(shortHeight - longHeight) < 1f,
            "Expected the long label to truncate to one line, not wrap: short=${shortHeight}dp long=${longHeight}dp",
        )
    }

    /** `GuidedExerciseScreen.kt`'s exact "Next: .../Stop here" Row shape (lines 318-332). */
    @Composable
    private fun nextAndStop(nextLabel: String) {
        Row(horizontalArrangement = Arrangement.spacedBy(GymDimens.HairGap)) {
            PrimaryActionButton(text = nextLabel, onClick = {}, modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = {},
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.sizeIn(minHeight = GymDimens.PrimaryAction, minWidth = GymDimens.PrimaryAction),
            ) {
                Text("Stop here")
            }
        }
    }
}
