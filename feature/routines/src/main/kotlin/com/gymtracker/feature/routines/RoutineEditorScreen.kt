package com.gymtracker.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.core.designsystem.component.DrillDownTopBar
import com.gymtracker.core.designsystem.component.GymDivider
import com.gymtracker.core.designsystem.component.PrimaryActionButton
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.set.LastPerformance
import com.gymtracker.core.domain.units.MinutesSeconds
import com.gymtracker.core.domain.units.WeightFormatter
import com.gymtracker.core.domain.units.WeightUnit
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The routine editor (US-29, and US-30 for the target half): a name, an order, add and remove
 * — and, for each movement, a plan for next time.
 *
 * ADR-0020 originally chose a routine that stores no target at all — the mock's tappable
 * "3×8 · 100 lb" was deliberately not built. ADR-0027 is the later, narrower reversal of
 * exactly that point: a target can be entered, edited and cleared here, and it renders beside
 * what was actually lifted last time, never merged into it — see [MovementListItem].
 */
@Composable
fun RoutineEditorRoute(
    routineId: RoutineId,
    onBack: () -> Unit,
    onAddExercise: () -> Unit,
    pickedExerciseIds: List<String> = emptyList(),
    onPicksHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: RoutineEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(routineId) { viewModel.open(routineId) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isDeleted by viewModel.isDeleted.collectAsStateWithLifecycle()
    val targetEditor by viewModel.target.editor.collectAsStateWithLifecycle()

    // The picker hands ids back the same way it does for a session (US-02a): appended in pick
    // order, so one visit can add several movements.
    LaunchedEffect(pickedExerciseIds) {
        if (pickedExerciseIds.isEmpty()) return@LaunchedEffect

        viewModel.onExercisesChosen(pickedExerciseIds.map(::ExerciseId))
        onPicksHandled()
    }

    // Deleting and leaving are the same navigation action: there is nothing left here to edit.
    LaunchedEffect(isDeleted) {
        if (isDeleted) onBack()
    }

    RoutineEditorScreen(
        state = state,
        onBack = onBack,
        onNameChanged = viewModel::onNameChanged,
        onAddExercise = onAddExercise,
        onRemoveMovement = viewModel::onRemoveMovement,
        onMoveUp = viewModel::onMoveUp,
        onMoveDown = viewModel::onMoveDown,
        onDeleteRoutine = viewModel::onDeleteRoutine,
        onEditTarget = viewModel.target::onEdit,
        modifier = modifier,
    )

    targetEditor?.let { editor ->
        TargetEditorDialog(
            editor = editor,
            onFieldChanged = viewModel.target::onFieldChanged,
            onSave = viewModel.target::onSave,
            onClear = viewModel.target::onClear,
            onDismiss = viewModel.target::onDismiss,
        )
    }
}

@Composable
internal fun RoutineEditorScreen(
    state: RoutineEditorUiState,
    onBack: () -> Unit = {},
    onNameChanged: (String) -> Unit = {},
    onAddExercise: () -> Unit = {},
    onRemoveMovement: (RoutineItemId) -> Unit = {},
    onMoveUp: (Int) -> Unit = {},
    onMoveDown: (Int) -> Unit = {},
    onDeleteRoutine: () -> Unit = {},
    onEditTarget: (RoutineItemId) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { DrillDownTopBar(onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(GymDimens.ScreenPadding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(GymDimens.Gap),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChanged,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.movements.isEmpty()) {
                Text(
                    text = "No movements yet. Add the exercises you do, in the order you do them.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().weight(1f).wrapContentHeight(Alignment.CenterVertically),
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(state.movements, key = { _, row -> row.itemId.value }) { index, row ->
                        MovementListItem(
                            row = row,
                            unit = state.unit,
                            isFirst = index == 0,
                            isLast = index == state.movements.lastIndex,
                            onMoveUp = { onMoveUp(index) },
                            onMoveDown = { onMoveDown(index) },
                            onRemove = { onRemoveMovement(row.itemId) },
                            onEditTarget = { onEditTarget(row.itemId) },
                        )
                        GymDivider()
                    }
                }
            }

            PrimaryActionButton(text = "Add exercise", onClick = onAddExercise)

            DeleteRoutineAction(routineName = state.name, onDeleteRoutine = onDeleteRoutine)
        }
    }
}

/**
 * "Delete routine" and its confirmation, split out of [RoutineEditorScreen] to keep that
 * function under detekt's length ceiling — the same idiom `SessionScaffold.kt`'s
 * `FinishConfirmation` uses.
 *
 * Whether the confirmation is up is local, transient UI state, the same shape
 * `ActiveSession`'s own `confirmingFinish` is: nothing outside this screen needs to know, so it
 * does not belong in the ViewModel's `uiState`.
 *
 * US-29 (amended 2026-09-03): a routine has no undo the way a deleted workout or set does
 * (ADR-0012), so this confirmation is the one safety net it gets before the button below
 * becomes permanent.
 */
@Composable
private fun DeleteRoutineAction(
    routineName: String,
    onDeleteRoutine: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }

    // Destructive, so it is outlined and never shares a surface with a save (ADR-0019) —
    // "Add exercise" above is this screen's one constructive action. Living here rather than on
    // the Routines list row is also what fixes that row wrapping onto a second line to fit it
    // (redesign audit finding 04).
    OutlinedButton(
        onClick = { confirming = true },
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = GymDimens.MinTouchTarget),
    ) {
        Text("Delete routine")
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Delete $routineName?") },
            text = { Text("It and its movements are gone for good. No session, past or present, is touched.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onDeleteRoutine()
                    },
                    modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirming = false },
                    modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

/**
 * One movement: what it was last time, what it is planned to be next time, and reordering.
 *
 * [row.lastTime] and [row.target] are both shown when both exist, on their own lines, never
 * reconciled into one (US-30, ADR-0027) — the labelling rule is what makes a target safe to
 * show at all, so "Target" is part of the string, not a color or a position doing the work.
 *
 * Reordering is up/down rather than a drag — see [RoutineEditorViewModel.onMoveUp] for why.
 * The buttons are text for the same reason the bottom bar's labels are: there is no icon
 * dependency in this app and adding one needs an ADR (constitution §7).
 */
@Composable
private fun MovementListItem(
    row: MovementRow,
    unit: WeightUnit,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onEditTarget: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = GymDimens.TightGap),
        verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
    ) {
        Text(row.exerciseName, style = MaterialTheme.typography.titleMedium)

        // The honest half of ADR-0020. Absent entirely when the movement has never been done,
        // rather than a zero or a dash pretending to be a plan (US-13, constitution §2.4).
        row.lastTime?.let { last ->
            Text(
                text = last.asHistory(unit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The other half, added by ADR-0027: absent the same way when nobody has set one.
        row.target?.let { target ->
            Text(
                text = target.asTargetLine(unit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        MovementActions(
            row = row,
            isFirst = isFirst,
            isLast = isLast,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onRemove = onRemove,
            onEditTarget = onEditTarget,
        )
    }
}

/** Up, down, target, remove — split out of [MovementListItem] to keep that function short. */
@Composable
private fun MovementActions(
    row: MovementRow,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onEditTarget: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(GymDimens.TightGap)) {
        TextButton(
            onClick = onMoveUp,
            enabled = !isFirst,
            modifier =
                Modifier
                    .sizeIn(minHeight = GymDimens.MinTouchTarget)
                    .semantics { contentDescription = "Move ${row.exerciseName} up" },
        ) {
            Text("Up")
        }
        TextButton(
            onClick = onMoveDown,
            enabled = !isLast,
            modifier =
                Modifier
                    .sizeIn(minHeight = GymDimens.MinTouchTarget)
                    .semantics { contentDescription = "Move ${row.exerciseName} down" },
        ) {
            Text("Down")
        }
        TextButton(
            onClick = onEditTarget,
            modifier =
                Modifier
                    .sizeIn(minHeight = GymDimens.MinTouchTarget)
                    .semantics { contentDescription = "Set a target for ${row.exerciseName}" },
        ) {
            Text(if (row.target == null) "Set target" else "Edit target")
        }
        TextButton(
            onClick = onRemove,
            modifier =
                Modifier
                    .sizeIn(minHeight = GymDimens.MinTouchTarget)
                    .semantics { contentDescription = "Remove ${row.exerciseName}" },
        ) {
            Text("Remove")
        }
    }
}

/**
 * "Last Tue 5 Aug · 135 lb × 8".
 *
 * The date is not decoration. Without it the line reads as a prescription, which is the one
 * thing ADR-0020 spent the whole decision avoiding.
 */
private fun LastPerformance.asHistory(unit: WeightUnit): String {
    val weight = WeightFormatter.format(weightKg, unit)
    return buildString {
        append("Last ${performedAt.asDay()}")
        append("  ·  ")
        append(weight.primary)
        append(" × $reps")
        weight.secondary?.let { append("  ·  $it") }
    }
}

private fun java.time.Instant.asDay(): String = DAY_FORMAT.format(atZone(ZoneId.systemDefault()))

private val DAY_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).withZone(ZoneId.systemDefault())

/**
 * "Target 3 × 8 · 105 lb · 1:30 rest" — every present field joined, any absent field simply not
 * mentioned (US-30: "each is optional on its own", so a load-only, a sets-only or — since
 * ADR-0050 — a rest-only target is still a target, not nothing). The rest reads the same way the
 * session screen reads it, through the one `m:ss` formatter both features share.
 */
private fun MovementTarget.asTargetLine(unit: WeightUnit): String {
    val setsReps =
        when {
            sets != null && reps != null -> "$sets × $reps"
            sets != null -> "$sets sets"
            reps != null -> "$reps reps"
            else -> null
        }
    val weight = weightKg?.let { WeightFormatter.format(it, unit).primary }
    val restLine = rest?.let { "${MinutesSeconds.format(it)} rest" }
    val parts = listOfNotNull(setsReps, weight, restLine)
    return "Target " + parts.joinToString("  ·  ")
}

/**
 * Why Save did nothing, one line per field it could not read (US-30). Absent until a save is
 * refused, and gone again on the next keystroke — the same place-and-lifetime a field's own
 * supporting text would have, without the three fields each needing to know the others' state.
 */
@Composable
private fun RefusalReasons(errors: List<String>) {
    errors.forEach { problem ->
        Text(
            text = problem,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
        )
    }
}

/** The four fields, in the order the target line reads them back: sets, reps, load, rest (ADR-0050). */
@Composable
private fun TargetFields(
    editor: TargetEditorState,
    onFieldChanged: (sets: String?, reps: String?, weight: String?, rest: String?) -> Unit,
) {
    OutlinedTextField(
        value = editor.sets,
        onValueChange = { onFieldChanged(it, null, null, null) },
        label = { Text("Sets") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = editor.reps,
        onValueChange = { onFieldChanged(null, it, null, null) },
        label = { Text("Reps") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = editor.weight,
        onValueChange = { onFieldChanged(null, null, it, null) },
        label = { Text("Load (${editor.unit.name.lowercase()})") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    // Whole seconds, typed: "90" is 1:30. A stepper in fifteen-second steps is Tier 3's inline
    // target editor; this dialog stays plain text fields for the reason its own doc gives.
    OutlinedTextField(
        value = editor.rest,
        onValueChange = { onFieldChanged(null, null, null, it) },
        label = { Text("Rest (seconds)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Sets, edits or clears one movement's target (US-30; the rest since ADR-0050).
 *
 * Plain text fields rather than the stepper the session screen uses for logging a set: this
 * dialog is reached from the sofa, not mid-set with chalk on your fingers, so ADR-0016's
 * one-handed constraint does not apply here the way it does there.
 */
@Composable
private fun TargetEditorDialog(
    editor: TargetEditorState,
    onFieldChanged: (sets: String?, reps: String?, weight: String?, rest: String?) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Target for ${editor.exerciseName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(GymDimens.Gap)) {
                Text(
                    text =
                        "Each number is optional. A blank field says nothing about that number; " +
                            "a blank rest means your default from Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TargetFields(editor, onFieldChanged)
                RefusalReasons(editor.errors)
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget)) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                // Destructive, so text rather than filled (ADR-0019) — but it shares this
                // dialog with Save rather than living behind a second tap: unlike a logged set,
                // a target is not something the app is recording on someone's behalf, and
                // clearing one is exactly as reversible as never having set it.
                TextButton(
                    onClick = onClear,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
                ) {
                    Text("Clear")
                }
                TextButton(onClick = onDismiss, modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget)) {
                    Text("Cancel")
                }
            }
        },
    )
}
