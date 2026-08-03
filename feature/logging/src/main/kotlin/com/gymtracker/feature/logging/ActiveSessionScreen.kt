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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
 * browse screen, which hands an id back through [pickedExerciseId]. History is the last
 * screen still selected by state within this route — the remainder of ADR-0013.
 *
 * @param pickedExerciseId an exercise chosen on the browse screen, appended to the session
 *   once and then cleared through [onPickHandled].
 */
@Composable
fun LoggingRoute(
    onBrowseCatalog: () -> Unit = {},
    onAddExercise: () -> Unit = {},
    pickedExerciseId: String? = null,
    onPickHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RestNotifications(viewModel)

    // Browse is a destination of its own, so it hands an exercise back through the nav
    // result rather than this screen owning a search overlay (US-12, ADR-0013).
    LaunchedEffect(pickedExerciseId) {
        pickedExerciseId?.let { id ->
            viewModel.onExerciseChosen(ExerciseId(id))
            onPickHandled()
        }
    }

    LoggingScreen(
        state = state,
        onStartWorkout = viewModel::onStartWorkout,
        onFinishWorkout = viewModel::onFinishWorkout,
        onResolveStale = viewModel::onResolveStale,
        onAddSet = viewModel.setEntry::open,
        onSkipRest = viewModel.rest::skip,
        onOpenHistory = viewModel.history::open,
        onBrowseCatalog = onBrowseCatalog,
        onCloseHistory = viewModel.history::close,
        onDeleteWorkout = viewModel.history::delete,
        onUndoDelete = viewModel.history::undo,
        setEntry =
            SetEntryCallbacks(
                onWeightChanged = { viewModel.setEntry.change(weight = it) },
                onWeightStepped = viewModel.setEntry::stepWeight,
                onRepsChanged = { viewModel.setEntry.change(reps = it) },
                onRepsStepped = viewModel.setEntry::stepReps,
                onSetsChanged = { viewModel.setEntry.change(sets = it) },
                onSetsStepped = viewModel.setEntry::stepSets,
                onRpeChanged = { viewModel.setEntry.change(rpe = it) },
                onConfirm = viewModel.setEntry::confirm,
                onDismiss = viewModel.setEntry::dismiss,
            ),
        onAddExercise = onAddExercise,
        modifier = modifier,
    )
}

@Composable
internal fun LoggingScreen(
    state: SessionUiState,
    onStartWorkout: () -> Unit,
    onResolveStale: (StaleSessionPrompt) -> Unit,
    onFinishWorkout: () -> Unit = {},
    onAddSet: (SessionExerciseRow) -> Unit = {},
    onSkipRest: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onBrowseCatalog: () -> Unit = {},
    onCloseHistory: () -> Unit = {},
    onDeleteWorkout: (SessionId) -> Unit = {},
    onUndoDelete: () -> Unit = {},
    setEntry: SetEntryCallbacks = SetEntryCallbacks.Inert,
    onAddExercise: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (state.history.isOpen) {
        // Back leaves history rather than the app — it is a side trip from the session
        // screen, not a second entry point.
        BackHandler(onBack = onCloseHistory)
        HistoryScreen(
            state = state.history,
            unit = state.unit,
            onDelete = onDeleteWorkout,
            onUndo = onUndoDelete,
            onDone = onCloseHistory,
            modifier = modifier,
        )
        return
    }

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        SessionBody(
            state = state,
            onStartWorkout = onStartWorkout,
            onOpenHistory = onOpenHistory,
            onBrowseCatalog = onBrowseCatalog,
            onAddExercise = onAddExercise,
            onAddSet = onAddSet,
            onSkipRest = onSkipRest,
            onFinishWorkout = onFinishWorkout,
            modifier = Modifier.padding(padding),
        )

        SessionDialogs(state = state, onResolveStale = onResolveStale, setEntry = setEntry)
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
    onSkipRest: () -> Unit,
    onFinishWorkout: () -> Unit,
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
    setEntry: SetEntryCallbacks,
) {
    state.stalePrompt?.let { prompt ->
        AbandonedSessionDialog(prompt = prompt, onResolve = onResolveStale)
    }

    state.setEntry?.let { entry ->
        SetEntrySheet(entry = entry, unit = state.unit, callbacks = setEntry)
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
    onSkipRest: () -> Unit,
    onFinishWorkout: () -> Unit,
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
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }

        // Above the bottom action and never over the list: the rest banner displays the stored
        // end time (ADR-0010) and gates nothing, which is US-05's "it never blocks logging the
        // next set". Every "Add set" above it stays live while it counts down.
        restRemaining?.let { remaining -> RestBanner(remaining, onSkipRest) }

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
 * The rest countdown, at the size you can read from where you are actually standing (ADR-0016).
 *
 * It was an assist chip, which made the most-glanced thing on the screen the smallest. Skip is
 * beside it because it is the only decision the timer offers.
 */
@Composable
private fun RestBanner(
    remaining: Duration,
    onSkipRest: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(GymDimens.Gap),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = GymDimens.Gap, vertical = GymDimens.TightGap),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Rest", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = remaining.asCountdown(),
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.semantics { contentDescription = "Rest ${remaining.asCountdown()} remaining" },
                )
            }
            TextButton(
                onClick = onSkipRest,
                modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
            ) {
                Text("Skip")
            }
        }
    }
}

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
 * the smallest thing on screen.
 */
@Composable
private fun SessionExercises(
    exercises: List<SessionExerciseRow>,
    unit: WeightUnit,
    onAddSet: (SessionExerciseRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GymDimens.Gap)) {
        items(exercises, key = { it.sessionExercise.id.value }) { row ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(GymDimens.Gap),
                    verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
                ) {
                    // The catalog entry is only absent if the row outlived its exercise,
                    // which the schema forbids; show the id rather than a blank line.
                    Text(
                        text = row.exercise?.name ?: row.sessionExercise.exerciseId.value,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LoggedSets(row.sets, unit)
                    PrimaryActionButton(text = "Add set", onClick = { onAddSet(row) })
                }
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
 * Set entry (US-03), as a bottom sheet with a stepper on every number (ADR-0016).
 *
 * Weight and reps arrive prefilled from the member's last set of this exercise, so when the
 * numbers are already right — which is most of the time — confirming is still a single tap and
 * the two-tap path is untouched. What changed is the case where they are *nearly* right: one
 * plate up is now a press rather than a keyboard.
 *
 * A sheet rather than a centred dialog because this is the screen you use with one hand, mid
 * workout, and the bottom of the phone is where that hand already is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetEntrySheet(
    entry: SetEntry,
    unit: WeightUnit,
    callbacks: SetEntryCallbacks,
) {
    ModalBottomSheet(onDismissRequest = callbacks.onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = GymDimens.ScreenPadding)
                    .padding(bottom = GymDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(GymDimens.Gap),
        ) {
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

            PrimaryActionButton(
                text = "Save set",
                onClick = callbacks.onConfirm,
                enabled =
                    entry.reps.toIntOrNull()?.let { it >= 1 } == true &&
                        entry.sets.toIntOrNull()?.let { it >= 1 } == true,
            )
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
