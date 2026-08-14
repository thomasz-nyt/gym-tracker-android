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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.core.designsystem.component.DrillDownTopBar
import com.gymtracker.core.designsystem.component.GymDivider
import com.gymtracker.core.designsystem.component.PrimaryActionButton
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.domain.model.RoutineId

/**
 * The member's routines (US-29).
 *
 * A drill-down reached from Train's one outlined `Routines` entry point (US-36, ADR-0030) —
 * not a tab. It carries a [DrillDownTopBar] now for the same reason `RoutineEditor` already
 * has one: a drill-down has no bottom bar to exit through, and this screen stopped being a
 * tab-bar destination the bar could no longer hide behind.
 */
@Composable
fun RoutinesRoute(
    onEditRoutine: (RoutineId) -> Unit,
    onWorkoutStarted: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoutinesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val outcome by viewModel.startOutcome.collectAsStateWithLifecycle()

    // Started navigates; AlreadyRunning stays put and explains, so it is not consumed here.
    LaunchedEffect(outcome) {
        if (outcome == RoutineStart.Started) {
            viewModel.onStartHandled()
            onWorkoutStarted()
        }
    }

    RoutinesScreen(
        state = state,
        alreadyRunning = outcome == RoutineStart.AlreadyRunning,
        onDismissAlreadyRunning = viewModel::onStartHandled,
        onGoToWorkout = {
            viewModel.onStartHandled()
            onWorkoutStarted()
        },
        onCreateRoutine = viewModel::onCreateRoutine,
        onEditRoutine = onEditRoutine,
        onStartRoutine = viewModel::onStartRoutine,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun RoutinesScreen(
    state: RoutinesUiState,
    alreadyRunning: Boolean = false,
    onDismissAlreadyRunning: () -> Unit = {},
    onGoToWorkout: () -> Unit = {},
    onCreateRoutine: (String) -> Unit = {},
    onEditRoutine: (RoutineId) -> Unit = {},
    onStartRoutine: (RoutineId) -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var naming by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { DrillDownTopBar(onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(GymDimens.ScreenPadding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(GymDimens.Gap),
        ) {
            Text("Routines", style = MaterialTheme.typography.titleLarge)

            if (state.routines.isEmpty()) {
                EmptyRoutines(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.routines, key = { it.routine.id.value }) { row ->
                        RoutineListItem(
                            row = row,
                            onEdit = { onEditRoutine(row.routine.id) },
                            onStart = { onStartRoutine(row.routine.id) },
                        )
                        GymDivider()
                    }
                }
            }

            if (alreadyRunning) {
                AlreadyRunningBanner(onGoToWorkout = onGoToWorkout, onDismiss = onDismissAlreadyRunning)
            }

            PrimaryActionButton(text = "New routine", onClick = { naming = true })
        }
    }

    if (naming) {
        NameRoutineDialog(
            onConfirm = { name ->
                naming = false
                onCreateRoutine(name)
            },
            onDismiss = { naming = false },
        )
    }
}

/**
 * What the app says before you have made a routine.
 *
 * It says what a routine *is*, because the word alone does not explain why you would want one
 * — and because starting a workout without one still works, this screen has to earn its tap.
 */
@Composable
private fun EmptyRoutines(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().wrapContentHeight(Alignment.CenterVertically),
        verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
    ) {
        Text(
            text = "No routines yet.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text =
                "A routine is a name and the movements you do, in order — Upper A, Leg day. " +
                    "Starting one sets up the workout so you are not rebuilding it every week.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * One routine: what it is called, how much is in it, and the two things you can do with it.
 *
 * **Deleting is not one of them.** ADR-0019's rule is that a destructive control never shares
 * a surface with a constructive one, and `Start` is the row's constructive action — so
 * deleting a routine lives in the editor instead (`RoutineEditorScreen`'s own outlined button),
 * which also fixes the row that used to wrap onto a second line to fit it (redesign audit
 * finding 04). `Start` is filled, the row's one prominent action; `Edit` stays quiet.
 */
@Composable
private fun RoutineListItem(
    row: RoutineRow,
    onEdit: () -> Unit,
    onStart: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(row.routine.name, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(row.movementSummary(), style = MaterialTheme.typography.bodyMedium) },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onEdit,
                    modifier =
                        Modifier
                            .sizeIn(minHeight = GymDimens.MinTouchTarget)
                            .semantics { contentDescription = "Edit ${row.routine.name}" },
                ) {
                    Text("Edit")
                }
                Button(
                    onClick = onStart,
                    // Button reads CornerFull rather than the shape scale (Shape.kt's
                    // documented trap), so without this it stays a stadium regardless of
                    // GymShapes.
                    shape = MaterialTheme.shapes.large,
                    modifier =
                        Modifier
                            .sizeIn(minHeight = GymDimens.MinTouchTarget)
                            .semantics { contentDescription = "Start ${row.routine.name}" },
                ) {
                    Text("Start")
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "3 movements" — a count, never a set-and-rep target (ADR-0020). */
private fun RoutineRow.movementSummary(): String =
    when (movements) {
        0 -> "No movements yet"
        1 -> "1 movement"
        else -> "$movements movements"
    }

/**
 * US-01 allows one workout at a time, so this explains rather than silently doing nothing.
 *
 * It offers the running workout instead, which is what the member almost certainly wants —
 * and it does **not** offer to add the routine's movements to it, because ADR-0020's copy
 * only happens on a fresh session.
 */
@Composable
private fun AlreadyRunningBanner(
    onGoToWorkout: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(GymDimens.Gap),
            verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
        ) {
            Text(
                text = "A workout is already running, so nothing was added to it. Finish it first.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(GymDimens.TightGap)) {
                TextButton(onClick = onGoToWorkout, modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget)) {
                    Text("Go to workout")
                }
                TextButton(onClick = onDismiss, modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget)) {
                    Text("Dismiss")
                }
            }
        }
    }
}

/** A routine needs a name before it can be a routine; everything else is added in the editor. */
@Composable
private fun NameRoutineDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New routine") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
                modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget)) {
                Text("Cancel")
            }
        },
    )
}
