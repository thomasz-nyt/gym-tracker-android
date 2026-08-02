package com.gymtracker.feature.logging

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.session.SessionSummary
import com.gymtracker.core.domain.units.WeightFormatter
import com.gymtracker.core.domain.units.WeightUnit
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Workout history (US-06), and deleting from it (US-06a).
 *
 * Only finished workouts are here. The session in progress is on the other screen, which is
 * what makes it impossible to delete the one you are standing in the middle of.
 */
@Composable
internal fun HistoryScreen(
    state: HistoryState,
    unit: WeightUnit,
    onDelete: (SessionId) -> Unit,
    onUndo: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenWorkout: (SessionId) -> Unit = {},
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = HISTORY_PADDING),
            verticalArrangement = Arrangement.spacedBy(HISTORY_GAP),
        ) {
            Text(
                text = "Past workouts",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = HISTORY_PADDING),
            )

            if (state.sessions.isEmpty()) {
                Text(
                    text = "Nothing finished yet. A workout appears here once you end it.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                WorkoutList(
                    sessions = state.sessions,
                    unit = unit,
                    onDelete = onDelete,
                    onOpen = onOpenWorkout,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }

            // Sits below the list rather than floating over it, so it can never cover the row
            // you were about to read or the button you were about to press.
            if (state.canUndo) {
                UndoBar(onUndo)
            }

            TextButton(
                onClick = onDone,
                modifier = Modifier.sizeIn(minHeight = MIN_HISTORY_TARGET),
            ) {
                Text("Done")
            }
        }
    }
}

/** One row per finished workout, newest first, each with the way to delete it (US-06a). */
@Composable
private fun WorkoutList(
    sessions: List<SessionSummary>,
    unit: WeightUnit,
    onDelete: (SessionId) -> Unit,
    onOpen: (SessionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(sessions, key = { it.session.id.value }) { summary ->
            ListItem(
                // Tapping the row opens what it contained (US-06b); "Delete" is a button of
                // its own, so opening cannot be mistaken for deleting.
                modifier =
                    Modifier
                        .sizeIn(minHeight = MIN_HISTORY_TARGET)
                        .clickable { onOpen(summary.session.id) },
                headlineContent = {
                    Text(
                        text = summary.session.startedAt.asWorkoutDate(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                supportingContent = {
                    Text(summary.describe(unit), style = MaterialTheme.typography.bodyMedium)
                },
                trailingContent = {
                    TextButton(
                        onClick = { onDelete(summary.session.id) },
                        modifier = Modifier.sizeIn(minHeight = MIN_HISTORY_TARGET),
                    ) {
                        Text("Delete")
                    }
                },
            )
            HorizontalDivider()
        }
    }
}

/** US-06a's five-second window. Vanishes on its own; nothing about it is a decision to make. */
@Composable
private fun UndoBar(onUndo: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = HISTORY_GAP),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Workout deleted", style = MaterialTheme.typography.bodyMedium)
            TextButton(
                onClick = onUndo,
                modifier = Modifier.sizeIn(minHeight = MIN_HISTORY_TARGET),
            ) {
                Text("Undo")
            }
        }
    }
}

/**
 * The one-line summary under the date: duration, what was done, and how much was moved.
 *
 * Bodyweight sets are named rather than folded into the volume, because their load was never
 * recorded and pretending it was zero would understate the workout (constitution §2).
 */
private fun SessionSummary.describe(unit: WeightUnit): String =
    buildString {
        duration?.let { append("${it.asWorkoutLength()}  ·  ") }
        append("$exerciseCount ${"exercise".plural(exerciseCount)}")
        append("  ·  $setCount ${"set".plural(setCount)}")
        WeightFormatter.formatVolume(volumeKg, unit)?.let { append("  ·  $it") }
        if (bodyweightSetCount > 0) {
            append("  ·  $bodyweightSetCount bodyweight")
        }
    }

private fun String.plural(count: Int): String = if (count == 1) this else "${this}s"

/** "1h 12m", or "48m" for anything under the hour — nobody reads "0h 48m". */
private fun Duration.asWorkoutLength(): String {
    val hours = toHours()
    val minutes = toMinutes() % MINUTES_PER_HOUR
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun Instant.asWorkoutDate(): String = WORKOUT_DATE.format(atZone(ZoneId.systemDefault()))

/** "Fri 1 Aug, 17:10" — the day and the time, which is how a workout is remembered. */
private val WORKOUT_DATE = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.getDefault())

private const val MINUTES_PER_HOUR = 60L
private val HISTORY_PADDING = 24.dp
private val HISTORY_GAP = 12.dp
private val MIN_HISTORY_TARGET = 48.dp

@Preview
@Composable
private fun HistoryPreview() {
    val started = Instant.parse("2026-08-01T17:10:00Z")
    GymTrackerTheme {
        HistoryScreen(
            state =
                HistoryState(
                    isOpen = true,
                    sessions =
                        listOf(
                            SessionSummary(
                                session =
                                    WorkoutSession(
                                        id = SessionId("preview"),
                                        userId = UserId("preview"),
                                        gymName = null,
                                        startedAt = started,
                                        endedAt = started.plus(Duration.ofMinutes(72)),
                                        metrics = null,
                                    ),
                                exerciseCount = 5,
                                setCount = 18,
                                volumeKg = 4120.0,
                                bodyweightSetCount = 3,
                            ),
                        ),
                    canUndo = true,
                ),
            unit = WeightUnit.LB,
            onDelete = {},
            onUndo = {},
            onDone = {},
        )
    }
}
