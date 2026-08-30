package com.gymtracker.feature.logging.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import com.gymtracker.core.designsystem.component.GymText
import com.gymtracker.core.designsystem.component.PrimaryActionButton
import com.gymtracker.core.designsystem.component.RepMascot
import com.gymtracker.core.designsystem.component.SecondaryActionButton
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymTextRoles
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
    onOpenSettings: () -> Unit,
    onSelectExercise: (SessionExerciseId) -> Unit,
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
                    restTotal = state.restTotal,
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
                    onSelectExercise = onSelectExercise,
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
                        onOpenSettings = onOpenSettings,
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
 * composable can be in, including the routine-less one below. `Settings` (US-40 … US-42, M3c)
 * joins it the same way, reached only from this row. Below them, the screen says which routine
 * is due next when it can honestly say one (the one gone longest without being done, or never
 * done at all) and offers `Start <name>` beside the unconditional `Freestyle` action; with no
 * routines at all it falls back to exactly what this screen said before this story, unchanged
 * word for word so `TabNavigationTest`'s `"Start workout"` signal keeps meaning what it always
 * has.
 *
 * US-43 / ADR-0035: `RepMascot` plays above the "next up" text, inside the same weighted band —
 * this is the one place in the app that empty band existed for. It carries no semantics node,
 * so `TabNavigationTest`'s signals are unaffected by its presence.
 */
@Composable
private fun NoSession(
    onStartWorkout: () -> Unit,
    onOpenHistory: () -> Unit,
    onBrowseCatalog: () -> Unit,
    nextRoutine: Routine?,
    onStartFromRoutine: (RoutineId) -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GymDimens.TightGap, Alignment.End),
        ) {
            OutlinedButton(
                onClick = onOpenSettings,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
            ) {
                Text("Settings")
            }
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
            Column(
                modifier = Modifier.weight(1f).wrapContentHeight(Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RepMascot(modifier = Modifier.size(GymDimens.MascotHome))
                GymText(
                    text = nextRoutine?.let { "${it.name} is next up" } ?: "No workout in progress",
                    role = GymTextRoles.TitleLg,
                    textAlign = TextAlign.Center,
                )
            }

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
    restTotal: Duration?,
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
    onSelectExercise: (SessionExerciseId) -> Unit,
    warmUp: WarmUp,
) {
    // ADR-0045: the running warm-up is a full-screen step, not content drawn alongside the
    // session — this branch is the entire reason "no state where both are visible" holds.
    if (warmUp.elapsed != null) {
        WarmUpStep(warmUp = warmUp, nextExerciseName = exercises.firstOrNull()?.exercise?.name)
        return
    }

    var confirmingFinish by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        SessionTopBar(session = session, progress = progress, onFinishWorkout = { confirmingFinish = true })

        WarmUpPanel(warmUp)

        if (canUndoRemoval) RemovalUndoBar(onUndoRemoval)
        if (canUndoSetDelete) SetDeleteUndoBar(onUndoSetDelete)

        if (restRemaining != null) {
            RestingBody(
                remaining = restRemaining,
                total = restTotal,
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
                orderIsAPlan = progress?.orderIsAPlan == true,
                onAddSet = onAddSet,
                onRemoveExercise = onRemoveExercise,
                onStartExercise = onStartExercise,
                onEditSet = onEditSet,
                onLogNextSet = onLogNextSet,
                onSelectExercise = onSelectExercise,
                modifier = Modifier.weight(1f),
            )
        }

        AddExerciseButton(onClick = onAddExercise)
    }

    FinishConfirmation(confirmingFinish, onFinishWorkout) { confirmingFinish = it }
}

/** Split out of [ActiveSession] to keep that function under detekt's length ceiling. */
@Composable
private fun FinishConfirmation(
    confirming: Boolean,
    onFinishWorkout: () -> Unit,
    setConfirming: (Boolean) -> Unit,
) {
    if (confirming) {
        FinishWorkoutDialog(
            onConfirm = {
                setConfirming(false)
                onFinishWorkout()
            },
            onDismiss = { setConfirming(false) },
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
        SegmentBar(
            total = progress.movementsTotal,
            done = progress.movementsDone,
            modifier = Modifier.padding(horizontal = GymDimens.ScreenPadding),
        )
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
    orderIsAPlan: Boolean,
    onAddSet: (SessionExerciseRow) -> Unit,
    onRemoveExercise: (SessionExerciseId) -> Unit,
    onStartExercise: (SessionExerciseRow) -> Unit,
    onEditSet: (SessionExerciseRow, ExerciseSet) -> Unit,
    onLogNextSet: (UpNextSet) -> Unit,
    onSelectExercise: (SessionExerciseId) -> Unit,
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
                orderIsAPlan = orderIsAPlan,
                onAddSet = onAddSet,
                onRemoveExercise = onRemoveExercise,
                onStartExercise = onStartExercise,
                onEditSet = onEditSet,
                onLogNextSet = onLogNextSet,
                onSelectExercise = onSelectExercise,
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
        modifier = Modifier.fillMaxWidth().padding(GymDimens.CompactScreenPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GymText(
                    // ADR-0011's Turn 4 amendment (frame 4c): sentence case, not the uppercase
                    // ADR-0029 originally drew this in — title.lg carries its own weight (800) and
                    // tracking for hierarchy, so the string transform is no longer needed for it to
                    // read as a title. The session's own routine.name is still never mutated.
                    text = session.routine?.name ?: "Freestyle",
                    role = GymTextRoles.TitleLg,
                    modifier = Modifier.testTag(SESSION_TITLE_TEST_TAG),
                    semantics = { contentDescription = "Session for ${session.routine?.name ?: "Freestyle"}" },
                )
                ModeTag(orderIsAPlan = progress?.orderIsAPlan == true)
            }
            GymText(
                text = headerMeta(elapsed, progress),
                role = GymTextRoles.Meta,
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
 * US-54 / ADR-0046: `GUIDED` (accent outline) for a session backed by a routine, `NO PLAN`
 * (neutral outline) otherwise — the same [orderIsAPlan][com.gymtracker.core.domain.session
 * .SessionProgress.orderIsAPlan] signal [sessionKicker]'s callers combine with a per-exercise
 * target. Local to this file rather than promoted to the design system: nothing else in the app
 * draws this exact "2px-outline caption" shape yet, and a second real consumer is what should
 * decide its home, not a guess now.
 */
@Composable
private fun ModeTag(orderIsAPlan: Boolean) {
    val color = if (orderIsAPlan) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    GymText(
        text = if (orderIsAPlan) "GUIDED" else "NO PLAN",
        role = GymTextRoles.TagCaps,
        color = color,
        modifier =
            Modifier
                .border(BorderStroke(GymDimens.DividerThickness, color))
                .padding(horizontal = GymDimens.TightGap, vertical = GymDimens.HairGap),
    )
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

/**
 * "24 min · 2 of 6", or just the elapsed time before movements are known.
 *
 * "done" was cut by ADR-0011's Turn 4 amendment (frame 4c) — "n of m" reads as progress on its
 * own, and the word was the difference between fitting on one line and not.
 */
private fun headerMeta(
    elapsed: Duration,
    progress: SessionProgress?,
): String =
    buildString {
        append(elapsed.asElapsedMinutes())
        if (progress != null && progress.movementsTotal > 0) {
            append("  ·  ${progress.movementsDone} of ${progress.movementsTotal}")
        }
    }

private fun Duration.asElapsedMinutes(): String {
    val minutes = (seconds / SECONDS_PER_MINUTE).coerceAtLeast(0)
    return "$minutes min"
}

private const val ELAPSED_TICK_MILLIS = 1_000L

private const val SECONDS_PER_MINUTE = 60L

/**
 * One 6dp bar per [total], three states (ADR-0029): done (solid accent), current (accent at
 * 55% alpha — the design's third red, `#EC3013`, would reopen ADR-0019's one-accent system for
 * a single decorative detail, so this reads the same accent at reduced opacity instead), and
 * upcoming (ink at 20%). On the session header, shown only for a session started from a routine
 * ([SessionProgress.orderIsAPlan]) — see [RestingBody]'s "then X" clause for the other half of
 * that same rule.
 *
 * Takes a plain count rather than [SessionProgress] (ADR-0033): the guided screen's per-set
 * progress has no [SessionProgress] to read one out of, only a target and a done count, so this
 * reads the same two numbers either caller already has instead of asking one of them to wrap
 * theirs in a type built for the other.
 */
@Composable
internal fun SegmentBar(
    total: Int,
    done: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = GymDimens.TightGap),
        horizontalArrangement = Arrangement.spacedBy(GymDimens.SegmentGap),
    ) {
        repeat(total) { index ->
            val color =
                when {
                    index < done -> MaterialTheme.colorScheme.primary
                    index == done -> MaterialTheme.colorScheme.primary.copy(alpha = CURRENT_SEGMENT_ALPHA)
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

/** ADR-0044: lets an instrumented test find the session title without matching its dynamic text. */
private const val SESSION_TITLE_TEST_TAG = "session-title"

/**
 * "HH:mm", shared by the abandoned-session dialog's explanation
 * ([StaleSessionPrompt.explanation]) — `internal` rather than file-private because both live in
 * different files after the split.
 */
internal fun Instant.asLocalTime(): String = TIME_FORMAT.format(atZone(ZoneId.systemDefault()))

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
