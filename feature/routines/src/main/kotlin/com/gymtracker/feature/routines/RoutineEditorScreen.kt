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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.core.designsystem.component.DrillDownTopBar
import com.gymtracker.core.designsystem.component.GymDivider
import com.gymtracker.core.designsystem.component.PrimaryActionButton
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.set.LastPerformance
import com.gymtracker.core.domain.units.WeightFormatter
import com.gymtracker.core.domain.units.WeightUnit
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The routine editor (US-29): a name, an order, add and remove.
 *
 * **What is not here is the design.** The mock let you tap a target — "3×8 · 100 lb" — and
 * change it. ADR-0020 chose a routine that stores no target, so there is no such field and no
 * control that could create one. What sits beside each movement instead is what you actually
 * lifted last time, dated and labelled as history.
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
        modifier = modifier,
    )
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
                        )
                        GymDivider()
                    }
                }
            }

            PrimaryActionButton(text = "Add exercise", onClick = onAddExercise)

            // Destructive, so it is outlined and never shares a surface with a save (ADR-0019)
            // — "Add exercise" above is this screen's one constructive action. Living here
            // rather than on the Routines list row is also what fixes that row wrapping onto a
            // second line to fit it (redesign audit finding 04).
            OutlinedButton(
                onClick = onDeleteRoutine,
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = GymDimens.MinTouchTarget),
            ) {
                Text("Delete routine")
            }
        }
    }
}

/**
 * One movement, and what it was last time.
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
