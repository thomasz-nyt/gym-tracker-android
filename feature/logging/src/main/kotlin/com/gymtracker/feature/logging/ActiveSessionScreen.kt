package com.gymtracker.feature.logging

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.session.StaleSessionPolicy
import com.gymtracker.core.domain.session.StaleSessionPrompt
import com.gymtracker.core.domain.set.SetGroup
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
 * Picking an exercise is a destination now (US-12): "Add exercise" navigates to the shared
 * browse screen, which hands its picks back through [pickedExerciseIds]. Three screens are
 * still selected by state inside this route — history, the workout opened from it (US-06b),
 * and the guided flow (US-05a). The first two are the remainder of ADR-0013; the third stays
 * here on purpose, for the reason ADR-0016 gives.
 *
 * @param pickedExerciseIds the exercises chosen on the browse screen, in the order they were
 *   picked, appended to the session once and then cleared through [onPicksHandled]. A list
 *   because one visit may add several (US-02a), including the same exercise twice (US-02).
 */
@Composable
fun LoggingRoute(
    onBrowseCatalog: () -> Unit = {},
    onAddExercise: () -> Unit = {},
    pickedExerciseIds: List<String> = emptyList(),
    onPicksHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
        onOpenHistory = viewModel.history::open,
        onBrowseCatalog = onBrowseCatalog,
        onCloseHistory = viewModel.history::close,
        onDeleteWorkout = viewModel.history::delete,
        onUndoDelete = viewModel.history::undo,
        onOpenWorkout = viewModel.history::openWorkout,
        onCloseWorkout = viewModel.history::closeWorkout,
        setEntry = viewModel.setEntryActions(),
        onAddExercise = onAddExercise,
        modifier = modifier,
    )
}

/**
 * Everything the set-entry dialog can be asked to do (US-03).
 *
 * Grouped for the same reason as [GuidedActions]. The two-tap path runs through
 * [onConfirm]; nothing here changes what it does.
 */
data class SetEntryActions(
    val onWeightChanged: (String) -> Unit = {},
    val onRepsChanged: (String) -> Unit = {},
    val onSetsChanged: (String) -> Unit = {},
    val onRpeChanged: (String) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/** Set entry's actions, wired to the controller that serves them. */
private fun ActiveSessionViewModel.setEntryActions() =
    SetEntryActions(
        onWeightChanged = { setEntry.change(weight = it) },
        onRepsChanged = { setEntry.change(reps = it) },
        onSetsChanged = { setEntry.change(sets = it) },
        onRpeChanged = { setEntry.change(rpe = it) },
        onConfirm = setEntry::confirm,
        onDismiss = setEntry::dismiss,
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
    onCloseHistory: () -> Unit = {},
    onDeleteWorkout: (SessionId) -> Unit = {},
    onUndoDelete: () -> Unit = {},
    onOpenWorkout: (SessionId) -> Unit = {},
    onCloseWorkout: () -> Unit = {},
    setEntry: SetEntryActions = SetEntryActions(),
    onAddExercise: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val running = state.guided.running
    val openWorkout = state.history.detail

    // Which full-screen thing is showing. A `when` rather than a chain of early returns: the
    // branches are exclusive and reading them as one list is the point (ADR-0013 keeps these
    // out of the navigation graph deliberately — see ADR-0016 for the guided one).
    when {
        // Guided mode takes the screen while it runs, but it is a lens over the same rows —
        // every set is already logged, so leaving it loses nothing (US-05a).
        running != null -> {
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

        // Before the list, because a workout is only ever open while history is (US-06b).
        openWorkout != null -> {
            BackHandler(onBack = onCloseWorkout)
            WorkoutDetailScreen(
                detail = openWorkout,
                unit = state.unit,
                onBack = onCloseWorkout,
                modifier = modifier,
            )
        }

        state.history.isOpen -> {
            // Back leaves history rather than the app — it is a side trip from the session
            // screen, not a second entry point.
            BackHandler(onBack = onCloseHistory)
            HistoryScreen(
                state = state.history,
                unit = state.unit,
                onDelete = onDeleteWorkout,
                onUndo = onUndoDelete,
                onDone = onCloseHistory,
                onOpenWorkout = onOpenWorkout,
                modifier = modifier,
            )
        }

        else ->
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
                onAddExercise = onAddExercise,
                modifier = modifier,
            )
    }
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
    setEntry: SetEntryActions,
    onAddExercise: () -> Unit,
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
            onSkipRest = onSkipRest,
            onFinishWorkout = onFinishWorkout,
            modifier = Modifier.padding(padding),
        )

        SessionDialogs(
            state = state,
            onResolveStale = onResolveStale,
            guided = guided,
            setEntry = setEntry,
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
    onSkipRest: () -> Unit,
    onFinishWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(SCREEN_PADDING),
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
                    onSkipRest = onSkipRest,
                    onFinishWorkout = onFinishWorkout,
                )
            else -> NoSession(onStartWorkout, onOpenHistory, onBrowseCatalog)
        }
    }
}

/** The two things that can sit over the session screen: the stale prompt, and set entry. */
@Composable
private fun SessionDialogs(
    state: SessionUiState,
    onResolveStale: (StaleSessionPrompt) -> Unit,
    setEntry: SetEntryActions,
    guided: GuidedActions,
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
        SetEntryDialog(
            entry = entry,
            unit = state.unit,
            onWeightChanged = setEntry.onWeightChanged,
            onRepsChanged = setEntry.onRepsChanged,
            onSetsChanged = setEntry.onSetsChanged,
            onRpeChanged = setEntry.onRpeChanged,
            onConfirm = setEntry.onConfirm,
            onDismiss = setEntry.onDismiss,
        )
    }
}

@Composable
private fun NoSession(
    onStartWorkout: () -> Unit,
    onOpenHistory: () -> Unit,
    onBrowseCatalog: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Text(
            text = "No workout in progress",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onStartWorkout,
            // 48dp is the accessibility minimum the M7 pass will audit; start as we mean to go on.
            modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET),
        ) {
            Text("Start workout")
        }
        // Below "Start workout", and only here: the core loop is what this screen is for, and
        // history is what you look at afterwards (constitution §2, principle 1).
        TextButton(
            onClick = onOpenHistory,
            modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET),
        ) {
            Text("Past workouts")
        }
        // Looking a machine up without starting a workout (US-12). Below the two actions
        // that are about training, because that is what this screen is for.
        TextButton(
            onClick = onBrowseCatalog,
            modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET),
        ) {
            Text("Browse exercises")
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
    onSkipRest: () -> Unit,
    onFinishWorkout: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Text(text = "Workout in progress", style = MaterialTheme.typography.titleLarge)

        // Sits above the list and gates nothing: "Add set" stays live throughout, which is
        // US-05's "it never blocks logging the next set".
        restRemaining?.let { remaining ->
            AssistChip(
                onClick = onSkipRest,
                label = { Text("Rest ${remaining.asCountdown()}  ·  Skip") },
                modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET),
            )
        }
        Text(
            text = "Started ${session.startedAt.asLocalTime()}",
            style = MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier.semantics {
                    contentDescription = "Session started at ${session.startedAt.asLocalTime()}"
                },
        )

        if (exercises.isEmpty()) {
            Text(
                text = "No exercises yet.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        } else {
            SessionExercises(
                exercises = exercises,
                unit = unit,
                onAddSet = onAddSet,
                onRemoveExercise = onRemoveExercise,
                onStartExercise = onStartExercise,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }

        // Below the list rather than floating over it, as in history: it can never cover the
        // row you were about to read or the button you were about to press (US-02c).
        if (canUndoRemoval) {
            RemovalUndoBar(onUndoRemoval)
        }

        Button(
            onClick = onAddExercise,
            modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET),
        ) {
            Text("Add exercise")
        }
        // A text button rather than a second filled one: ending the workout is the rarer
        // action of the two, and it should not compete with "Add exercise" for the thumb.
        TextButton(
            onClick = onFinishWorkout,
            modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET),
        ) {
            Text("Finish workout")
        }
    }
}

/** The exercises in the session, each with its sets and its way to add another (US-03). */
@Composable
private fun SessionExercises(
    exercises: List<SessionExerciseRow>,
    unit: WeightUnit,
    onAddSet: (SessionExerciseRow) -> Unit,
    onRemoveExercise: (SessionExerciseId) -> Unit,
    onStartExercise: (SessionExerciseRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        itemsIndexed(exercises, key = { _, row -> row.sessionExercise.id.value }) { index, row ->
            ListItem(
                headlineContent = {
                    // The catalog entry is only absent if the row outlived its exercise,
                    // which the schema forbids; show the id rather than a blank line.
                    Text(row.exercise?.name ?: row.sessionExercise.exerciseId.value)
                },
                // The place in the list as shown, not `position` (US-02b). Removing an exercise
                // leaves a gap in the stored positions on purpose; the list closes it.
                overlineContent = { Text("${exercises.size - index}") },
                supportingContent = {
                    Column {
                        LoggedSets(row.sets, unit)
                        // A secondary line rather than more trailing buttons: "Add set" must
                        // keep its place and its label, because the two-tap path of US-03
                        // runs through it (constitution §2.1, ADR-0016).
                        Row {
                            TextButton(
                                onClick = { onStartExercise(row) },
                                modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET),
                            ) {
                                Text("Start exercise")
                            }
                            TextButton(
                                onClick = { onRemoveExercise(row.sessionExercise.id) },
                                modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET),
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                },
                trailingContent = {
                    TextButton(
                        onClick = { onAddSet(row) },
                        modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET),
                    ) {
                        Text("Add set")
                    }
                },
            )
            HorizontalDivider()
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
            modifier = Modifier.padding(horizontal = GAP),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Exercise removed", style = MaterialTheme.typography.bodyMedium)
            TextButton(
                onClick = onUndo,
                modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET),
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

/** The sets already logged against one exercise, each in both units (ADR-0008). */
@Composable
private fun LoggedSets(
    sets: List<ExerciseSet>,
    unit: WeightUnit,
) {
    if (sets.isEmpty()) {
        Text("No sets yet", style = MaterialTheme.typography.bodyMedium)
        return
    }

    Column {
        // Identical consecutive sets read as "3 × 12" rather than three near-identical
        // lines (ADR-0009). The rows underneath stay separate.
        SetGroup.of(sets).forEach { group ->
            val weight = WeightFormatter.format(group.weightKg, unit)
            Text(
                text =
                    buildString {
                        if (group.count > 1) {
                            append("${group.count} × ${group.reps}")
                        } else {
                            append("${group.firstSetIndex}.  ${group.reps} reps")
                        }
                        append("   ${weight.primary}")
                        weight.secondary?.let { append("  ·  $it") }
                        group.rpe?.let { append("   RPE $it") }
                    },
                // The line you came back to the phone to read, so it takes the role that says
                // so rather than the smallest one there is (ADR-0011).
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/**
 * Set entry (US-03). Weight and reps arrive prefilled from the member's last set of this
 * exercise, so when the numbers are already right, confirming is a single tap.
 */
@Composable
private fun SetEntryDialog(
    entry: SetEntry,
    unit: WeightUnit,
    onWeightChanged: (String) -> Unit,
    onRepsChanged: (String) -> Unit,
    onSetsChanged: (String) -> Unit,
    onRpeChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.exerciseName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                WeightField(entry.weight, unit, onWeightChanged)

                Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    OutlinedTextField(
                        value = entry.sets,
                        onValueChange = onSetsChanged,
                        label = { Text("Sets") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = entry.reps,
                        onValueChange = onRepsChanged,
                        label = { Text("Reps") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    // Optional (US-03). Left blank means not recorded, which is not a claim
                    // that the set was easy — constitution §2, absence is not a value.
                    OutlinedTextField(
                        value = entry.rpe,
                        onValueChange = onRpeChanged,
                        label = { Text("RPE") },
                        placeholder = { Text("—") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!entry.prefilled) {
                    Text(
                        "First time logging this one.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled =
                    entry.reps.toIntOrNull()?.let { it >= 1 } == true &&
                        entry.sets.toIntOrNull()?.let { it >= 1 } == true,
                modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET),
            ) {
                Text("Save set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * The weight field, with the other unit updating live beneath it (ADR-0008), so nobody has to
 * convert in their head between sets.
 */
@Composable
private fun WeightField(
    value: String,
    unit: WeightUnit,
    onChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChanged,
            label = { Text("Weight (${unit.name.lowercase()})") },
            placeholder = { Text("Bodyweight") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        value
            .trim()
            .toDoubleOrNull()
            ?.let { typed -> WeightFormatter.format(UnitConverter.toKilograms(typed, unit), unit).secondary }
            ?.let { other -> Text(other, style = MaterialTheme.typography.bodySmall) }
    }
}

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

private val SCREEN_PADDING = 24.dp
private val GAP = 12.dp
private val MIN_TOUCH_TARGET = 48.dp

@Preview
@Composable
private fun NoSessionPreview() {
    GymTrackerTheme {
        LoggingScreen(SessionUiState(isLoading = false), onStartWorkout = {}, onResolveStale = {})
    }
}

@Preview
@Composable
private fun ActiveSessionPreview() {
    GymTrackerTheme {
        LoggingScreen(
            state =
                SessionUiState(
                    isLoading = false,
                    activeSession =
                        WorkoutSession(
                            id = SessionId("preview"),
                            userId = UserId("preview"),
                            gymName = null,
                            startedAt = Instant.parse("2026-07-26T17:10:00Z"),
                            endedAt = null,
                            metrics = null,
                        ),
                ),
            onStartWorkout = {},
            onResolveStale = {},
        )
    }
}
