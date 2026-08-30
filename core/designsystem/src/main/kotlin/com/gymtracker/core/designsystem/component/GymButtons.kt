package com.gymtracker.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymTextRoles

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
 * **Retuned by ADR-0011's Turn 4 amendment.** [eyebrow] and [detail] now read through
 * [GymTextRoles.TagCaps] and [GymTextRoles.TitleMd] rather than `labelSmall`/`titleMedium` —
 * `title.md` is weight 800 (frame `4f`'s own table names it for exactly this line), so
 * [NumeralText]'s digit-only bolding, which this KDoc used to describe here, would draw nothing
 * extra on it; that mechanism is what the amendment's split baseline row (`GymLoadRow`) replaces
 * on load lines generally, and [detail] is always a single already-composed string like
 * `"135 lb × 8"`, never itself drawn as separate baseline pieces. The kg conversion this KDoc
 * used to mention dimming inline (`"· 45.4 kg"`) is withdrawn from this surface entirely
 * (ADR-0008's Turn 4 amendment), so there is no longer a secondary span to dim.
 *
 * **[outlined] (ADR-0036).** The rest countdown's final-ten-seconds swap needs this exact button
 * built two ways: filled while the countdown is calm, stepped back to outlined the moment the
 * countdown itself takes the accent fill — "exactly one filled accent element" holding true
 * through the swap, not just around it. Outlined reuses the same unstyled `OutlinedButton` idiom
 * every other outlined control in this codebase already uses (`Done`, `Add set`, `SKIP REST`)
 * rather than hand-matching the design's literal ink-coloured border.
 *
 * **Floor retuned 72dp → 64dp by ADR-0011's Turn 4 amendment**, then unified with
 * [GymDimens.PrimaryAction] itself by ADR-0044 (Turn 5): both overloads now read the same
 * 64dp floor, so the log button is no longer shorter than a less-frequent primary action. The
 * dedicated `LogRowHeight` token this KDoc used to name is retired — a second name for the same
 * number as [GymDimens.PrimaryAction] had no reader left to serve once the two floors matched.
 */
@Composable
fun PrimaryActionButton(
    eyebrow: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
) {
    // sizeIn, not a fixed height: a large font scale can still push two short lines past 64dp,
    // and the button should grow rather than clip — 64dp is the floor, not a ceiling.
    val buttonModifier = modifier.fillMaxWidth().sizeIn(minHeight = GymDimens.PrimaryAction)
    val label: @Composable RowScope.() -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            // 2dp, not a GymDimens role: this is the designsystem layer itself, where the token
            // system's raw values live, not feature code — see GymDimens's own doc comment.
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            GymText(
                text = eyebrow,
                role = GymTextRoles.TagCaps,
                color = LocalContentColor.current.copy(alpha = EYEBROW_ALPHA),
            )
            GymText(text = detail, role = GymTextRoles.TitleMd, modifier = Modifier.fillMaxWidth())
        }
    }

    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = MaterialTheme.shapes.large,
            modifier = buttonModifier,
            content = label,
        )
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = MaterialTheme.shapes.large,
            modifier = buttonModifier,
            content = label,
        )
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
        // Reported live: a long label (GuidedExerciseScreen.kt's "Next: <exercise name>", in the
        // width-constrained Row it shares with "Stop here") wrapped to two lines and the fixed-
        // height Button clipped the rest with no ellipsis — see PrimaryActionButtonTest.
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}
