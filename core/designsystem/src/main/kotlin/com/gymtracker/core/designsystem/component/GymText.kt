package com.gymtracker.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import com.gymtracker.core.designsystem.theme.GymTextRole

/**
 * A [Text] that gets its `maxLines` from its [role] rather than from the call site (ADR-0011's
 * Turn 4 amendment). This is the mechanism that makes the ceiling belong to the role: a screen
 * names [GymTextRoles][com.gymtracker.core.designsystem.theme.GymTextRoles].`TitleMd` and gets
 * the 2-line cap for free, the same way it already gets the size and weight for free — there is
 * no `Text(style = …)` spelling of a role that forgets the ceiling, because there is no `Text`
 * call at all.
 *
 * @param semantics an escape hatch for the handful of call sites that already override the
 *   accessible name with [androidx.compose.ui.semantics.contentDescription] — matching
 *   `NumeralText`'s and the plain `Text` calls it replaces on this exact point, not a new pattern.
 */
@Composable
fun GymText(
    text: String,
    role: GymTextRole,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    semantics: (SemanticsPropertyReceiver.() -> Unit)? = null,
) {
    Text(
        text = text,
        style = role.style,
        color = color,
        maxLines = role.maxLines,
        overflow = role.overflow,
        modifier = if (semantics != null) modifier.semantics(properties = semantics) else modifier,
    )
}
