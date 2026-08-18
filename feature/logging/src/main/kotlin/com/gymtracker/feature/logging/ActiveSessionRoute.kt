package com.gymtracker.feature.logging

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.rest.UpNextSet
import com.gymtracker.core.domain.session.StaleSessionPrompt
import com.gymtracker.feature.logging.rest.RestAlarm
import com.gymtracker.feature.logging.session.SessionBody
import com.gymtracker.feature.logging.session.SessionDialogs
import kotlinx.coroutines.launch
import java.time.Duration

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
    onOpenRoutines: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    pickedExerciseIds: List<String> = emptyList(),
    onPicksHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
    warmUpViewModel: WarmUpViewModel = hiltViewModel(),
    trainHomeViewModel: TrainHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val warmUpElapsed by warmUpViewModel.elapsed.collectAsStateWithLifecycle()
    val nextRoutine by trainHomeViewModel.nextRoutine.collectAsStateWithLifecycle()
    RestNotifications(viewModel)

    // Re-ranked whenever NoSession is what's on screen (US-36) — after finishing a workout, or
    // after a visit to Routines that might have added, deleted or renamed one. Firing on every
    // activeSession change rather than only on the null->non-null->null edge is redundant with
    // the ViewModel's own init-time read the first time, but cheap: one Room read against
    // already-observed tables, not a new subscription.
    LaunchedEffect(state.activeSession) {
        if (state.activeSession == null) trainHomeViewModel.refresh()
    }

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
        onFinishWorkout = viewModel.finish::confirm,
        onFinishSummaryDismissed = viewModel.finish::dismiss,
        onResolveStale = viewModel::onResolveStale,
        onAddSet = viewModel.setEntry::open,
        onRemoveExercise = viewModel.removal::remove,
        onUndoRemoval = viewModel.removal::undo,
        onSelectExercise = viewModel.selection::select,
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
        nextRoutine = nextRoutine,
        onStartFromRoutine = viewModel::onStartFromRoutine,
        onOpenRoutines = onOpenRoutines,
        onOpenSettings = onOpenSettings,
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
        onSetupWeightStepped = guided::stepSetupWeight,
        onSetupRepsChanged = { guided.changeSetup(reps = it) },
        onSetupRepsStepped = guided::stepSetupReps,
        onRepsChanged = guided::changeReps,
        onRepsStepped = guided::stepReps,
        onSetsChanged = { guided.changeSetup(sets = it) },
        onSetupSetsStepped = guided::stepSetupSets,
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
    /** Steps the start dialog's weight by one increment of the member's unit. */
    val onSetupWeightStepped: (Int) -> Unit = {},
    /** The target typed in the start dialog. */
    val onSetupRepsChanged: (String) -> Unit = {},
    /** Steps that same target by -1 or +1, before the flow has begun. */
    val onSetupRepsStepped: (Int) -> Unit = {},
    /** The count actually managed on the set about to be finished — not the same thing. */
    val onRepsChanged: (String) -> Unit = {},
    /** Steps that same count by -1 or +1 (ADR-0033), sharing [onFinishSet]'s own fallback. */
    val onRepsStepped: (Int) -> Unit = {},
    val onSetsChanged: (String) -> Unit = {},
    /** Steps the start dialog's set target by -1 or +1. */
    val onSetupSetsStepped: (Int) -> Unit = {},
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
    onFinishSummaryDismissed: () -> Unit = {},
    onAddSet: (SessionExerciseRow) -> Unit = {},
    onRemoveExercise: (SessionExerciseId) -> Unit = {},
    onUndoRemoval: () -> Unit = {},
    onSelectExercise: (SessionExerciseId) -> Unit = {},
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
    nextRoutine: Routine? = null,
    onStartFromRoutine: (RoutineId) -> Unit = {},
    onOpenRoutines: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    warmUp: WarmUp = WarmUp(),
    modifier: Modifier = Modifier,
) {
    val running = state.guided.running

    // Which full-screen thing is showing. History and the workout detail are navigation
    // destinations of their own now (ADR-0024); only the guided flow and the finish summary
    // (US-31) are still selected by state here, deliberately, for the reason ADR-0017 gives.
    if (running != null) {
        // A lens over the already-logged rows, so leaving it loses nothing (US-05a).
        GuidedRoute(running = running, state = state, guided = guided, modifier = modifier)
    } else {
        SessionScreen(
            state = state,
            onStartWorkout = onStartWorkout,
            onResolveStale = onResolveStale,
            onFinishWorkout = onFinishWorkout,
            onFinishSummaryDismissed = onFinishSummaryDismissed,
            onAddSet = onAddSet,
            onRemoveExercise = onRemoveExercise,
            onUndoRemoval = onUndoRemoval,
            onSelectExercise = onSelectExercise,
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
            nextRoutine = nextRoutine,
            onStartFromRoutine = onStartFromRoutine,
            onOpenRoutines = onOpenRoutines,
            onOpenSettings = onOpenSettings,
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
        onRepsStepped = guided.onRepsStepped,
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
    onFinishSummaryDismissed: () -> Unit,
    onAddSet: (SessionExerciseRow) -> Unit,
    onRemoveExercise: (SessionExerciseId) -> Unit,
    onUndoRemoval: () -> Unit,
    onSelectExercise: (SessionExerciseId) -> Unit,
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
    nextRoutine: Routine?,
    onStartFromRoutine: (RoutineId) -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenSettings: () -> Unit,
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
            onFinishSummaryDismissed = onFinishSummaryDismissed,
            nextRoutine = nextRoutine,
            onStartFromRoutine = onStartFromRoutine,
            onOpenRoutines = onOpenRoutines,
            onOpenSettings = onOpenSettings,
            onSelectExercise = onSelectExercise,
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
