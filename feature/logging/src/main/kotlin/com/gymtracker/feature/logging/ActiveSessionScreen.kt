package com.gymtracker.feature.logging

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.core.designsystem.component.PrimaryActionButton
import com.gymtracker.core.designsystem.component.SecondaryActionButton
import com.gymtracker.core.designsystem.component.StepperField
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymPreviews
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.rest.UpNextSet
import com.gymtracker.core.domain.session.StaleSessionPolicy
import com.gymtracker.core.domain.session.StaleSessionPrompt
import com.gymtracker.core.domain.units.UnitConverter
import com.gymtracker.core.domain.units.WeightFormatter
import com.gymtracker.core.domain.units.WeightUnit
import com.gymtracker.feature.logging.rest.RestAlarm
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The core-loop screen: start a session, come back to it, resolve one that was left running
 * (US-01), add exercises (US-02), log sets (US-03).
 *
 * **Which of home and session you see is still derived from the database, not from a back
 * stack** — that is what makes "reopen and you are back in your session" true even after the
 * process is killed. M3 put a navigation graph above this route (ADR-0013), and the graph's
 * start destination is this screen precisely so that property survives; navigation decides
 * where *back* goes, not what you resume into.
 *
 * Picking an exercise is a destination (US-12): "Add exercise" navigates to the shared browse
 * screen, which hands its picks back through [pickedExerciseIds]. History and the workout
 * detail reached from it are destinations of their own now too (ADR-0024, US-06b), so
 * [onOpenHistory] is a plain navigation callback here rather than a call into this screen's
 * ViewModel. Only the guided flow (US-05a) is still selected by state inside this route, on
 * purpose, for the reason ADR-0017 gives.
 *
 * @param pickedExerciseIds the exercises chosen on the browse screen, in the order they were
 *   picked, appended to the session once and then cleared through [onPicksHandled]. A list
 *   because one visit may add several (US-02a), including the same exercise twice (US-02).
 */
@Composable
fun LoggingRoute(
    onBrowseCatalog: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onAddExercise: () -> Unit = {},
    pickedExerciseIds: List<String> = emptyList(),
    onPicksHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
    warmUpViewModel: WarmUpViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val warmUpElapsed by warmUpViewModel.elapsed.collectAsStateWithLifecycle()
    RestNotifications(viewModel)

    // Browse is a destination of its own, so it hands exercises back through the nav result
    // rather than this screen owning a search overlay (US-12, ADR-0013).
    //
    // Appended in pick order, so a workout set up in one visit reads in the order it was
    // chosen — `position` is what US-02b then displays newest-first.
    LaunchedEffect(pickedExerciseIds) {
        if (pickedExerciseIds.isEmpty()) return@LaunchedEffect

        viewModel.onExercisesChosen(pickedExerciseIds.map(::ExerciseId))
        onPicksHandled()
    }

    LoggingScreen(
        state = state,
        onStartWorkout = viewModel::onStartWorkout,
        onFinishWorkout = viewModel::onFinishWorkout,
        onResolveStale = viewModel::onResolveStale,
        onAddSet = viewModel.setEntry::open,
        onRemoveExercise = viewModel.removal::remove,
        onUndoRemoval = viewModel.removal::undo,
        guided = viewModel.guidedActions(),
        onSkipRest = viewModel.rest::skip,
        onOpenHistory = onOpenHistory,
        onBrowseCatalog = onBrowseCatalog,
        setEntry = viewModel.setEntryCallbacks(),
        onEditSet = { row, set ->
            viewModel.setEdit.open(set, row.exercise?.name ?: row.sessionExercise.exerciseId.value)
        },
        setEdit = viewModel.setEditCallbacks(),
        onUndoSetDelete = viewModel.setEdit::undo,
        onLogNextSet = viewModel::onLogNextSet,
        onAddExercise = onAddExercise,
        warmUp =
            WarmUp(
                elapsed = warmUpElapsed,
                onStart = warmUpViewModel::onStartWarmUp,
                onStop = warmUpViewModel::onStopWarmUp,
            ),
        modifier = modifier,
    )
}

/** Set entry's actions, wired to the controller that serves them (ADR-0016's stepper sheet). */
private fun ActiveSessionViewModel.setEntryCallbacks() =
    SetEntryCallbacks(
        onWeightChanged = { setEntry.change(weight = it) },
        onWeightStepped = setEntry::stepWeight,
        onRepsChanged = { setEntry.change(reps = it) },
        onRepsStepped = setEntry::stepReps,
        onSetsChanged = { setEntry.change(sets = it) },
        onSetsStepped = setEntry::stepSets,
        onRpeChanged = { setEntry.change(rpe = it) },
        onConfirm = setEntry::confirm,
        onDismiss = setEntry::dismiss,
    )

/** The set editor's actions (US-04), wired to the controller that serves them. */
private fun ActiveSessionViewModel.setEditCallbacks() =
    SetEditCallbacks(
        onWeightChanged = { setEdit.change(weight = it) },
        onWeightStepped = setEdit::stepWeight,
        onRepsChanged = { setEdit.change(reps = it) },
        onRepsStepped = setEdit::stepReps,
        onRpeChanged = { setEdit.change(rpe = it) },
        onSave = setEdit::save,
        onDelete = setEdit::delete,
        onDismiss = setEdit::dismiss,
    )

/** The guided flow's actions, wired to the ViewModel that serves them. */
private fun ActiveSessionViewModel.guidedActions() =
    GuidedActions(
        onStartExercise = ::onStartExercise,
        onStartNext = ::onStartNextExercise,
        onWeightChanged = { guided.changeSetup(weight = it) },
        onSetupRepsChanged = { guided.changeSetup(reps = it) },
        onRepsChanged = guided::changeReps,
        onSetsChanged = { guided.changeSetup(sets = it) },
        onBegin = guided::begin,
        onDismissSetup = guided::dismissSetup,
        onFinishSet = guided::finishSet,
        onStop = guided::stop,
    )

/**
 * Everything guided mode can be asked to do (US-05a).
 *
 * One record rather than ten loose lambdas on [LoggingScreen], which had accumulated
 * thirty-five of them. Grouped by feature, not by arity: these are the actions of one flow,
 * and they arrive and leave together.
 */
data class GuidedActions(
    val onStartExercise: (SessionExerciseRow) -> Unit = {},
    val onStartNext: (SessionExerciseRow) -> Unit = {},
    val onWeightChanged: (String) -> Unit = {},
    /** The target typed in the start dialog. */
    val onSetupRepsChanged: (String) -> Unit = {},
    /** The count actually managed on the set about to be finished — not the same thing. */
    val onRepsChanged: (String) -> Unit = {},
    val onSetsChanged: (String) -> Unit = {},
    val onBegin: () -> Unit = {},
    val onDismissSetup: () -> Unit = {},
    val onFinishSet: () -> Unit = {},
    val onStop: () -> Unit = {},
)

/**
 * The warm-up, and the two things it can be asked to do (US-28, ADR-0021).
 *
 * A record for the same reason [GuidedActions] is one, and it carries its own state rather
 * than reading it off [SessionUiState] — because a warm-up is not part of the session. It has
 * no row, adds nothing to the duration, and never reaches history, and it comes from
 * [WarmUpViewModel] rather than from the session's own state to keep that true structurally.
 *
 * [elapsed] is null when no warm-up is running, and [Duration.ZERO] for one that has just
 * begun. Those are different states and the screen renders them differently.
 */
data class WarmUp(
    val elapsed: Duration? = null,
    val onStart: () -> Unit = {},
    val onStop: () -> Unit = {},
)

@Composable
internal fun LoggingScreen(
    state: SessionUiState,
    onStartWorkout: () -> Unit,
    onResolveStale: (StaleSessionPrompt) -> Unit,
    onFinishWorkout: () -> Unit = {},
    onAddSet: (SessionExerciseRow) -> Unit = {},
    onRemoveExercise: (SessionExerciseId) -> Unit = {},
    onUndoRemoval: () -> Unit = {},
    guided: GuidedActions = GuidedActions(),
    onSkipRest: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onBrowseCatalog: () -> Unit = {},
    setEntry: SetEntryCallbacks = SetEntryCallbacks.Inert,
    onEditSet: (SessionExerciseRow, ExerciseSet) -> Unit = { _, _ -> },
    setEdit: SetEditCallbacks = SetEditCallbacks.Inert,
    onUndoSetDelete: () -> Unit = {},
    onLogNextSet: (UpNextSet) -> Unit = {},
    onAddExercise: () -> Unit = {},
    warmUp: WarmUp = WarmUp(),
    modifier: Modifier = Modifier,
) {
    val running = state.guided.running

    // Which full-screen thing is showing. History and the workout detail are navigation
    // destinations of their own now (ADR-0024); only the guided flow is still selected by state
    // here, deliberately, for the reason ADR-0017 gives.
    if (running != null) {
        // A lens over the already-logged rows, so leaving it loses nothing (US-05a).
        GuidedRoute(running = running, state = state, guided = guided, modifier = modifier)
    } else {
        SessionScreen(
            state = state,
            onStartWorkout = onStartWorkout,
            onResolveStale = onResolveStale,
            onFinishWorkout = onFinishWorkout,
            onAddSet = onAddSet,
            onRemoveExercise = onRemoveExercise,
            onUndoRemoval = onUndoRemoval,
            guided = guided,
            onSkipRest = onSkipRest,
            onOpenHistory = onOpenHistory,
            onBrowseCatalog = onBrowseCatalog,
            setEntry = setEntry,
            onEditSet = onEditSet,
            setEdit = setEdit,
            onUndoSetDelete = onUndoSetDelete,
            onLogNextSet = onLogNextSet,
            onAddExercise = onAddExercise,
            warmUp = warmUp,
            modifier = modifier,
        )
    }
}

/** The guided flow's own screen (US-05a), kept behind the same `BackHandler` its stop button offers. */
@Composable
private fun GuidedRoute(
    running: GuidedRunning,
    state: SessionUiState,
    guided: GuidedActions,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = guided.onStop)
    GuidedExerciseScreen(
        running = running,
        unit = state.unit,
        restRemaining = state.restRemaining,
        onRepsChanged = guided.onRepsChanged,
        onFinishSet = guided.onFinishSet,
        onStartNext = guided.onStartNext,
        onStop = guided.onStop,
        modifier = modifier,
    )
}

/** The session itself: the body, and the two dialogs that can sit over it. */
@Composable
private fun SessionScreen(
    state: SessionUiState,
    onStartWorkout: () -> Unit,
    onResolveStale: (StaleSessionPrompt) -> Unit,
    onFinishWorkout: () -> Unit,
    onAddSet: (SessionExerciseRow) -> Unit,
    onRemoveExercise: (SessionExerciseId) -> Unit,
    onUndoRemoval: () -> Unit,
    guided: GuidedActions,
    onSkipRest: () -> Unit,
    onOpenHistory: () -> Unit,
    onBrowseCatalog: () -> Unit,
    setEntry: SetEntryCallbacks,
    onEditSet: (SessionExerciseRow, ExerciseSet) -> Unit,
    setEdit: SetEditCallbacks,
    onUndoSetDelete: () -> Unit,
    onLogNextSet: (UpNextSet) -> Unit,
    onAddExercise: () -> Unit,
    warmUp: WarmUp,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        SessionBody(
            state = state,
            onStartWorkout = onStartWorkout,
            onOpenHistory = onOpenHistory,
            onBrowseCatalog = onBrowseCatalog,
            onAddExercise = onAddExercise,
            onAddSet = onAddSet,
            onRemoveExercise = onRemoveExercise,
            onUndoRemoval = onUndoRemoval,
            onStartExercise = guided.onStartExercise,
            onEditSet = onEditSet,
            onUndoSetDelete = onUndoSetDelete,
            onLogNextSet = onLogNextSet,
            onSkipRest = onSkipRest,
            onFinishWorkout = onFinishWorkout,
            warmUp = warmUp,
            modifier = Modifier.padding(padding),
        )

        SessionDialogs(
            state = state,
            onResolveStale = onResolveStale,
            guided = guided,
            setEntry = setEntry,
            setEdit = setEdit,
        )
    }
}

/**
 * Which of the three session states is on screen. Derived from the database rather than from
 * a back stack, which is what makes "reopen and you are back in your session" survive a kill.
 */
@Composable
private fun SessionBody(
    state: SessionUiState,
    onStartWorkout: () -> Unit,
    onOpenHistory: () -> Unit,
    onBrowseCatalog: () -> Unit,
    onAddExercise: () -> Unit,
    onAddSet: (SessionExerciseRow) -> Unit,
    onRemoveExercise: (SessionExerciseId) -> Unit,
    onUndoRemoval: () -> Unit,
    onStartExercise: (SessionExerciseRow) -> Unit,
    onEditSet: (SessionExerciseRow, ExerciseSet) -> Unit,
    onUndoSetDelete: () -> Unit,
    onLogNextSet: (UpNextSet) -> Unit,
    onSkipRest: () -> Unit,
    onFinishWorkout: () -> Unit,
    warmUp: WarmUp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(GymDimens.ScreenPadding),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.isLoading -> CircularProgressIndicator()
            state.activeSession != null ->
                ActiveSession(
                    session = state.activeSession,
                    exercises = state.exercises,
                    unit = state.unit,
                    restRemaining = state.restRemaining,
                    onAddExercise = onAddExercise,
                    onAddSet = onAddSet,
                    onRemoveExercise = onRemoveExercise,
                    canUndoRemoval = state.canUndoRemoval,
                    onUndoRemoval = onUndoRemoval,
                    onStartExercise = onStartExercise,
                    onEditSet = onEditSet,
                    canUndoSetDelete = state.canUndoSetDelete,
                    onUndoSetDelete = onUndoSetDelete,
                    upNext = state.upNext,
                    onLogNextSet = onLogNextSet,
                    onSkipRest = onSkipRest,
                    onFinishWorkout = onFinishWorkout,
                    warmUp = warmUp,
                )
            else -> NoSession(onStartWorkout, onOpenHistory, onBrowseCatalog)
        }
    }
}

/** The things that can sit over the session screen: the stale prompt, guided setup, and set entry. */
@Composable
private fun SessionDialogs(
    state: SessionUiState,
    onResolveStale: (StaleSessionPrompt) -> Unit,
    guided: GuidedActions,
    setEntry: SetEntryCallbacks,
    setEdit: SetEditCallbacks,
) {
    state.stalePrompt?.let { prompt ->
        AbandonedSessionDialog(prompt = prompt, onResolve = onResolveStale)
    }

    state.guided.setup?.let { setup ->
        GuidedSetupDialog(
            setup = setup,
            unit = state.unit,
            onWeightChanged = guided.onWeightChanged,
            onRepsChanged = guided.onSetupRepsChanged,
            onSetsChanged = guided.onSetsChanged,
            onBegin = guided.onBegin,
            onDismiss = guided.onDismissSetup,
        )
    }

    state.setEntry?.let { entry ->
        SetEntrySheet(entry = entry, unit = state.unit, callbacks = setEntry)
    }

    state.setEdit?.let { edit ->
        SetEditSheet(edit = edit, unit = state.unit, callbacks = setEdit)
    }
}

@Composable
private fun NoSession(
    onStartWorkout: () -> Unit,
    onOpenHistory: () -> Unit,
    onBrowseCatalog: () -> Unit,
) {
    // Bottom-weighted rather than centred (ADR-0016): starting a workout is done one-handed,
    // standing, often with the other hand holding something, so the button that matters is
    // the one nearest the thumb.
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = "No workout in progress",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).wrapContentHeight(Alignment.CenterVertically),
        )

        Column(verticalArrangement = Arrangement.spacedBy(GymDimens.Gap)) {
            // Above the primary action but below it in weight: history and the catalog are
            // what you reach for around a workout, not during one (constitution §2).
            SecondaryActionButton(text = "Past workouts", onClick = onOpenHistory)
            // Looking a machine up without starting a workout (US-12).
            SecondaryActionButton(text = "Browse exercises", onClick = onBrowseCatalog)
            PrimaryActionButton(text = "Start workout", onClick = onStartWorkout)
        }
    }
}

@Composable
private fun ActiveSession(
    session: WorkoutSession,
    exercises: List<SessionExerciseRow>,
    unit: WeightUnit,
    restRemaining: Duration?,
    onAddExercise: () -> Unit,
    onAddSet: (SessionExerciseRow) -> Unit,
    onRemoveExercise: (SessionExerciseId) -> Unit,
    canUndoRemoval: Boolean,
    onUndoRemoval: () -> Unit,
    onStartExercise: (SessionExerciseRow) -> Unit,
    onEditSet: (SessionExerciseRow, ExerciseSet) -> Unit,
    canUndoSetDelete: Boolean,
    onUndoSetDelete: () -> Unit,
    upNext: UpNextSet?,
    onLogNextSet: (UpNextSet) -> Unit,
    onSkipRest: () -> Unit,
    onFinishWorkout: () -> Unit,
    warmUp: WarmUp,
) {
    var confirmingFinish by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(GymDimens.Gap),
    ) {
        SessionHeader(session = session, onFinishWorkout = { confirmingFinish = true })

        if (exercises.isEmpty()) {
            Text(
                text = "No exercises yet.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().weight(1f).wrapContentHeight(Alignment.CenterVertically),
            )
        } else {
            SessionExercises(
                exercises = exercises,
                unit = unit,
                onAddSet = onAddSet,
                onRemoveExercise = onRemoveExercise,
                onStartExercise = onStartExercise,
                onEditSet = onEditSet,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }

        // The warm-up sits above the undo bars and the rest, in the same never-over-the-list
        // slot. It is drawn on the session screen but is no part of the session (ADR-0021):
        // it logs nothing, so there is nothing here that a set could be confused with.
        WarmUpPanel(warmUp)

        // Below the list and above the primary action, same slot the rest banner uses: neither
        // may cover the row you were about to read or the button you were about to press
        // (US-02c, US-05).
        if (canUndoRemoval) {
            RemovalUndoBar(onUndoRemoval)
        }

        if (canUndoSetDelete) {
            SetDeleteUndoBar(onUndoSetDelete)
        }

        // Above the bottom action and never over the list: the rest banner displays the stored
        // end time (ADR-0010) and gates nothing, which is US-05's "it never blocks logging the
        // next set". Every "Add set" above it stays live while it counts down.
        restRemaining?.let { remaining ->
            val row = exercises.firstOrNull { it.sessionExercise.id == upNext?.sessionExerciseId }
            RestBanner(
                remaining = remaining,
                upNext = upNext,
                exerciseName = row?.exercise?.name,
                unit = unit,
                onSkipRest = onSkipRest,
                onLogNext = { upNext?.let(onLogNextSet) },
                onAdjust = { row?.let(onAddSet) },
            )
        }

        PrimaryActionButton(text = "Add exercise", onClick = onAddExercise)
    }

    if (confirmingFinish) {
        FinishWorkoutDialog(
            onConfirm = {
                confirmingFinish = false
                onFinishWorkout()
            },
            onDismiss = { confirmingFinish = false },
        )
    }
}

/**
 * The session's title bar, and the one way out of it.
 *
 * "Finish workout" lives up here, away from the thumb (ADR-0016). It is the rarest action on
 * the screen and the only one with no undo — nothing in US-06 reopens a finished session — so
 * it is deliberately the hardest thing here to hit by accident.
 */
@Composable
private fun SessionHeader(
    session: WorkoutSession,
    onFinishWorkout: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = "Workout in progress", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Started ${session.startedAt.asLocalTime()}",
                style = MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier.semantics {
                        contentDescription = "Session started at ${session.startedAt.asLocalTime()}"
                    },
            )
        }
        TextButton(
            onClick = onFinishWorkout,
            modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
        ) {
            Text("Finish")
        }
    }
}

/**
 * The warm-up: a stopwatch, and nothing else (US-28, ADR-0021).
 *
 * Idle, it is one quiet text button — the warm-up is the least of what this screen does and it
 * does not get to look like the most. Running, it counts up at the size the rest countdown uses,
 * because it is read from the same distance.
 *
 * What is deliberately absent: a weight field, a rep field, an exercise name, and any "save".
 * There is nothing to save. Stopping it discards it, which is why the control says "Done"
 * rather than anything that sounds like it writes a row.
 */
@Composable
private fun WarmUpPanel(warmUp: WarmUp) {
    val elapsed = warmUp.elapsed

    if (elapsed == null) {
        TextButton(
            onClick = warmUp.onStart,
            modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
        ) {
            Text("Start warm-up")
        }
        return
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(GymDimens.Gap),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Warm-up", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = elapsed.asCountdown(),
                    style = MaterialTheme.typography.displayMedium,
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Warm-up ${elapsed.asCountdown()} elapsed, not recorded"
                        },
                )
            }
            TextButton(
                onClick = warmUp.onStop,
                modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
            ) {
                Text("Done")
            }
        }
    }
}

/**
 * The rest countdown, at the size you can read from where you are actually standing (ADR-0016).
 *
 * It was an assist chip, which made the most-glanced thing on the screen the smallest. Skip is
 * beside it because it is the only decision the timer offers.
 */
@Composable
private fun RestBanner(
    remaining: Duration,
    upNext: UpNextSet?,
    exerciseName: String?,
    unit: WeightUnit,
    onSkipRest: () -> Unit,
    onLogNext: () -> Unit,
    onAdjust: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(GymDimens.Gap),
            verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Rest", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = remaining.asCountdown(),
                        style = MaterialTheme.typography.displayMedium,
                        modifier =
                            Modifier.semantics {
                                contentDescription = "Rest ${remaining.asCountdown()} remaining"
                            },
                    )
                }
                TextButton(
                    onClick = onSkipRest,
                    modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
                ) {
                    Text("Skip")
                }
            }

            // ADR-0023: the ninety seconds says what is coming, and lets it be logged from here.
            // Absent before the first set of the session — there is nothing to be next yet.
            if (upNext != null) {
                UpNext(upNext = upNext, exerciseName = exerciseName, unit = unit)
                Row(horizontalArrangement = Arrangement.spacedBy(GymDimens.TightGap)) {
                    PrimaryActionButton(
                        text = "Log set ${upNext.setNumber}",
                        onClick = onLogNext,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onAdjust,
                        modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
                    ) {
                        Text("Adjust")
                    }
                }
            }
        }
    }
}

/**
 * What the next set will be, and what the same movement was last time (ADR-0023).
 *
 * Note what is **not** here: no "of N", because nothing in the app knows how many sets you
 * intend — [UpNextSet] has no field it could be rendered from. And no comparison at all when
 * the movement has no earlier session, rather than a zero or a dash pretending to be one
 * (constitution §2.4).
 */
@Composable
private fun UpNext(
    upNext: UpNextSet,
    exerciseName: String?,
    unit: WeightUnit,
) {
    val next = WeightFormatter.format(upNext.prefill.weight?.let { UnitConverter.toKilograms(it, unit) }, unit)
    Column {
        Text("Up next", style = MaterialTheme.typography.titleSmall)
        Text(
            text = exerciseName ?: upNext.exerciseId.value,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text =
                buildString {
                    append("Set ${upNext.setNumber}")
                    append("   ${upNext.prefill.reps} reps")
                    append("   ${next.primary}")
                    next.secondary?.let { append("  ·  $it") }
                },
            style = MaterialTheme.typography.titleMedium,
        )
        upNext.comparison?.let { last ->
            val previous = WeightFormatter.format(last.weightKg, unit)
            Text(
                text = "Last ${last.performedAt.asDay()}  ·  ${last.reps} reps   ${previous.primary}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** The day a set happened, for the rest panel's comparison line. */
private fun Instant.asDay(): String =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).withZone(ZoneId.systemDefault()).format(this)

/**
 * The guard on the one tap in the app that cannot be taken back (ADR-0016).
 *
 * Finishing is once per workout, so the extra tap costs nothing measurable; ending a workout
 * you were halfway through costs the rest of it.
 */
@Composable
private fun FinishWorkoutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finish this workout?") },
        text = { Text("It moves to your past workouts. Sets you have already logged are kept.") },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget)) {
                Text("Finish workout")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget)) {
                Text("Keep going")
            }
        },
    )
}

/**
 * The exercises in the session, each with its sets and its way to add another (US-03).
 *
 * One card per exercise, each ending in a full-width "Add set" (ADR-0016). It used to be a
 * small text button on the row's right edge — the most-tapped control in the app rendered as
 * the smallest thing on screen. "Start exercise" (US-05a) and "Remove" (US-02c) sit above it as
 * a lighter-weight row: "Add set" stays the one filled action per card.
 */
@Composable
private fun SessionExercises(
    exercises: List<SessionExerciseRow>,
    unit: WeightUnit,
    onAddSet: (SessionExerciseRow) -> Unit,
    onRemoveExercise: (SessionExerciseId) -> Unit,
    onStartExercise: (SessionExerciseRow) -> Unit,
    onEditSet: (SessionExerciseRow, ExerciseSet) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GymDimens.Gap)) {
        itemsIndexed(exercises, key = { _, row -> row.sessionExercise.id.value }) { index, row ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(GymDimens.Gap),
                    verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // The catalog entry is only absent if the row outlived its exercise,
                        // which the schema forbids; show the id rather than a blank line.
                        Text(
                            text = row.exercise?.name ?: row.sessionExercise.exerciseId.value,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        // The place added, not the place shown (US-02b): the list itself is
                        // newest-first, but this number counts up in the order you added them,
                        // so removing one leaves a gap on purpose rather than renumbering.
                        Text(
                            text = "${exercises.size - index}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LoggedSets(row.sets, unit) { set -> onEditSet(row, set) }
                    Row(horizontalArrangement = Arrangement.spacedBy(GymDimens.TightGap)) {
                        TextButton(
                            onClick = { onStartExercise(row) },
                            modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
                        ) {
                            Text("Start exercise")
                        }
                        // ADR-0019 replaced ADR-0016's "red means destructive" with a structural
                        // rule, because red is the accent now: a destructive control never
                        // shares a surface with a save, and is outlined rather than filled. This
                        // button predates that rule and still sits beside "Add set" on the same
                        // card — a known exception ADR-0019 flags to revisit, not a pattern to
                        // copy (US-02c).
                        TextButton(
                            onClick = { onRemoveExercise(row.sessionExercise.id) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
                        ) {
                            Text("Remove")
                        }
                    }
                    PrimaryActionButton(text = "Add set", onClick = { onAddSet(row) })
                }
            }
        }
    }
}

/** US-02c's five-second window, worded like history's so the two read alike. */
@Composable
private fun RemovalUndoBar(onUndo: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = GymDimens.Gap),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Exercise removed", style = MaterialTheme.typography.bodyMedium)
            TextButton(
                onClick = onUndo,
                modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
            ) {
                Text("Undo")
            }
        }
    }
}

@Composable
private fun AbandonedSessionDialog(
    prompt: StaleSessionPrompt,
    onResolve: (StaleSessionPrompt) -> Unit,
) {
    // Not dismissible: US-01 offers finish or discard, and nothing else. Leaving the session
    // open is not one of the choices, so there is no way to tap it away.
    AlertDialog(
        onDismissRequest = {},
        title = { Text("You left a workout running") },
        text = { Text(prompt.explanation()) },
        confirmButton = {
            TextButton(onClick = { onResolve(prompt) }) {
                Text(if (prompt is StaleSessionPrompt.Finish) "Finish it" else "Discard it")
            }
        },
    )
}

/**
 * mm:ss, so 90 seconds reads "1:30" rather than "PT1M30S".
 *
 * Arithmetic on [Duration.getSeconds] rather than `toMinutesPart`/`toSecondsPart`, which are
 * API 31 and would crash on the API 26 devices `tech-stack.md` supports.
 */
private fun Duration.asCountdown(): String =
    "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)

private const val SECONDS_PER_MINUTE = 60

/**
 * Schedules the rest notification and asks for permission once (US-05, ADR-0010).
 *
 * The alarm is a side effect of a rest starting, not part of the timer: the timer is the
 * stored end time, so if this never runs the countdown on screen is still correct.
 */
@Composable
private fun RestNotifications(viewModel: ActiveSessionViewModel) {
    val context = LocalContext.current
    val alarm = remember(context) { RestAlarm(context) }
    val restStarted by viewModel.rest.restStarted.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val requestPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Scheduled here, not before launching: RestAlarm checks the permission, so
            // scheduling while the dialog is still open silently does nothing. Found on a
            // device — the countdown looked right and no notification ever arrived.
            scope.launch {
                viewModel.rest.endsAt()?.let(alarm::schedule)
                viewModel.rest.onHandled()
            }
        }

    LaunchedEffect(restStarted) {
        if (!restStarted) return@LaunchedEffect

        val mustAsk =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                viewModel.rest.shouldAskForNotifications()

        if (mustAsk) {
            // Once, ever (US-05). Denial is fine — the countdown still runs on screen.
            viewModel.rest.onNotificationPermissionAsked()
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.rest.endsAt()?.let(alarm::schedule) ?: alarm.cancel()
            viewModel.rest.onHandled()
        }
    }
}

/**
 * The sets already logged against one exercise, each in both units (ADR-0008), and each its own
 * tap target (ADR-0022).
 *
 * These used to be collapsed — three identical sets read as "3 × 12" on one line (ADR-0009).
 * That was fine to read and impossible to correct: one line, three rows, three ids, and no way
 * for a tap to say which. US-04 needs every set reachable, so the grouping went and the set
 * index stays as the label, naming the row it edits.
 */
@Composable
private fun LoggedSets(
    sets: List<ExerciseSet>,
    unit: WeightUnit,
    onEditSet: (ExerciseSet) -> Unit,
) {
    if (sets.isEmpty()) {
        Text("No sets yet", style = MaterialTheme.typography.bodyMedium)
        return
    }

    Column {
        sets.forEach { set ->
            val weight = WeightFormatter.format(set.weightKg, unit)
            Text(
                text =
                    buildString {
                        append("${set.setIndex}.  ${set.reps} reps")
                        append("   ${weight.primary}")
                        weight.secondary?.let { append("  ·  $it") }
                        set.rpe?.let { append("   RPE $it") }
                    },
                // The line you came back to the phone to read, so it takes the role that says
                // so rather than the smallest one there is (ADR-0011).
                style = MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = GymDimens.MinTouchTarget)
                        .clickable { onEditSet(set) }
                        // Named rather than left to the row's text, so the target says what it
                        // does — for TalkBack (M7) as much as for the tests.
                        .semantics { contentDescription = "Edit set ${set.setIndex}" }
                        .wrapContentHeight(Alignment.CenterVertically),
            )
        }
    }
}

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
private fun SetEntrySheet(
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
            enabled =
                entry.reps.toIntOrNull()?.let { it >= 1 } == true &&
                    entry.sets.toIntOrNull()?.let { it >= 1 } == true,
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
            enabled = edit.reps.toIntOrNull()?.let { it >= 1 } == true,
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

        OutlinedTextField(
            value = edit.rpe,
            onValueChange = callbacks.onRpeChanged,
            label = { Text("RPE (optional)") },
            placeholder = { Text("—") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
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

/** US-04's five-second window, worded like the other two so all three read alike. */
@Composable
private fun SetDeleteUndoBar(onUndo: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = GymDimens.Gap),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Set deleted", style = MaterialTheme.typography.bodyMedium)
            TextButton(
                onClick = onUndo,
                modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
            ) {
                Text("Undo")
            }
        }
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

        // Optional (US-03), and left as a plain field: RPE is typed occasionally and
        // deliberately, so it does not earn a stepper. Blank means not recorded, which is
        // not a claim that the set was easy — constitution §2, absence is not a value.
        OutlinedTextField(
            value = entry.rpe,
            onValueChange = callbacks.onRpeChanged,
            label = { Text("RPE (optional)") },
            placeholder = { Text("—") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
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

private fun StaleSessionPrompt.explanation(): String =
    when (this) {
        is StaleSessionPrompt.Finish ->
            "It has been idle for more than ${StaleSessionPromptCopy.THRESHOLD_HOURS} hours. " +
                "We will end it at your last set, ${endedAt.asLocalTime()} — never at a time you were not lifting."

        is StaleSessionPrompt.Discard ->
            "It has been idle for more than ${StaleSessionPromptCopy.THRESHOLD_HOURS} hours and has no sets, " +
                "so there is nothing to keep and no honest end time to record."
    }

private object StaleSessionPromptCopy {
    /** Derived from the policy, so the copy can never drift from the rule it describes. */
    val THRESHOLD_HOURS: Long = StaleSessionPolicy.STALE_AFTER.toHours()
}

private fun Instant.asLocalTime(): String = TIME_FORMAT.format(atZone(ZoneId.systemDefault()))

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

// Sizes live in GymDimens now (ADR-0016), so M7's accessibility pass tunes one file rather
// than grepping four screens for a 48.dp.

@GymPreviews
@Composable
private fun NoSessionPreview() {
    GymTrackerTheme {
        LoggingScreen(SessionUiState(isLoading = false), onStartWorkout = {}, onResolveStale = {})
    }
}

private val previewSession =
    WorkoutSession(
        id = SessionId("preview"),
        userId = UserId("preview"),
        gymName = null,
        startedAt = Instant.parse("2026-07-26T17:10:00Z"),
        endedAt = null,
        metrics = null,
    )

@GymPreviews
@Composable
private fun ActiveSessionPreview() {
    GymTrackerTheme {
        LoggingScreen(
            state = SessionUiState(isLoading = false, activeSession = previewSession),
            onStartWorkout = {},
            onResolveStale = {},
        )
    }
}

/** The screen as it actually looks mid-workout: sets logged, and a rest counting down. */
@GymPreviews
@Composable
private fun RestingPreview() {
    val appearance = SessionExercise(SessionExerciseId("se"), previewSession.id, ExerciseId("bench"), 1)
    GymTrackerTheme {
        LoggingScreen(
            state =
                SessionUiState(
                    isLoading = false,
                    activeSession = previewSession,
                    unit = WeightUnit.LB,
                    restRemaining = Duration.ofSeconds(75),
                    exercises =
                        listOf(
                            SessionExerciseRow(
                                sessionExercise = appearance,
                                exercise = null,
                                sets =
                                    listOf(
                                        ExerciseSet("1", appearance.id, 1, 61.23, 8, null, previewSession.startedAt),
                                        ExerciseSet("2", appearance.id, 2, 61.23, 8, null, previewSession.startedAt),
                                    ),
                            ),
                        ),
                ),
            onStartWorkout = {},
            onResolveStale = {},
        )
    }
}

/**
 * The eight minutes on the treadmill, counting up (US-28).
 *
 * Worth a preview of its own because of what it has to *not* show: no weight, no reps, no
 * exercise, and no save. If a future edit puts any of those here, this preview is where it
 * will look wrong first.
 */
@GymPreviews
@Composable
private fun WarmingUpPreview() {
    GymTrackerTheme {
        LoggingScreen(
            state = SessionUiState(isLoading = false, activeSession = previewSession),
            onStartWorkout = {},
            onResolveStale = {},
            warmUp = WarmUp(elapsed = Duration.ofSeconds(252)),
        )
    }
}
