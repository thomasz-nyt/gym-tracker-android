package com.gymtracker.feature.logging

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import com.gymtracker.core.domain.units.UnitConverter
import com.gymtracker.core.domain.units.WeightFormatter
import com.gymtracker.core.domain.units.WeightUnit
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

    LoggingScreen(
        state = state,
        onStartWorkout = viewModel::onStartWorkout,
        onResolveStale = viewModel::onResolveStale,
        onAddSet = viewModel.setEntry::open,
        onSetWeightChanged = { viewModel.setEntry.change(weight = it) },
        onSetRepsChanged = { viewModel.setEntry.change(reps = it) },
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
    onAddSet: (SessionExerciseRow) -> Unit = {},
    onSetWeightChanged: (String) -> Unit = {},
    onSetRepsChanged: (String) -> Unit = {},
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

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(SCREEN_PADDING),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()
                state.activeSession != null ->
                    ActiveSession(
                        session = state.activeSession,
                        exercises = state.exercises,
                        unit = state.unit,
                        onAddExercise = onAddExercise,
                        onAddSet = onAddSet,
                    )
                else -> NoSession(onStartWorkout)
            }
        }

        state.stalePrompt?.let { prompt ->
            AbandonedSessionDialog(prompt = prompt, onResolve = onResolveStale)
        }

        state.setEntry?.let { entry ->
            SetEntryDialog(
                entry = entry,
                unit = state.unit,
                onWeightChanged = onSetWeightChanged,
                onRepsChanged = onSetRepsChanged,
                onConfirm = onConfirmSet,
                onDismiss = onSetEntryDismissed,
            )
        }
    }
}

@Composable
private fun NoSession(onStartWorkout: () -> Unit) {
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
    }
}

@Composable
private fun ActiveSession(
    session: WorkoutSession,
    exercises: List<SessionExerciseRow>,
    unit: WeightUnit,
    onAddExercise: () -> Unit,
    onAddSet: (SessionExerciseRow) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Text(text = "Workout in progress", style = MaterialTheme.typography.titleLarge)
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
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
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

        Button(
            onClick = onAddExercise,
            modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET),
        ) {
            Text("Add exercise")
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

/** The sets already logged against one exercise, each in both units (ADR-0008). */
@Composable
private fun LoggedSets(
    sets: List<ExerciseSet>,
    unit: WeightUnit,
) {
    if (sets.isEmpty()) {
        Text("No sets yet", style = MaterialTheme.typography.bodySmall)
        return
    }

    Column {
        sets.forEach { set ->
            val weight = WeightFormatter.format(set.weightKg, unit)
            Text(
                text =
                    buildString {
                        append("${set.setIndex}.  ${weight.primary} × ${set.reps}")
                        weight.secondary?.let { append("   ·  $it") }
                    },
                style = MaterialTheme.typography.bodySmall,
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
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val enteredKilograms =
        entry.weight
            .trim()
            .toDoubleOrNull()
            ?.let { UnitConverter.toKilograms(it, unit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.exerciseName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                OutlinedTextField(
                    value = entry.weight,
                    onValueChange = onWeightChanged,
                    label = { Text("Weight (${unit.name.lowercase()})") },
                    placeholder = { Text("Bodyweight") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                // The other unit, live, so nobody converts in their head mid-set (ADR-0008).
                enteredKilograms?.let { kilograms ->
                    WeightFormatter.format(kilograms, unit).secondary?.let { other ->
                        Text(other, style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedTextField(
                    value = entry.reps,
                    onValueChange = onRepsChanged,
                    label = { Text("Reps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
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
                enabled = entry.reps.toIntOrNull()?.let { it >= 1 } == true,
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
