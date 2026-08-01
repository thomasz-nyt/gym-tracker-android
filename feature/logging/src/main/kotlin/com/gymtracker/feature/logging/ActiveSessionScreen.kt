package com.gymtracker.feature.logging

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
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
 * The core-loop screen. US-01 only: start a session, come back to it, resolve one that was
 * left running. Exercises and sets arrive in US-02 and US-03.
 *
 * There is deliberately no navigation graph yet. Which screen you see is derived from the
 * database, not from a back stack, which is what makes "reopen and you are back in your
 * session" true even after the process is killed.
 */
@Composable
fun LoggingRoute(
    modifier: Modifier = Modifier,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RestNotifications(viewModel)

    LoggingScreen(
        state = state,
        onStartWorkout = viewModel::onStartWorkout,
        onFinishWorkout = viewModel::onFinishWorkout,
        onResolveStale = viewModel::onResolveStale,
        onAddSet = viewModel.setEntry::open,
        onSkipRest = viewModel.rest::skip,
        onOpenHistory = viewModel.history::open,
        onCloseHistory = viewModel.history::close,
        onDeleteWorkout = viewModel.history::delete,
        onUndoDelete = viewModel.history::undo,
        onSetWeightChanged = { viewModel.setEntry.change(weight = it) },
        onSetRepsChanged = { viewModel.setEntry.change(reps = it) },
        onSetCountChanged = { viewModel.setEntry.change(sets = it) },
        onSetRpeChanged = { viewModel.setEntry.change(rpe = it) },
        onConfirmSet = viewModel.setEntry::confirm,
        onSetEntryDismissed = viewModel.setEntry::dismiss,
        onAddExercise = viewModel::onAddExerciseClicked,
        onQueryChanged = viewModel::onQueryChanged,
        onExerciseChosen = viewModel::onExerciseChosen,
        onSearchDismissed = viewModel::onSearchDismissed,
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
    onCloseHistory: () -> Unit = {},
    onDeleteWorkout: (SessionId) -> Unit = {},
    onUndoDelete: () -> Unit = {},
    onSetWeightChanged: (String) -> Unit = {},
    onSetRepsChanged: (String) -> Unit = {},
    onSetCountChanged: (String) -> Unit = {},
    onSetRpeChanged: (String) -> Unit = {},
    onConfirmSet: () -> Unit = {},
    onSetEntryDismissed: () -> Unit = {},
    onAddExercise: () -> Unit = {},
    onQueryChanged: (String) -> Unit = {},
    onExerciseChosen: (ExerciseId) -> Unit = {},
    onSearchDismissed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (state.isSearching) {
        ExerciseSearch(
            query = state.query,
            results = state.results,
            onQueryChanged = onQueryChanged,
            onExerciseChosen = onExerciseChosen,
            onDismiss = onSearchDismissed,
            modifier = modifier,
        )
        return
    }

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
            onAddExercise = onAddExercise,
            onAddSet = onAddSet,
            onSkipRest = onSkipRest,
            onFinishWorkout = onFinishWorkout,
            modifier = Modifier.padding(padding),
        )

        SessionDialogs(
            state = state,
            onResolveStale = onResolveStale,
            onSetWeightChanged = onSetWeightChanged,
            onSetRepsChanged = onSetRepsChanged,
            onSetCountChanged = onSetCountChanged,
            onSetRpeChanged = onSetRpeChanged,
            onConfirmSet = onConfirmSet,
            onSetEntryDismissed = onSetEntryDismissed,
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
    onAddExercise: () -> Unit,
    onAddSet: (SessionExerciseRow) -> Unit,
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
                    onSkipRest = onSkipRest,
                    onFinishWorkout = onFinishWorkout,
                )
            else -> NoSession(onStartWorkout, onOpenHistory)
        }
    }
}

/** The two things that can sit over the session screen: the stale prompt, and set entry. */
@Composable
private fun SessionDialogs(
    state: SessionUiState,
    onResolveStale: (StaleSessionPrompt) -> Unit,
    onSetWeightChanged: (String) -> Unit,
    onSetRepsChanged: (String) -> Unit,
    onSetCountChanged: (String) -> Unit,
    onSetRpeChanged: (String) -> Unit,
    onConfirmSet: () -> Unit,
    onSetEntryDismissed: () -> Unit,
) {
    state.stalePrompt?.let { prompt ->
        AbandonedSessionDialog(prompt = prompt, onResolve = onResolveStale)
    }

    state.setEntry?.let { entry ->
        SetEntryDialog(
            entry = entry,
            unit = state.unit,
            onWeightChanged = onSetWeightChanged,
            onRepsChanged = onSetRepsChanged,
            onSetsChanged = onSetCountChanged,
            onRpeChanged = onSetRpeChanged,
            onConfirm = onConfirmSet,
            onDismiss = onSetEntryDismissed,
        )
    }
}

@Composable
private fun NoSession(
    onStartWorkout: () -> Unit,
    onOpenHistory: () -> Unit,
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
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
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
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(exercises, key = { it.sessionExercise.id.value }) { row ->
            ListItem(
                headlineContent = {
                    // The catalog entry is only absent if the row outlived its exercise,
                    // which the schema forbids; show the id rather than a blank line.
                    Text(row.exercise?.name ?: row.sessionExercise.exerciseId.value)
                },
                overlineContent = { Text("${row.sessionExercise.position}") },
                supportingContent = { LoggedSets(row.sets, unit) },
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

/**
 * Catalog search (US-02). Results are ranked by how recently the member used each exercise,
 * then alphabetically — the ordering comes from the query, not from this list.
 */
@Composable
private fun ExerciseSearch(
    query: String,
    results: List<Exercise>,
    onQueryChanged: (String) -> Unit,
    onExerciseChosen: (ExerciseId) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = SCREEN_PADDING),
            verticalArrangement = Arrangement.spacedBy(GAP),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                label = { Text("Search exercises") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (results.isEmpty()) {
                Text(
                    text = if (query.isBlank()) "Loading the catalog…" else "Nothing matches \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(results, key = { it.id.value }) { exercise ->
                    ListItem(
                        headlineContent = { Text(exercise.name) },
                        supportingContent = {
                            Text(
                                exercise.equipment.name
                                    .lowercase()
                                    .replaceFirstChar { it.uppercase() },
                            )
                        },
                        leadingContent = { ExerciseThumbnail(exercise.imageAsset) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .sizeIn(minHeight = MIN_TOUCH_TARGET)
                                .clickable { onExerciseChosen(exercise.id) },
                    )
                    HorizontalDivider()
                }
            }

            TextButton(onClick = onDismiss, modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET)) {
                Text("Cancel")
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

/**
 * A bundled photo of the movement, for the starter exercises that ship one (ADR-0007).
 *
 * When no image is bundled the space is left empty rather than filled with a generic icon:
 * an image that says nothing is worse than no image, and constitution §2 says absent is
 * shown as absent. The rest of the catalog gets media at M3.
 */
@Composable
private fun ExerciseThumbnail(imageAsset: String?) {
    if (imageAsset == null) {
        Box(modifier = Modifier.size(THUMBNAIL))
        return
    }

    AsyncImage(
        model = "file:///android_asset/exercise_images/$imageAsset",
        // The name is right beside it, so repeating it would only add noise for TalkBack.
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            Modifier
                .size(THUMBNAIL)
                .clip(RoundedCornerShape(THUMBNAIL_CORNER))
                .background(MaterialTheme.colorScheme.surfaceVariant),
    )
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
private val THUMBNAIL = 56.dp
private val THUMBNAIL_CORNER = 8.dp

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
