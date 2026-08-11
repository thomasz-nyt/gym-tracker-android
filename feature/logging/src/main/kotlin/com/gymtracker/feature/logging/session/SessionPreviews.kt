package com.gymtracker.feature.logging.session

import androidx.compose.runtime.Composable
import com.gymtracker.core.designsystem.theme.GymPreviews
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.units.WeightUnit
import com.gymtracker.feature.logging.LoggingScreen
import com.gymtracker.feature.logging.SessionExerciseRow
import com.gymtracker.feature.logging.SessionUiState
import com.gymtracker.feature.logging.WarmUp
import java.time.Duration
import java.time.Instant

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

/**
 * The eight minutes on the treadmill, counting up (US-28).
 *
 * Worth a preview of its own because of what it has to *not* show: no weight, no reps, no
 * exercise, and no save. If a future edit puts any of those here, this preview is where it
 * will look wrong first.
 */
@GymPreviews
@Composable
private fun WarmingUpPreview() {
    GymTrackerTheme {
        LoggingScreen(
            state = SessionUiState(isLoading = false, activeSession = previewSession),
            onStartWorkout = {},
            onResolveStale = {},
            warmUp = WarmUp(elapsed = Duration.ofSeconds(252)),
        )
    }
}
