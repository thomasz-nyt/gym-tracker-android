package com.gymtracker.feature.logging

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.session.StaleSessionPolicy
import com.gymtracker.core.domain.session.StaleSessionPrompt
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
                        onAddExercise = onAddExercise,
                    )
                else -> NoSession(onStartWorkout)
            }
        }

        state.stalePrompt?.let { prompt ->
            AbandonedSessionDialog(prompt = prompt, onResolve = onResolveStale)
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
    onAddExercise: () -> Unit,
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
                        supportingContent = { Text("Logging sets arrives with US-03.") },
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
