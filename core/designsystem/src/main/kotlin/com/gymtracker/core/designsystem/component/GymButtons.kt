package com.gymtracker.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import com.gymtracker.core.designsystem.theme.GymDimens

/**
 * The one action a screen exists for (ADR-0016): full width, [GymDimens.PrimaryAction] tall,
 * in the accent colour.
 *
 * There is at most one of these per screen, and it is the screen's most *frequent* action
 * rather than its most important-sounding one — Add set, not Finish workout.
 */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().height(GymDimens.PrimaryAction),
    ) {
        ButtonLabel(text, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * An action worth a full-width target but not the accent: "Past workouts", "Browse exercises".
 *
 * Tonal rather than outlined so it still reads as a button at arm's length — a hairline border
 * is the first thing to disappear in bad light.
 */
@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().sizeIn(minHeight = GymDimens.MinTouchTarget),
    ) {
        ButtonLabel(text, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * ADR-0019: button labels sit flush left at the padding edge, weight 800, with a little
 * letter-spacing — never centred, including on the full-width buttons above.
 *
 * **Deliberately not visually uppercased.** `TwoTapSetLoggingTest` and `CorrectingASetTest`
 * match button labels case-sensitively (`onNodeWithText("Add set")`, `onNodeWithText("Save
 * set")`) because a `Text` composable's drawn string and its semantics string are the same
 * value — there is no supported way to draw "ADD SET" while keeping "Add set" as what the
 * instrumented suite (and TalkBack) reads, short of a `clearAndSetSemantics` override this repo
 * has no precedent for and that trades a broad, hard-to-verify risk to the two-tap tripwire for
 * a stylistic detail. [FontWeight.ExtraBold], the letter-spacing and the flush-left alignment
 * below are pure [androidx.compose.ui.text.TextStyle] changes and carry none of that risk.
 */
@Composable
private fun ButtonLabel(
    text: String,
    style: TextStyle,
) {
    Text(
        text = text,
        style = style.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.05.em),
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}
