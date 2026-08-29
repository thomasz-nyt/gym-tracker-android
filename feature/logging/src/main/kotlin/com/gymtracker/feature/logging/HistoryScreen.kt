package com.gymtracker.feature.logging

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.core.designsystem.R
import com.gymtracker.core.designsystem.component.GymDivider
import com.gymtracker.core.designsystem.component.GymText
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymTextRoles
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

/**
 * "Machine Bench Press · est. 1RM", the number, and how it has moved (US-16, US-33).
 *
 * ADR-0011's Turn 4 amendment (frame `4d`): the kicker over the name, not a dot-joined sentence
 * ("Ab Crunch Machine · est. 1RM" used to wrap on one line at 20sp). kg stays here — ADR-0008's
 * Turn 4 amendment keeps it on Progress and history rows specifically, this card being both.
 */
@Composable
private fun TopLiftCard(
    lift: TopLift.Lift,
    unit: WeightUnit,
    onClick: () -> Unit,
) {
    RuledBand(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap)) {
            GymText(text = "EST. 1RM", role = GymTextRoles.TagCaps, color = MaterialTheme.colorScheme.primary)
            GymText(text = lift.exerciseName, role = GymTextRoles.Body)

            val weight = WeightFormatter.format(lift.estimatedOneRepMaxKg, unit)
            val number = weight.number
            val displayUnit = weight.unit
            // Not GymLoadRow: that component always draws a trailing "× reps", and a 1RM has
            // no rep count to show — the load alone, drawn directly.
            Row(horizontalArrangement = Arrangement.spacedBy(GymDimens.HairGap), verticalAlignment = Alignment.Bottom) {
                if (number != null && displayUnit != null) {
                    GymText(text = number, role = GymTextRoles.NumeralLg)
                    GymText(text = displayUnit, role = GymTextRoles.WordUnit)
                } else {
                    GymText(text = weight.primary, role = GymTextRoles.WordUnit)
                }
            }
            weight.secondary?.let {
                GymText(text = it, role = GymTextRoles.Meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            lift.deltaKg?.let { delta ->
                GymText(
                    text = delta.asChangeOver8Weeks(unit),
                    role = GymTextRoles.Meta,
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
    RuledBand(onClick = onClick) {
        GymText(text = "Weekly volume by muscle", role = GymTextRoles.TitleMd)
    }
}

/**
 * A ruled band (ADR-0011's Turn 4 amendment, frame `4d`): 2px rules top and bottom on the bare
 * ground, replacing the `surfaceContainerLow` fill both cards above used. Once the caps labels
 * carry the hierarchy, a tinted box adds mud rather than structure — the same argument
 * `SessionScaffold`'s ruled sheet already made for the session screen (ADR-0029).
 */
@Composable
private fun RuledBand(
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        GymDivider()
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = GymDimens.MinTouchTarget),
        ) {
            Column(modifier = Modifier.padding(GymDimens.Gap), content = content)
        }
        GymDivider()
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
            // ADR-0011's Turn 4 amendment (frame 4d): two cells on one baseline rather than one
            // AnnotatedString — the name (which truncates) and the date (which does not) no
            // longer share a break point, so a long routine name can never push the date onto
            // its own line the way the welded string could.
            Row(
                horizontalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
                verticalAlignment = Alignment.Bottom,
            ) {
                // US-32 (ADR-0028): the routine this session was started from leads the row,
                // falling back to "Freestyle" for an ordinary "Start workout". Never resolved
                // through routine_id — this reads the name copied onto the session at start, so
                // a rename or delete afterward cannot change it.
                GymText(
                    text = summary.session.routine?.name ?: "Freestyle",
                    role = GymTextRoles.TitleMd,
                    modifier = Modifier.weight(1f, fill = false),
                )
                GymText(
                    text = summary.session.startedAt.asRowDate(),
                    role = GymTextRoles.Meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // A FlowRow of measurements, not a joined string (cause 3 of Turn 4's wrapping bug):
            // "5m · 2 exercises · 6 sets · 1,980 lb · 3 bodyweight" left an orphan "3 bodyweight"
            // tail on its own line, with a dangling separator on the line above it. No separator
            // at any width means adding a sixth measurement can never invent a new wrap.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(GymDimens.MetricFlowRowGapHorizontal),
                verticalArrangement = Arrangement.spacedBy(GymDimens.MetricFlowRowGapVertical),
            ) {
                summary.describe(unit).forEach { metric ->
                    GymText(
                        text = metric,
                        role = GymTextRoles.LabelCaps,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (hasRecord) {
            PrBadge()
        }

        // ADR-0011's Turn 4 amendment (frame 4d): a 44dp neutral icon button, not a 96dp accent
        // text button — the accent stays reserved for the one filled action elsewhere in the
        // app, and the row's own title keeps its column instead of narrowing against "Delete".
        // The frame's own 44dp is superseded by GymDimens.MinTouchTarget (48dp), the same
        // accessibility-floor trade WarmUpRowHeight's own doc already made. Still outlined-in-
        // spirit (ADR-0019: destructive is never filled) via the error-tinted icon alone, rather
        // than an OutlinedButton's border, which an icon-only control has no room to draw.
        IconButton(
            onClick = onDelete,
            modifier = Modifier.sizeIn(minWidth = GymDimens.MinTouchTarget, minHeight = GymDimens.MinTouchTarget),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = "Delete workout",
                tint = MaterialTheme.colorScheme.error,
            )
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
 * The measurements under the date/title row, as separate items rather than one joined sentence
 * (ADR-0011's Turn 4 amendment, frame `4d`) — a `FlowRow` at the call site draws them with no
 * separator at any width, so a sixth measurement can never invent a new wrap the way
 * "5m · 2 exercises · 6 sets · 1,980 lb · 3 bodyweight" used to.
 *
 * Bodyweight sets are named rather than folded into the volume, because their load was never
 * recorded and pretending it was zero would understate the workout (constitution §2).
 * "bodyweight" shortens to "bw" (frame `4d`) — a label.caps row is not the format prose reads in.
 */
private fun SessionSummary.describe(unit: WeightUnit): List<String> =
    buildList {
        duration?.let { add(it.asWorkoutLength()) }
        add("$exerciseCount ${"exercise".plural(exerciseCount)}")
        add("$setCount ${"set".plural(setCount)}")
        WeightFormatter.formatVolume(volumeKg, unit)?.let { add(it) }
        if (bodyweightSetCount > 0) {
            add("$bodyweightSetCount bw")
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

/**
 * ADR-0011's Turn 4 amendment: 320dp, 130% font scale, the longest exercise name and a
 * bodyweight-only session — frame `4d`'s two worst cases, on the `TopLiftCard` and the one
 * history row that carries every measurement `FlowRow` draws, including "bw".
 */
@Preview(widthDp = 320, fontScale = 1.3f)
@Composable
private fun HistoryNarrowWorstCasePreview() {
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
                                        id = SessionId("preview-worst-case"),
                                        userId = UserId("preview"),
                                        gymName = null,
                                        startedAt = started,
                                        endedAt = started.plus(Duration.ofMinutes(72)),
                                        metrics = null,
                                        routine = RoutineOrigin(id = "r1", name = "Barbell Incline Bench Press Day"),
                                    ),
                                exerciseCount = 5,
                                setCount = 18,
                                volumeKg = 4120.0,
                                bodyweightSetCount = 3,
                            ),
                        ),
                    canUndo = false,
                    topLift =
                        TopLift.Lift(
                            exerciseId = ExerciseId("preview-worst-case"),
                            exerciseName = "Barbell Incline Bench Press - Medium Grip",
                            estimatedOneRepMaxKg = 56.2,
                            deltaKg = 3.2,
                        ),
                    sessionsWithRecords = emptySet(),
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
