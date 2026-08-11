package com.gymtracker.feature.logging.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.gymtracker.core.designsystem.component.PrimaryActionButton
import com.gymtracker.core.designsystem.component.SecondaryActionButton
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.rest.UpNextSet
import com.gymtracker.core.domain.units.WeightUnit
import com.gymtracker.feature.logging.FinishFlow
import com.gymtracker.feature.logging.FinishSummaryScreen
import com.gymtracker.feature.logging.SessionExerciseRow
import com.gymtracker.feature.logging.SessionUiState
import com.gymtracker.feature.logging.WarmUp
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
    warmUp: WarmUp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(GymDimens.ScreenPadding),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.finish is FinishFlow.Ready ->
                FinishSummaryScreen(
                    detail = state.finish.detail,
                    records = state.finish.records,
                    unit = state.unit,
                    onDone = onFinishSummaryDismissed,
                )
            state.finish != null -> CircularProgressIndicator()
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
                    onEditSet = onEditSet,
                    canUndoSetDelete = state.canUndoSetDelete,
                    onUndoSetDelete = onUndoSetDelete,
                    upNext = state.upNext,
                    onLogNextSet = onLogNextSet,
                    onSkipRest = onSkipRest,
                    onFinishWorkout = onFinishWorkout,
                    warmUp = warmUp,
                )
            else -> NoSession(onStartWorkout, onOpenHistory, onBrowseCatalog)
        }
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
                onRemoveExercise = onRemoveExercise,
                onStartExercise = onStartExercise,
                onEditSet = onEditSet,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }

        // The warm-up sits above the undo bars and the rest, in the same never-over-the-list
        // slot. It is drawn on the session screen but is no part of the session (ADR-0021):
        // it logs nothing, so there is nothing here that a set could be confused with.
        WarmUpPanel(warmUp)

        // Below the list and above the primary action, same slot the rest banner uses: neither
        // may cover the row you were about to read or the button you were about to press
        // (US-02c, US-05).
        if (canUndoRemoval) {
            RemovalUndoBar(onUndoRemoval)
        }

        if (canUndoSetDelete) {
            SetDeleteUndoBar(onUndoSetDelete)
        }

        // Above the bottom action and never over the list: the rest banner displays the stored
        // end time (ADR-0010) and gates nothing, which is US-05's "it never blocks logging the
        // next set". Every "Add set" above it stays live while it counts down.
        restRemaining?.let { remaining ->
            val row = exercises.firstOrNull { it.sessionExercise.id == upNext?.sessionExerciseId }
            RestBanner(
                remaining = remaining,
                upNext = upNext,
                exerciseName = row?.exercise?.name,
                unit = unit,
                onSkipRest = onSkipRest,
                onLogNext = { upNext?.let(onLogNextSet) },
                onAdjust = { row?.let(onAddSet) },
            )
        }

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
 * "HH:mm", shared by [SessionHeader] and the abandoned-session dialog's explanation
 * ([StaleSessionPrompt.explanation]) — `internal` rather than file-private because both live in
 * different files after the split.
 */
internal fun Instant.asLocalTime(): String = TIME_FORMAT.format(atZone(ZoneId.systemDefault()))

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
