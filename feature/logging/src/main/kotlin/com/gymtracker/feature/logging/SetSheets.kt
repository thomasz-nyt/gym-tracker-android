package com.gymtracker.feature.logging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.gymtracker.core.designsystem.component.PrimaryActionButton
import com.gymtracker.core.designsystem.component.StepperField
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.domain.set.RpeFormatter
import com.gymtracker.core.domain.units.UnitConverter
import com.gymtracker.core.domain.units.WeightFormatter
import com.gymtracker.core.domain.units.WeightUnit
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Set entry (US-03), as a bottom sheet with a stepper on every number (ADR-0016).
 *
 * Weight and reps arrive prefilled from the member's last set of this exercise, so when the
 * numbers are already right — which is most of the time — confirming is still a single tap and
 * the two-tap path is untouched. What changed is the case where they are *nearly* right: one
 * plate up is now a press rather than a keyboard.
 *
 * A sheet rather than a centred dialog because this is the screen you use with one hand, mid
 * workout, and the bottom of the phone is where that hand already is.
 *
 * **"Save set" is pinned outside the scrolling area.** Three steppers, a supporting line and an
 * RPE field are taller than the sheet on a normal phone, so with everything in one scrolling
 * column the confirm button opened below the fold: tap, *scroll*, tap. The instrumented test
 * still passed — it drives the semantics tree, which does not care what is on screen — so this
 * was only visible with the app in front of me. Constitution §2 makes it a bug, not a nit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetEntrySheet(
    entry: SetEntry,
    unit: WeightUnit,
    callbacks: SetEntryCallbacks,
) {
    ModalBottomSheet(
        onDismissRequest = callbacks.onDismiss,
        // Straight to full height. A sheet left at its half-open default opens showing weight
        // and reps but not the button that saves them, which turns the two-tap path into
        // tap–scroll–tap. Skipping the partial state is what actually keeps US-03's promise;
        // pinning the button below only helps once the sheet is tall enough to show it.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        SetEntryFields(
            entry = entry,
            unit = unit,
            callbacks = callbacks,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = GymDimens.ScreenPadding),
        )

        // Outside the scroll, so it is on screen the moment the sheet opens. When the prefill
        // is already right, this is the second of the two taps (US-03) and nothing may come
        // between the thumb and it.
        PrimaryActionButton(
            text = "Save set",
            onClick = callbacks.onConfirm,
            // The same predicate SetEntryController.confirm() checks before writing anything —
            // see SetEntry.canSave's doc. Reps/sets alone used to gate this button, so an
            // unparseable weight or RPE left it enabled and tapping it silently did nothing.
            enabled = entry.canSave(),
            modifier =
                Modifier
                    .padding(horizontal = GymDimens.ScreenPadding)
                    .padding(top = GymDimens.Gap, bottom = GymDimens.ScreenPadding),
        )
    }
}

/**
 * Correcting a set already logged (US-04), in the same sheet shape as set entry.
 *
 * Two differences from [SetEntrySheet], both deliberate:
 *
 * - **No "Sets" field.** Logging can write three identical rows at once (ADR-0009); correcting
 *   is always about the one row you tapped, and a repeat count here would mean "turn this set
 *   into three".
 * - **"Delete set" lives here**, and nowhere else. ADR-0019 replaced ADR-0016's rule that red
 *   means destructive — red is the accent now — with a structural one: a destructive control
 *   never shares a surface with a save, and is outlined rather than filled. So delete is not on
 *   the set row, not on the card next to "Add set", and is the only outlined thing in the sheet.
 *
 * Internal rather than private: [WorkoutDetailScreen]'s route uses this same sheet for a set
 * from a past workout (ADR-0022, US-04's third criterion) — one editor regardless of which
 * screen a set was tapped from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetEditSheet(
    edit: SetEdit,
    unit: WeightUnit,
    callbacks: SetEditCallbacks,
) {
    ModalBottomSheet(
        onDismissRequest = callbacks.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        SetEditFields(
            edit = edit,
            unit = unit,
            callbacks = callbacks,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = GymDimens.ScreenPadding),
        )

        // Pinned outside the scroll for the reason set entry's is: a sheet that opens showing
        // the numbers but not the button that saves them is a scroll nobody asked for.
        PrimaryActionButton(
            text = "Save changes",
            onClick = callbacks.onSave,
            // See SetEntry.canSave's doc: reps alone used to gate this button, so an unparseable
            // weight or RPE left it enabled and tapping it silently did nothing.
            enabled = edit.canSave(),
            modifier =
                Modifier
                    .padding(horizontal = GymDimens.ScreenPadding)
                    .padding(top = GymDimens.Gap),
        )

        OutlinedButton(
            onClick = callbacks.onDelete,
            // Square, like every other control: `OutlinedButton` reads `CornerFull` rather than
            // the shape scale, so ADR-0019's radius-0 does not reach it on its own. See Shape.kt.
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = GymDimens.MinTouchTarget)
                    .padding(horizontal = GymDimens.ScreenPadding)
                    .padding(top = GymDimens.TightGap, bottom = GymDimens.ScreenPadding),
        ) {
            Text("Delete set")
        }
    }
}

/** The editor's fields: the same numbers as entry, minus the repeat count. */
@Composable
private fun SetEditFields(
    edit: SetEdit,
    unit: WeightUnit,
    callbacks: SetEditCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GymDimens.Gap)) {
        Text(edit.exerciseName, style = MaterialTheme.typography.titleLarge)
        Text("Set ${edit.set.setIndex}", style = MaterialTheme.typography.bodyMedium)

        StepperField(
            label = "Weight (${unit.name.lowercase()})",
            value = edit.weight,
            onValueChange = callbacks.onWeightChanged,
            onStep = callbacks.onWeightStepped,
            placeholder = "Bodyweight",
            supporting = edit.weight.otherUnit(unit),
            keyboardType = KeyboardType.Decimal,
        )

        StepperField(
            label = "Reps",
            value = edit.reps,
            onValueChange = callbacks.onRepsChanged,
            onStep = callbacks.onRepsStepped,
        )

        RpeChips(selected = edit.rpe, onSelected = callbacks.onRpeChanged)
    }
}

/** What the editor can do, gathered up like [SetEntryCallbacks]. */
internal data class SetEditCallbacks(
    val onWeightChanged: (String) -> Unit,
    val onWeightStepped: (Int) -> Unit,
    val onRepsChanged: (String) -> Unit,
    val onRepsStepped: (Int) -> Unit,
    val onRpeChanged: (String) -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
    val onDismiss: () -> Unit,
) {
    companion object {
        /** For previews and for callers that only render the session behind the sheet. */
        val Inert = SetEditCallbacks({}, {}, {}, {}, {}, {}, {}, {})
    }
}

/** Everything in the sheet that scrolls: the numbers, and the optional RPE. */
@Composable
private fun SetEntryFields(
    entry: SetEntry,
    unit: WeightUnit,
    callbacks: SetEntryCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GymDimens.Gap)) {
        Text(entry.exerciseName, style = MaterialTheme.typography.titleLarge)

        if (!entry.prefilled) {
            Text("First time logging this one.", style = MaterialTheme.typography.bodyMedium)
        } else if (entry.fromHistory && entry.lastPerformedAt != null) {
            // US-37 (ADR-0031): only when the prefill came from a real past set — a target
            // already renders labelled as one, elsewhere on this same sheet, so it needs no
            // second line saying so here.
            Text(
                text = "Prefilled from ${entry.lastPerformedAt.asDay()} — ${entry.prefillSummary(unit)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        StepperField(
            label = "Weight (${unit.name.lowercase()})",
            value = entry.weight,
            onValueChange = callbacks.onWeightChanged,
            onStep = callbacks.onWeightStepped,
            placeholder = "Bodyweight",
            // The other unit, live, so nobody converts in their head between sets (ADR-0008).
            supporting = entry.weight.otherUnit(unit),
            keyboardType = KeyboardType.Decimal,
        )

        StepperField(
            label = "Reps",
            value = entry.reps,
            onValueChange = callbacks.onRepsChanged,
            onStep = callbacks.onRepsStepped,
        )

        StepperField(
            label = "Sets",
            value = entry.sets,
            onValueChange = callbacks.onSetsChanged,
            onStep = callbacks.onSetsStepped,
            supporting = "Records this many identical sets.",
        )

        RpeChips(selected = entry.rpe, onSelected = callbacks.onRpeChanged)
    }
}

/**
 * RPE as one tap, not a keyboard (US-60).
 *
 * US-03 left RPE a plain decimal field because it is "typed occasionally and deliberately" — and
 * then it was typed almost never, because a keyboard between sets is exactly the cost ADR-0016
 * built steppers to avoid. The valid values are eleven half steps from 5 to 10
 * ([RpeFormatter.scale]), which is a row of chips, not a number to type: tap one to record it, tap
 * it again to clear it. Blank stays blank — not recorded is not a claim the set was easy
 * (constitution §2.4). [selected] is the sheet's own string, so a stored `8.0` and a tapped `8`
 * are compared as numbers, not spellings.
 *
 * **Each chip reads `@8`, `@8.5` — never a bare number.** Found on the emulator, not by
 * inspection: a bare "8" chip is the exact same text as a reps count of 8, and with the reps
 * field prefilled to 8 (a very common rep count) the two are indistinguishable on screen — which
 * is exactly what broke `TwoTapSetLoggingTest`'s own prefill assertion, proof the ambiguity is
 * real for a member reading the sheet, not only for a test. `@` is the same lifting notation
 * this class's own read-back already uses everywhere a set is shown ([RpeFormatter.at]).
 */
@Composable
private fun RpeChips(
    selected: String,
    onSelected: (String) -> Unit,
) {
    val selectedValue = selected.trim().toDoubleOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(GymDimens.HairGap)) {
        Text(
            text = "RPE (optional)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
            verticalArrangement = Arrangement.spacedBy(GymDimens.HairGap),
        ) {
            RpeFormatter.scale.forEach { value ->
                val isSelected = selectedValue == value
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelected(if (isSelected) "" else RpeFormatter.number(value)) },
                    label = { Text(RpeFormatter.at(value)) },
                    // ADR-0019: FilterChip reads CornerFull unless told otherwise (Shape.kt's trap).
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
                )
            }
        }
    }
}

/**
 * What set entry can do, gathered up so the sheet takes one parameter instead of eight.
 *
 * Steps are separate from edits because they mean different things: a step is domain
 * arithmetic in the member's unit, an edit is whatever was typed.
 */
internal data class SetEntryCallbacks(
    val onWeightChanged: (String) -> Unit,
    val onWeightStepped: (Int) -> Unit,
    val onRepsChanged: (String) -> Unit,
    val onRepsStepped: (Int) -> Unit,
    val onSetsChanged: (String) -> Unit,
    val onSetsStepped: (Int) -> Unit,
    val onRpeChanged: (String) -> Unit,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
) {
    companion object {
        /** For previews and for callers that only render the session behind the sheet. */
        val Inert =
            SetEntryCallbacks({}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

/** The same weight in the unit the member does not read, or nothing if this is not a number. */
private fun String.otherUnit(unit: WeightUnit): String? =
    trim()
        .toDoubleOrNull()
        ?.let { typed -> WeightFormatter.format(UnitConverter.toKilograms(typed, unit), unit).secondary }

/** "100 lb × 8", or "8 reps" for a bodyweight prefill — the sheet's own provenance line (US-37). */
private fun SetEntry.prefillSummary(unit: WeightUnit): String =
    weight
        .takeIf { it.isNotBlank() }
        ?.let { "$it ${unit.name.lowercase()} × $reps" }
        ?: "$reps reps"

/** The same "EEE d MMM" convention the rest panel's comparison line already uses. */
private fun Instant.asDay(): String =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).withZone(ZoneId.systemDefault()).format(this)
