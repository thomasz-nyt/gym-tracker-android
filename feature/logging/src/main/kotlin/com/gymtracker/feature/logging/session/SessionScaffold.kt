package com.gymtracker.feature.logging.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.gymtracker.core.designsystem.component.PrimaryActionButton
import com.gymtracker.core.designsystem.component.SecondaryActionButton
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.progress.PersonalRecord
import com.gymtracker.core.domain.rest.UpNextSet
import com.gymtracker.core.domain.session.SessionProgress
import com.gymtracker.core.domain.units.WeightUnit
import com.gymtracker.feature.logging.FinishFlow
import com.gymtracker.feature.logging.FinishSummaryScreen
import com.gymtracker.feature.logging.SessionExerciseRow
import com.gymtracker.feature.logging.SessionUiState
import com.gymtracker.feature.logging.WarmUp
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Which of the four session states is on screen. Derived from the database rather than from
 * a back stack, which is what makes "reopen and you are back in your session" survive a kill.
 *
 * [SessionUiState.finish] is checked first, above even [SessionUiState.isLoading]: once
 * "Finish workout" is confirmed, [ActiveSessionViewModel.onFinishWorkout] sets it before ending
 * the session, specifically so this branch wins the moment `activeSession` goes null — the
 * alternative is a frame of the empty "start a workout" screen before the summary appears
 * (US-31).
 */
@Composable
internal fun SessionBody(
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
    onFinishSummaryDismissed: () -> Unit,
    nextRoutine: Routine?,
    onStartFromRoutine: (RoutineId) -> Unit,
    onOpenRoutines: () -> Unit,
    warmUp: WarmUp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.finish is FinishFlow.Ready ->
                Box(modifier = Modifier.padding(GymDimens.ScreenPadding)) {
                    FinishSummaryScreen(
                        detail = state.finish.detail,
                        records = state.finish.records,
                        unit = state.unit,
                        onDone = onFinishSummaryDismissed,
                    )
                }
            state.finish != null -> CenteredSpinner()
            state.isLoading -> CenteredSpinner()
            state.activeSession != null ->
                ActiveSession(
                    session = state.activeSession,
                    exercises = state.exercises,
                    unit = state.unit,
                    restRemaining = state.restRemaining,
                    progress = state.progress,
                    openSessionExerciseId = state.openSessionExerciseId,
                    nextLoggableSet = state.nextLoggableSet,
                    justSetRecord = state.justSetRecord,
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
            else ->
                Box(modifier = Modifier.padding(GymDimens.ScreenPadding)) {
                    NoSession(
                        onStartWorkout = onStartWorkout,
                        onOpenHistory = onOpenHistory,
                        onBrowseCatalog = onBrowseCatalog,
                        nextRoutine = nextRoutine,
                        onStartFromRoutine = onStartFromRoutine,
                        onOpenRoutines = onOpenRoutines,
                    )
                }
        }
    }
}

@Composable
private fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Train home with no workout running (US-36, ADR-0030).
 *
 * `Routines` is reached only from here — one outlined button, top-right, on every state this
 * composable can be in, including the routine-less one below. Below it, the screen says which
 * routine is due next when it can honestly say one (the one gone longest without being done,
 * or never done at all) and offers `Start <name>` beside the unconditional `Freestyle` action;
 * with no routines at all it falls back to exactly what this screen said before this story,
 * unchanged word for word so `TabNavigationTest`'s `"Start workout"` signal keeps meaning what
 * it always has.
 */
@Composable
private fun NoSession(
    onStartWorkout: () -> Unit,
    onOpenHistory: () -> Unit,
    onBrowseCatalog: () -> Unit,
    nextRoutine: Routine?,
    onStartFromRoutine: (RoutineId) -> Unit,
    onOpenRoutines: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(
                onClick = onOpenRoutines,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
            ) {
                Text("Routines")
            }
        }

        // Bottom-weighted rather than centred (ADR-0016): starting a workout is done one-handed,
        // standing, often with the other hand holding something, so the button that matters is
        // the one nearest the thumb.
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = nextRoutine?.let { "${it.name} is next up" } ?: "No workout in progress",
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

                if (nextRoutine == null) {
                    PrimaryActionButton(text = "Start workout", onClick = onStartWorkout)
                } else {
                    SecondaryActionButton(text = "Freestyle", onClick = onStartWorkout)
                    PrimaryActionButton(
                        text = "Start ${nextRoutine.name}",
                        onClick = { onStartFromRoutine(nextRoutine.id) },
                    )
                }
            }
        }
    }
}

/**
 * The session screen, rebuilt as a ruled sheet (ADR-0029): a header carrying the routine's
 * provenance and progress, a full-bleed structural rule, then either the plan (mid-set) or the
 * rest banner (resting) — never both, since the two states are mutually exclusive on this
 * screen exactly as they were before the redesign.
 */
@Composable
private fun ActiveSession(
    session: WorkoutSession,
    exercises: List<SessionExerciseRow>,
    unit: WeightUnit,
    restRemaining: Duration?,
    progress: SessionProgress?,
    openSessionExerciseId: SessionExerciseId?,
    nextLoggableSet: UpNextSet?,
    justSetRecord: PersonalRecord?,
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

    Column(modifier = Modifier.fillMaxSize()) {
        SessionTopBar(session = session, progress = progress, onFinishWorkout = { confirmingFinish = true })

        WarmUpPanel(warmUp)

        if (canUndoRemoval) RemovalUndoBar(onUndoRemoval)
        if (canUndoSetDelete) SetDeleteUndoBar(onUndoSetDelete)

        if (restRemaining != null) {
            RestingBody(
                remaining = restRemaining,
                upNext = upNext,
                exerciseName =
                    exercises
                        .firstOrNull { it.sessionExercise.id == upNext?.sessionExerciseId }
                        ?.exercise
                        ?.name,
                progress = progress,
                exercises = exercises,
                unit = unit,
                justSetRecord = justSetRecord,
                onSkipRest = onSkipRest,
                onLogNext = { upNext?.let(onLogNextSet) },
                onAdjust = {
                    exercises.firstOrNull { it.sessionExercise.id == upNext?.sessionExerciseId }?.let(onAddSet)
                },
                modifier = Modifier.weight(1f),
            )
        } else {
            MidSetBody(
                exercises = exercises,
                openSessionExerciseId = openSessionExerciseId,
                unit = unit,
                nextLoggableSet = nextLoggableSet,
                onAddSet = onAddSet,
                onRemoveExercise = onRemoveExercise,
                onStartExercise = onStartExercise,
                onEditSet = onEditSet,
                onLogNextSet = onLogNextSet,
                modifier = Modifier.weight(1f),
            )
        }

        AddExerciseButton(onClick = onAddExercise)
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
 * The header, the segment bar (only for a session started from a routine — ADR-0029), and the
 * structural rule under both. Split out of [ActiveSession] to keep that function short, not
 * because this is its own concept.
 */
@Composable
private fun SessionTopBar(
    session: WorkoutSession,
    progress: SessionProgress?,
    onFinishWorkout: () -> Unit,
) {
    SessionHeader(session = session, progress = progress, onFinishWorkout = onFinishWorkout)

    if (progress?.orderIsAPlan == true) {
        SegmentBar(progress = progress, modifier = Modifier.padding(horizontal = GymDimens.ScreenPadding))
    }

    // The structural rule (ADR-0029): solid ink, the heavier of the two rule weights this
    // screen draws with. Full-bleed, unlike the row rules inside the plan below.
    HorizontalDivider(thickness = GymDimens.StructuralRuleThickness, color = MaterialTheme.colorScheme.onSurface)
}

/**
 * Not resting: the plan (or, before any exercise has been added, the empty state) plus the
 * bottom log bar. Split out of [ActiveSession] to keep that function short, not because this is
 * its own concept — it is still just "what the screen shows when not resting."
 */
@Composable
private fun MidSetBody(
    exercises: List<SessionExerciseRow>,
    openSessionExerciseId: SessionExerciseId?,
    unit: WeightUnit,
    nextLoggableSet: UpNextSet?,
    onAddSet: (SessionExerciseRow) -> Unit,
    onRemoveExercise: (SessionExerciseId) -> Unit,
    onStartExercise: (SessionExerciseRow) -> Unit,
    onEditSet: (SessionExerciseRow, ExerciseSet) -> Unit,
    onLogNextSet: (UpNextSet) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (exercises.isEmpty()) {
            Text(
                text = "No exercises yet.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .padding(GymDimens.ScreenPadding),
            )
        } else {
            SessionPlan(
                exercises = exercises,
                openSessionExerciseId = openSessionExerciseId,
                nextLoggableSet = nextLoggableSet,
                unit = unit,
                onAddSet = onAddSet,
                onRemoveExercise = onRemoveExercise,
                onStartExercise = onStartExercise,
                onEditSet = onEditSet,
                onLogNextSet = onLogNextSet,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = GymDimens.ScreenPadding),
            )
        }
    }
}

/**
 * Not in the design's frames (a routine already sets up the plan; "Add exercise" is for the
 * freestyle case, or adding an extra movement mid-workout) — kept as a quiet outlined control
 * rather than the screen's one filled action, which the log button is now (ADR-0029).
 */
@Composable
private fun AddExerciseButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        modifier =
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = GymDimens.MinTouchTarget)
                .padding(horizontal = GymDimens.ScreenPadding, vertical = GymDimens.TightGap),
    ) {
        Text("Add exercise")
    }
}

/**
 * The session's title bar: routine, progress, and the one way out.
 *
 * "Finish workout" lives up here, away from the thumb (ADR-0016). It is the rarest action on
 * the screen and the only one with no undo — nothing in US-06 reopens a finished session — so
 * it is deliberately the hardest thing here to hit by accident.
 *
 * Elapsed time is computed here, in Compose, from [WorkoutSession.startedAt] against the wall
 * clock — not read off [SessionUiState][com.gymtracker.feature.logging.SessionUiState] the way
 * [SessionProgress] is. A ticking value threaded through the ViewModel's `combine` chain adds a
 * `uiState` emission every second purely from display refresh, which several
 * `ActiveSessionViewModelTest` cases caught immediately: they assert an exact `awaitItem()`
 * sequence, and a tick landing between two asserted items reads as an unconsumed event. Display
 * ticking belongs at the display layer; nothing about *what the app did* depends on it.
 */
@Composable
private fun SessionHeader(
    session: WorkoutSession,
    progress: SessionProgress?,
    onFinishWorkout: () -> Unit,
) {
    val elapsed = rememberElapsed(session.startedAt)

    Row(
        modifier = Modifier.fillMaxWidth().padding(GymDimens.ScreenPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                // Uppercased for display only (ADR-0029) — the session's own routine.name is
                // never mutated, matching History and the finish summary's typed-case rendering.
                text = (session.routine?.name ?: "Freestyle").uppercase(),
                style = MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier.semantics {
                        contentDescription = "Session for ${session.routine?.name ?: "Freestyle"}"
                    },
            )
            Text(
                text = headerMeta(elapsed, progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(
            onClick = onFinishWorkout,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
        ) {
            Text("FINISH")
        }
    }
}

/**
 * Ticks once a second from [startedAt] to now, restarting cleanly if the session itself changes
 * (a different `startedAt` — e.g. resuming a different session) rather than drifting.
 */
@Composable
private fun rememberElapsed(startedAt: Instant): Duration {
    var elapsed by remember(startedAt) { mutableStateOf(Duration.between(startedAt, Instant.now())) }
    LaunchedEffect(startedAt) {
        while (true) {
            delay(ELAPSED_TICK_MILLIS)
            elapsed = Duration.between(startedAt, Instant.now())
        }
    }
    return elapsed
}

/** "24 min · 2 of 6 done", or just the elapsed time before movements are known. */
private fun headerMeta(
    elapsed: Duration,
    progress: SessionProgress?,
): String =
    buildString {
        append(elapsed.asElapsedMinutes())
        if (progress != null && progress.movementsTotal > 0) {
            append("  ·  ${progress.movementsDone} of ${progress.movementsTotal} done")
        }
    }

private fun Duration.asElapsedMinutes(): String {
    val minutes = (seconds / SECONDS_PER_MINUTE).coerceAtLeast(0)
    return "$minutes min"
}

private const val ELAPSED_TICK_MILLIS = 1_000L

private const val SECONDS_PER_MINUTE = 60L

/**
 * One 6dp bar per movement, three states (ADR-0029): done (solid accent), current (accent at
 * 55% alpha — the design's third red, `#EC3013`, would reopen ADR-0019's one-accent system for
 * a single decorative detail, so this reads the same accent at reduced opacity instead), and
 * upcoming (ink at 20%). Shown only for a session started from a routine
 * ([SessionProgress.orderIsAPlan]) — see [RestingBody]'s "then X" clause for the other half of
 * that same rule.
 */
@Composable
private fun SegmentBar(
    progress: SessionProgress,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = GymDimens.TightGap),
        horizontalArrangement = Arrangement.spacedBy(GymDimens.SegmentGap),
    ) {
        repeat(progress.movementsTotal) { index ->
            val color =
                when {
                    index < progress.movementsDone -> MaterialTheme.colorScheme.primary
                    index == progress.movementsDone ->
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = CURRENT_SEGMENT_ALPHA,
                        )
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = UPCOMING_SEGMENT_ALPHA)
                }
            SegmentBarSegment(color = color, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SegmentBarSegment(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(GymDimens.SegmentHeight)
                .background(color),
    )
}

private const val CURRENT_SEGMENT_ALPHA = 0.55f
private const val UPCOMING_SEGMENT_ALPHA = 0.2f

/**
 * "HH:mm", shared by the abandoned-session dialog's explanation
 * ([StaleSessionPrompt.explanation]) — `internal` rather than file-private because both live in
 * different files after the split.
 */
internal fun Instant.asLocalTime(): String = TIME_FORMAT.format(atZone(ZoneId.systemDefault()))

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
