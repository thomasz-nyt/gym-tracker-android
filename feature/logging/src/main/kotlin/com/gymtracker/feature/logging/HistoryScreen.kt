package com.gymtracker.feature.logging

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.core.designsystem.component.GymDivider
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.RoutineOrigin
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
import kotlin.math.abs

/**
 * Progress, as a destination of its own (ADR-0024, US-06, US-33).
 *
 * Reads through its own [HistoryViewModel] rather than `ActiveSessionViewModel` — the split
 * ADR-0017 asked for, once history stopped being a flag on the session screen and became a
 * place you navigate to.
 *
 * Nothing is read until this route is actually entered, the same property the flag-based
 * screen had: history is a side trip, and the core loop should not pay for it.
 */
@Composable
fun HistoryRoute(
    onOpenWorkout: (SessionId) -> Unit,
    modifier: Modifier = Modifier,
    onSeeWeeklyVolume: () -> Unit = {},
    onSeeExerciseProgress: (ExerciseId) -> Unit = {},
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.open() }

    HistoryScreen(
        state =
            HistoryState(
                sessions = state.sessions,
                canUndo = state.canUndo,
                topLift = state.topLift,
                sessionsWithRecords = state.sessionsWithRecords,
            ),
        unit = state.unit,
        onDelete = viewModel::delete,
        onUndo = viewModel::undo,
        onOpenWorkout = onOpenWorkout,
        onSeeWeeklyVolume = onSeeWeeklyVolume,
        onSeeExerciseProgress = onSeeExerciseProgress,
        modifier = modifier,
    )
}

/**
 * Progress (US-33): a reason to open past workouts beyond "what did I do" — the same list
 * US-06 already built, headed by a top section answering "am I getting stronger."
 *
 * Only finished workouts are in the list below. The session in progress is on the other
 * screen, which is what makes it impossible to delete the one you are standing in the middle
 * of.
 *
 * There is no "Done" here (finding 06 of the redesign audit, ADR-0024): the bottom bar and the
 * system back gesture are the way out, like every other Android screen.
 */
@Composable
internal fun HistoryScreen(
    state: HistoryState,
    unit: WeightUnit,
    onDelete: (SessionId) -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenWorkout: (SessionId) -> Unit = {},
    onSeeWeeklyVolume: () -> Unit = {},
    onSeeExerciseProgress: (ExerciseId) -> Unit = {},
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = GymDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(GymDimens.Gap),
        ) {
            Text(
                text = "Progress",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = GymDimens.ScreenPadding),
            )

            TopSection(
                topLift = state.topLift,
                unit = unit,
                onSeeExerciseProgress = onSeeExerciseProgress,
                onSeeWeeklyVolume = onSeeWeeklyVolume,
            )

            Text("Past workouts", style = MaterialTheme.typography.titleMedium)

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
                    sessionsWithRecords = state.sessionsWithRecords,
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
        }
    }
}

/**
 * US-33's top section: one lift's estimated 1RM, chosen without asking (see
 * [MostRecentlyTrainedExercise][com.gymtracker.core.domain.progress.MostRecentlyTrainedExercise]),
 * and the way to US-17's chart — a labelled row now, not the bare [TextButton] this screen
 * carried before (redesign audit section 5).
 */
@Composable
private fun TopSection(
    topLift: TopLift,
    unit: WeightUnit,
    onSeeExerciseProgress: (ExerciseId) -> Unit,
    onSeeWeeklyVolume: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GymDimens.Gap)) {
        when (topLift) {
            TopLift.None ->
                // US-19: nothing performed recently enough to feature, said plainly.
                Text(
                    text = "Finish a few workouts and your progress shows up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            is TopLift.Lift ->
                TopLiftCard(
                    lift = topLift,
                    unit = unit,
                    onClick = { onSeeExerciseProgress(topLift.exerciseId) },
                )
        }

        WeeklyVolumeRow(onClick = onSeeWeeklyVolume)
    }
}

/** "Machine Bench Press · est. 1RM", the number, and how it has moved (US-16, US-33). */
@Composable
private fun TopLiftCard(
    lift: TopLift.Lift,
    unit: WeightUnit,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = GymDimens.MinTouchTarget),
    ) {
        Column(
            modifier = Modifier.padding(GymDimens.Gap),
            verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
        ) {
            Text("${lift.exerciseName} · est. 1RM", style = MaterialTheme.typography.titleSmall)

            val weight = WeightFormatter.format(lift.estimatedOneRepMaxKg, unit)
            Row(
                horizontalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(weight.primary, style = MaterialTheme.typography.titleLarge)
                weight.secondary?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            lift.deltaKg?.let { delta ->
                Text(
                    text = delta.asChangeOver8Weeks(unit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A row, not a bare link (redesign audit section 5: "not floating red text"). `ListItem` isn't
 * used here — this row carries no secondary text, and a plain clickable `Surface` avoids the
 * empty `supportingContent` slot `ListItem` would otherwise reserve.
 */
@Composable
private fun WeeklyVolumeRow(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = GymDimens.MinTouchTarget),
    ) {
        Text(
            text = "Weekly volume by muscle",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(GymDimens.Gap),
        )
    }
}

/** "↑ 7 lb in 8 weeks", or "↓" when the estimate has come down since. */
private fun Double.asChangeOver8Weeks(unit: WeightUnit): String {
    val arrow = if (this >= 0) "↑" else "↓"
    val magnitude = WeightFormatter.formatVolume(abs(this), unit).orEmpty()
    return "$arrow $magnitude in 8 weeks"
}

/**
 * One row per finished workout, newest first (US-06a, US-38).
 *
 * A ruled `Row`, not a `ListItem` (redesign audit section 5 / ADR-0029's ruled-sheet precedent):
 * the old three-line `ListItem` gave the routine name, the date and the summary metrics equal
 * visual weight, which is what made "3m · 34 sets" and "47m · 1 set" both read as unremarkable.
 * The name and date now share one line; the metrics move to a second, smaller and muted line —
 * one hierarchy, not four equal numbers.
 */
@Composable
private fun WorkoutList(
    sessions: List<SessionSummary>,
    unit: WeightUnit,
    sessionsWithRecords: Set<SessionId>,
    onDelete: (SessionId) -> Unit,
    onOpen: (SessionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(sessions, key = { it.session.id.value }) { summary ->
            WorkoutRow(
                summary = summary,
                unit = unit,
                hasRecord = summary.session.id in sessionsWithRecords,
                onOpen = { onOpen(summary.session.id) },
                onDelete = { onDelete(summary.session.id) },
            )
            GymDivider()
        }
    }
}

@Composable
private fun WorkoutRow(
    summary: SessionSummary,
    unit: WeightUnit,
    hasRecord: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        // Tapping the row opens what it contained (US-06b); "Delete" is a button of its own,
        // so opening cannot be mistaken for deleting.
        modifier =
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = GymDimens.MinListRowHeight)
                .clickable(onClick = onOpen)
                .padding(vertical = GymDimens.TightGap),
        horizontalArrangement = Arrangement.spacedBy(GymDimens.Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GymDimens.HairGap)) {
            Text(
                text =
                    buildAnnotatedString {
                        // US-32 (ADR-0028): the routine this session was started from leads the
                        // row, falling back to "Freestyle" for an ordinary "Start workout".
                        // Never resolved through routine_id — this reads the name copied onto
                        // the session at start, so a rename or delete afterward cannot change
                        // it. One text node with two weights, not two rows — line one is the
                        // whole point of this redesign: the name is what you remember it by.
                        append(summary.session.routine?.name ?: "Freestyle")
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            append(" · ${summary.session.startedAt.asRowDate()}")
                        }
                    },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = summary.describe(unit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (hasRecord) {
            PrBadge()
        }

        // ADR-0019: a destructive control is outlined, never filled — this was a filled-looking
        // TextButton until it was fixed here. The row's own tap target opens the workout, which
        // is a navigation, not a save, so this does not "share a surface with a save" in the
        // letter of that rule; it is outlined regardless, to read as destructive the same way
        // SetEditSheet's and the routine editor's delete controls do. Kept on the row rather
        // than moved (unlike Routines' delete): moving it to WorkoutDetailScreen would lose
        // US-06a's five-second undo, since that window lives in this screen's HistoryViewModel
        // instance and would not survive a navigation away from it.
        OutlinedButton(
            onClick = onDelete,
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
        ) {
            Text("Delete")
        }
    }
}

/**
 * The `PR` badge (US-38): outlined, never filled — the accent is spent on the log button
 * elsewhere in the app (ADR-0029's "exactly one filled accent element" rule), and a badge is
 * emphasis, not the screen's one action. [GymDimens.DividerThickness] rather than a new border
 * token — the same 2px weight every rule in this system draws with.
 */
@Composable
private fun PrBadge() {
    Box(
        modifier =
            Modifier
                .border(BorderStroke(GymDimens.DividerThickness, MaterialTheme.colorScheme.primary))
                .padding(horizontal = GymDimens.TightGap, vertical = GymDimens.HairGap),
    ) {
        Text(
            text = "PR",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.08.em),
            color = MaterialTheme.colorScheme.primary,
        )
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
            modifier = Modifier.padding(horizontal = GymDimens.Gap),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Workout deleted", style = MaterialTheme.typography.bodyMedium)
            TextButton(
                onClick = onUndo,
                modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
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

/** "Tue 4 Aug" — no time. Matches `RestPanel`'s own comparison-line date, not a new convention. */
private fun Instant.asRowDate(): String = ROW_DATE.format(atZone(ZoneId.systemDefault()))

private val ROW_DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

private const val MINUTES_PER_HOUR = 60L

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
                                        id = SessionId("preview-routine"),
                                        userId = UserId("preview"),
                                        gymName = null,
                                        startedAt = started,
                                        endedAt = started.plus(Duration.ofMinutes(72)),
                                        metrics = null,
                                        routine = RoutineOrigin(id = "r1", name = "Upper A"),
                                    ),
                                exerciseCount = 5,
                                setCount = 18,
                                volumeKg = 4120.0,
                                bodyweightSetCount = 3,
                            ),
                            SessionSummary(
                                session =
                                    WorkoutSession(
                                        id = SessionId("preview-freestyle"),
                                        userId = UserId("preview"),
                                        gymName = null,
                                        startedAt = started.minus(Duration.ofDays(1)),
                                        endedAt = started.minus(Duration.ofDays(1)).plus(Duration.ofMinutes(40)),
                                        metrics = null,
                                    ),
                                exerciseCount = 3,
                                setCount = 9,
                                volumeKg = 1980.0,
                                bodyweightSetCount = 0,
                            ),
                        ),
                    canUndo = true,
                    topLift =
                        TopLift.Lift(
                            exerciseId = ExerciseId("bench"),
                            exerciseName = "Machine Bench Press",
                            estimatedOneRepMaxKg = 56.2,
                            deltaKg = 3.2,
                        ),
                    sessionsWithRecords = setOf(SessionId("preview-routine")),
                ),
            unit = WeightUnit.LB,
            onDelete = {},
            onUndo = {},
        )
    }
}

@Preview
@Composable
private fun HistoryEmptyTopSectionPreview() {
    GymTrackerTheme {
        HistoryScreen(
            state = HistoryState(isOpen = true, sessions = emptyList(), topLift = TopLift.None),
            unit = WeightUnit.LB,
            onDelete = {},
            onUndo = {},
        )
    }
}
