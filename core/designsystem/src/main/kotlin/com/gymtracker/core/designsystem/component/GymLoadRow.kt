package com.gymtracker.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymTextRole

/**
 * A load line as a baseline `Row` of separate `Text`s, rather than one formatted string
 * (ADR-0011's Turn 4 amendment, cause 4 of the wrapping bug it diagnoses): digits at
 * [numeralRole], words at [wordRole]. This is what makes `100 lb × 12` and `Bodyweight × 12`
 * both fit on one line with no autosizing and no measuring — `Bodyweight` is eleven characters
 * and cannot make one line at a numeral role's size at 320dp × 1.3 font scale at any gutter,
 * which is exactly the wrap this component exists to prevent.
 *
 * Domain-agnostic on purpose: `:core:designsystem` carries no dependency on `:core:domain`
 * (checked against every other file in this package before adding one), so this takes
 * `number`/`unit`/`wordFallback` as plain strings — the pieces of
 * `com.gymtracker.core.domain.units.WeightFormatter`'s `WeightDisplay` a call site already has —
 * rather than the domain type itself.
 *
 * @param number the bare digits (e.g. `"135"`), or null for a bodyweight movement — mirrors
 *   `WeightDisplay.number`.
 * @param unit the unit suffix (e.g. `"lb"`), null exactly when [number] is null — mirrors
 *   `WeightDisplay.unit`.
 * @param wordFallback drawn at [wordRole] instead of [number]/[unit] when [number] is null —
 *   the literal word "Bodyweight", mirroring `WeightDisplay.primary` in that state.
 * @param reps drawn at [numeralRole] after a `×` — a rep count is always digits, never a word.
 */
@Composable
fun GymLoadRow(
    number: String?,
    unit: String?,
    wordFallback: String,
    reps: String,
    numeralRole: GymTextRole,
    wordRole: GymTextRole,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(GymDimens.HairGap),
    ) {
        if (number != null && unit != null) {
            GymText(text = number, role = numeralRole, color = color)
            GymText(text = unit, role = wordRole, color = color)
        } else {
            GymText(text = wordFallback, role = wordRole, color = color)
        }
        GymText(text = "×", role = wordRole, color = color)
        GymText(text = reps, role = numeralRole, color = color)
    }
}
