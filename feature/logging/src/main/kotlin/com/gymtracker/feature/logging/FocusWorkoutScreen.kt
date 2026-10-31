package com.gymtracker.feature.logging

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.gymtracker.core.designsystem.component.GymText
import com.gymtracker.core.designsystem.component.PrimaryActionButton
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymTextRoles
import com.gymtracker.core.domain.model.MovementOutcome
import com.gymtracker.core.domain.model.MovementStatus
import com.gymtracker.core.domain.model.status
import com.gymtracker.core.domain.rest.UpNextSet
import com.gymtracker.core.domain.units.WeightFormatter
import kotlinx.coroutines.delay

private enum class FocusPage { CURRENT, PLAN, MORE, RECORDS }

/** US-63: a sparse current-set lens over the same persisted workout as the plan. */
@Composable
fun FocusWorkoutScreen(
    state: SessionUiState,
    actions: FocusActions = FocusActions(),
    modifier: Modifier = Modifier,
) {
    var page by rememberSaveable(state.activeSession?.id?.value) { mutableStateOf(FocusPage.CURRENT) }
    var finish by rememberSaveable { mutableStateOf(false) }
    var showUndo by rememberSaveable { mutableStateOf(false) }
    val rows = state.exercises.sortedBy { it.sessionExercise.position }
    val row = rows.firstOrNull { it.sessionExercise.id == state.openSessionExerciseId } ?: rows.firstOrNull()
    val contentScroll = remember(page, row?.sessionExercise?.id?.value) { ScrollState(0) }
    val latest = rows.flatMap { it.sets }.maxByOrNull { it.performedAt }?.id
    LaunchedEffect(state.canUndoOneTapLog, latest) {
        showUndo = state.canUndoOneTapLog
        if (showUndo) {
            delay(5_000)
            showUndo = false
        }
    }
    BackHandler(page != FocusPage.CURRENT) { page = FocusPage.CURRENT }
    Scaffold(modifier = modifier.fillMaxSize()) { insets ->
        Column(Modifier.padding(insets).padding(GymDimens.CompactScreenPadding)) {
            FocusHeader(page) { page = it }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(contentScroll),
                verticalArrangement = Arrangement.spacedBy(GymDimens.SectionSpace),
            ) {
                state.actionError?.let { message ->
                    FocusText(message, color = MaterialTheme.colorScheme.error)
                    FocusButton("Dismiss", onClick = actions.onDismissError)
                }
                when (page) {
                    FocusPage.CURRENT -> FocusCurrent(row, state, actions)
                    FocusPage.PLAN -> FocusPlan(rows, state, actions, { page = FocusPage.CURRENT }, { finish = true })
                    FocusPage.MORE ->
                        FocusMore(row, state, actions, { page = FocusPage.RECORDS }, {
                            page =
                                FocusPage.CURRENT
                        }, { finish = true })
                    FocusPage.RECORDS -> row?.let { FocusRecords(it, state, actions) }
                }
            }
            if (page == FocusPage.CURRENT) {
                if (showUndo) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        FocusText("Set saved")
                        FocusButton("Undo", onClick = actions.onUndoLog)
                    }
                }
                FocusFooter(row, rows, state, actions) { page = FocusPage.PLAN }
            }
        }
    }
    if (finish) {
        FocusFinishConfirmation(rows.sumOf { it.sets.size }, { finish = false }) {
            finish = false
            actions.onFinish()
        }
    }
}

@Composable
private fun FocusHeader(
    page: FocusPage,
    onPage: (FocusPage) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        FocusButton(if (page == FocusPage.CURRENT) "Workout" else "Back to set") {
            onPage(if (page == FocusPage.CURRENT) FocusPage.PLAN else FocusPage.CURRENT)
        }
        FocusButton("⋯", "More exercise actions") { onPage(FocusPage.MORE) }
    }
}

/** Secondary details remain readable at large text sizes within their scrolling container. */
@Composable
private fun FocusText(
    text: String,
    color: Color = Color.Unspecified,
) {
    Text(text, color = color, maxLines = 20, style = GymTextRoles.Body.style)
}

@Composable
private fun FocusCurrent(
    row: SessionExerciseRow?,
    state: SessionUiState,
    actions: FocusActions,
) {
    if (row == null) {
        GymText("Choose your first exercise", role = GymTextRoles.TitleLg)
        FocusText("Your workout is running. Add a movement to begin.")
    } else {
        GymText(row.name(), role = GymTextRoles.TitleLg)
        val status = row.sessionExercise.status(row.sets)
        if (status.isFinished) {
            GymText(status.label(), role = GymTextRoles.TitleMd)
            FocusText("${row.sets.size} sets recorded")
            FocusButton("Add an extra set") { actions.onAdjust(row) }
        } else {
            FocusSet(row, state, actions)
        }
        FocusRest(state, actions)
    }
}

@Composable
private fun FocusSet(
    row: SessionExerciseRow,
    state: SessionUiState,
    actions: FocusActions,
) {
    val next = state.nextLoggableSet?.takeIf { it.sessionExerciseId == row.sessionExercise.id }
    val number = next?.setNumber ?: ((row.sets.maxOfOrNull { it.setIndex } ?: 0) + 1)
    val planned = row.sessionExercise.target?.sets
    FocusText(if (planned != null && number <= planned) "Set $number of $planned" else "Set $number")
    if (next == null) {
        FocusText("Choose the load and reps you actually perform.")
    } else {
        FocusNumbers(next, state)
        FocusButton("Adjust this set") { actions.onAdjust(row) }
    }
}

@Composable
private fun FocusNumbers(
    next: UpNextSet,
    state: SessionUiState,
) {
    val load = next.prefill.weight?.let { numberText(it) } ?: "Bodyweight"
    val unit = if (next.prefill.weight == null) "Load" else state.unit.name.lowercase()
    if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) {
        Column(verticalArrangement = Arrangement.spacedBy(GymDimens.Gap)) {
            FocusMetric(load, unit, next.prefill.weight != null)
            FocusMetric(next.prefill.reps.toString(), "Reps", true)
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GymDimens.SectionSpace)) {
            Column(Modifier.weight(1f)) { FocusMetric(load, unit, next.prefill.weight != null) }
            Column(Modifier.weight(1f)) { FocusMetric(next.prefill.reps.toString(), "Reps", true) }
        }
    }
}

@Composable
private fun FocusMetric(
    value: String,
    label: String,
    numeric: Boolean,
) {
    GymText(value, role = if (numeric) GymTextRoles.NumeralLg else GymTextRoles.TitleLg)
    FocusText(label)
}

@Composable
private fun FocusRest(
    state: SessionUiState,
    actions: FocusActions,
) {
    state.restRemaining?.let { remaining ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FocusText("Rest")
            val seconds = remaining.seconds.coerceAtLeast(0)
            GymText("${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", role = GymTextRoles.TitleMd)
            FocusButton("Skip", onClick = actions.onSkipRest)
        }
    }
}

@Composable
private fun FocusPlan(
    rows: List<SessionExerciseRow>,
    state: SessionUiState,
    actions: FocusActions,
    onSelected: () -> Unit,
    onFinish: () -> Unit,
) {
    GymText("Your workout", role = GymTextRoles.TitleLg)
    rows.forEachIndexed { index, movement ->
        Column {
            FocusButton(movement.name()) {
                actions.onSelect(movement.sessionExercise.id)
                onSelected()
            }
            FocusText("${movement.sessionExercise.status(movement.sets).label()} · ${movement.sets.size} sets recorded")
            Row {
                if (index >
                    0
                ) {
                    FocusButton(
                        "Move up",
                        "Move ${movement.name()} up",
                    ) { actions.onMove(movement.sessionExercise.id, -1) }
                }
                if (index <
                    rows.lastIndex
                ) {
                    FocusButton(
                        "Move down",
                        "Move ${movement.name()} down",
                    ) { actions.onMove(movement.sessionExercise.id, 1) }
                }
                FocusButton("Remove", "Remove ${movement.name()}") { actions.onRemove(movement.sessionExercise.id) }
            }
        }
    }
    FocusButton("Add exercise", onClick = actions.onAddExercise)
    if (state.canUndoRemoval) FocusButton("Undo remove exercise", onClick = actions.onUndoRemoval)
    FocusButton("Finish workout", onClick = onFinish)
}

@Composable
private fun FocusMore(
    row: SessionExerciseRow?,
    state: SessionUiState,
    actions: FocusActions,
    onRecords: () -> Unit,
    onCurrent: () -> Unit,
    onFinish: () -> Unit,
) {
    GymText(row?.name() ?: "Workout actions", role = GymTextRoles.TitleLg)
    if (row != null) {
        FocusButton("Sets, history & target", onClick = onRecords)
        FocusOutcomes(row, actions, onCurrent)
    }
    if (state.canUndoOneTapLog) FocusButton("Undo last logged set", onClick = actions.onUndoLog)
    if (state.canUndoSetDelete) FocusButton("Undo delete set", onClick = actions.onUndoDelete)
    if (state.canUndoRemoval) FocusButton("Undo remove exercise", onClick = actions.onUndoRemoval)
    if (state.restRemaining != null) FocusButton("Add 30 seconds rest", onClick = actions.onExtendRest)
    FocusButton("Settings", onClick = actions.onSettings)
    FocusButton("Finish workout", onClick = onFinish)
}

@Composable
private fun FocusOutcomes(
    row: SessionExerciseRow,
    actions: FocusActions,
    onCurrent: () -> Unit,
) {
    val status = row.sessionExercise.status(row.sets)
    if (row.sets.isEmpty()) {
        FocusButton("Skip exercise") {
            actions.onOutcome(row, MovementOutcome.SKIPPED)
            onCurrent()
        }
    } else if (!status.isFinished) {
        val planned = row.sessionExercise.target?.sets
        FocusButton(if (planned == null) "Mark exercise done" else "End exercise early") {
            actions.onOutcome(
                row,
                if (planned ==
                    null
                ) {
                    MovementOutcome.DONE
                } else {
                    MovementOutcome.ENDED_EARLY
                },
            )
            onCurrent()
        }
    }
    if (row.sessionExercise.outcome != null) {
        FocusButton("Continue exercise") {
            actions.onOutcome(row, null)
            onCurrent()
        }
    }
}

@Composable
private fun FocusFooter(
    row: SessionExerciseRow?,
    rows: List<SessionExerciseRow>,
    state: SessionUiState,
    actions: FocusActions,
    onReview: () -> Unit,
) {
    val status = row?.sessionExercise?.status(row.sets)
    val next = state.nextLoggableSet?.takeIf { it.sessionExerciseId == row?.sessionExercise?.id }
    val nextMovement =
        row?.let { current ->
            val index = rows.indexOf(current)
            (rows.drop(index + 1) + rows.take(index)).firstOrNull { !it.sessionExercise.status(it.sets).isFinished }
        }
    PrimaryActionButton(
        text =
            when {
                row == null -> "Add exercise"
                status?.isFinished == true -> if (nextMovement == null) "Review workout" else "Next exercise"
                next != null -> "LOG SET ${next.setNumber}"
                else -> "Add set"
            },
        onClick = {
            when {
                row == null -> actions.onAddExercise()
                status?.isFinished == true && nextMovement != null -> actions.onSelect(nextMovement.sessionExercise.id)
                status?.isFinished == true -> onReview()
                next != null -> actions.onLogSet(next)
                else -> actions.onAdjust(row)
            }
        },
    )
}

@Composable
private fun FocusFinishConfirmation(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { FocusText("Finish this workout?") },
        text = { FocusText("$count actual sets will be kept. Unfinished exercises are not marked complete.") },
        confirmButton = { FocusButton("Finish workout", onClick = onConfirm) },
        dismissButton = { FocusButton("Keep going", onClick = onDismiss) },
    )
}

@Composable
private fun FocusRecords(
    row: SessionExerciseRow,
    state: SessionUiState,
    actions: FocusActions,
) {
    GymText("This exercise’s sets", role = GymTextRoles.TitleLg)
    if (row.sets.isEmpty()) FocusText("No sets recorded yet.")
    row.sets.sortedBy { it.setIndex }.forEach { set ->
        FocusButton(
            "Set ${set.setIndex} · ${WeightFormatter.format(set.weightKg, state.unit).primary} × ${set.reps}",
            "Edit set ${set.setIndex}",
        ) {
            actions.onEdit(row, set)
        }
    }
    if (state.canUndoSetDelete) FocusButton("Undo delete set", onClick = actions.onUndoDelete)
    FocusButton("Add set") { actions.onAdjust(row) }
    val target = row.sessionExercise.target
    FocusText(
        if (target ==
            null
        ) {
            "Target: none"
        } else {
            "Target: ${target.sets ?: "—"} sets × ${target.reps ?: "—"} reps · ${WeightFormatter.format(
                target.weightKg,
                state.unit,
            ).primary}"
        },
    )
    GymText("Last time", role = GymTextRoles.TitleMd)
    val last = state.lastTime
    if (last == null) {
        FocusText("No previous performance.")
    } else {
        FocusText(last.performedAt.toString().substringBefore('T'))
        last.sets.forEach {
            FocusText("Set ${it.setIndex} · ${WeightFormatter.format(it.weightKg, state.unit).primary} × ${it.reps}")
        }
    }
}

@Composable
private fun FocusButton(
    text: String,
    description: String? = null,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier =
            Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget).then(
                if (description == null) Modifier else Modifier.semantics { contentDescription = description },
            ),
    ) { FocusText(text) }
}

private fun SessionExerciseRow.name() = exercise?.name ?: sessionExercise.exerciseId.value

private fun numberText(value: Double) = if (value % 1 == 0.0) value.toLong().toString() else value.toString()

private fun MovementStatus.label() =
    when (this) {
        MovementStatus.NOT_STARTED -> "Not started"
        MovementStatus.IN_PROGRESS -> "In progress"
        MovementStatus.PLAN_MET -> "Plan met"
        MovementStatus.DONE -> "Done"
        MovementStatus.ENDED_EARLY -> "Ended early"
        MovementStatus.SKIPPED -> "Skipped"
    }
