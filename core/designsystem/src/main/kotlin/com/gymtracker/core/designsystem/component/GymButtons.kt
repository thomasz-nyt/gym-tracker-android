package com.gymtracker.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
 * ADR-0029: the session screen's one-tap log button — "LOG SET 3" over "100 lb × 8". The same
 * "at most one per screen" primary action as the single-string overload above, just stating
 * what it will do before doing it (US-35).
 *
 * The detail line reads through `titleMedium` — the design's own 21px for this line rounds to
 * 22sp, the size ADR-0011 already names "the logged-set line", and reusing it rather than
 * adding a sixth ADR-0029 role for a 1px difference keeps the role count down. `titleMedium`
 * stays at its deliberately-unbolded base weight (see `Type.kt`'s class doc) so [NumeralText]'s
 * digit-only bolding still creates contrast within the line, the same mechanism the set rows use.
 *
 * The design also dims the unit suffix within the detail line (`lb`, `· 45.4 kg`) to a smaller,
 * lower-opacity span; this button keeps the whole line at one size and colour instead — matching
 * every micro-span in a design is a much larger, easily-regressed surface than the digit-weight
 * contrast the design actually calls out as the priority.
 */
@Composable
fun PrimaryActionButton(
    eyebrow: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        // sizeIn, not a fixed height: the detail line carries both units ("135 lb × 8 · 61.2 kg")
        // and wraps to two lines on a narrow screen or at a large font scale. A fixed height
        // clipped the second line mid-glyph — the button grows instead, since 72dp is the floor
        // the sweaty-hands constraint asks for, not a ceiling.
        modifier = modifier.fillMaxWidth().sizeIn(minHeight = GymDimens.PrimaryAction),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            // 2dp, not a GymDimens role: this is the designsystem layer itself, where the token
            // system's raw values live, not feature code — see GymDimens's own doc comment.
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = eyebrow,
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        color = LocalContentColor.current.copy(alpha = EYEBROW_ALPHA),
                    ),
            )
            NumeralText(
                text = detail,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val EYEBROW_ALPHA = 0.8f

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
