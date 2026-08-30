package com.gymtracker.feature.logging.session

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.gymtracker.core.designsystem.theme.GymPreviews
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import com.gymtracker.core.domain.model.Equipment
import com.gymtracker.core.domain.model.Exercise
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

private val previewAppearance = SessionExercise(SessionExerciseId("se"), previewSession.id, ExerciseId("bench"), 1)

// MagicNumber's ignoreAnnotated exemption (detekt.yml) covers literals inside a @Composable or
// @Preview function; this fixture moved out to a top-level val so RestingPreview and
// RestingFinalTenPreview can share it, which loses that exemption even though the literals are
// exactly as illustrative here as they were inline.
@Suppress("MagicNumber")
private val previewLoggedSets =
    listOf(
        ExerciseSet("1", previewAppearance.id, 1, 61.23, 8, null, previewSession.startedAt),
        ExerciseSet("2", previewAppearance.id, 2, 61.23, 8, null, previewSession.startedAt),
    )

/**
 * The screen as it actually looks mid-workout: sets logged, and a rest counting down — the
 * calm state (ADR-0036), ink rather than accent-filled. `restTotal` (90s) is deliberately
 * longer than `restRemaining` (75s): a preview where the countdown exceeds its own total would
 * draw a nonsensical, clamped-to-empty progress bar.
 */
@GymPreviews
@Composable
private fun RestingPreview() {
    GymTrackerTheme {
        LoggingScreen(
            state =
                SessionUiState(
                    isLoading = false,
                    activeSession = previewSession,
                    unit = WeightUnit.LB,
                    restRemaining = Duration.ofSeconds(75),
                    restTotal = Duration.ofSeconds(90),
                    exercises =
                        listOf(
                            SessionExerciseRow(previewAppearance, exercise = null, sets = previewLoggedSets),
                        ),
                ),
            onStartWorkout = {},
            onResolveStale = {},
        )
    }
}

/**
 * The final ten seconds of a rest (ADR-0036): the countdown block takes the accent fill, and
 * the log button beneath it steps back to outlined — this is the frame where a future edit
 * that lets both go filled at once would look wrong first.
 */
@GymPreviews
@Composable
private fun RestingFinalTenPreview() {
    GymTrackerTheme {
        LoggingScreen(
            state =
                SessionUiState(
                    isLoading = false,
                    activeSession = previewSession,
                    unit = WeightUnit.LB,
                    restRemaining = Duration.ofSeconds(7),
                    restTotal = Duration.ofSeconds(90),
                    exercises =
                        listOf(
                            SessionExerciseRow(previewAppearance, exercise = null, sets = previewLoggedSets),
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

/**
 * ADR-0045 / `00-gate.md` section 5: the two device configurations every changed composable in
 * this pass carries beside its existing previews. The longest bundled exercise name stress-tests
 * the `THEN` row's truncation at the narrowest of the two.
 */
@Preview(name = "narrow, worst case", widthDp = 320, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "393dp", widthDp = 393, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WarmingUpNextExerciseWorstCasePreview() {
    GymTrackerTheme {
        LoggingScreen(
            state =
                SessionUiState(
                    isLoading = false,
                    activeSession = previewSession,
                    exercises =
                        listOf(
                            SessionExerciseRow(
                                previewAppearance,
                                exercise =
                                    Exercise(
                                        id = ExerciseId("preview-longest"),
                                        name = "Barbell Incline Bench Press - Medium Grip",
                                        aliases = emptyList(),
                                        primaryMuscles = emptyList(),
                                        secondaryMuscles = emptyList(),
                                        equipment = Equipment.BARBELL,
                                        instructions = emptyList(),
                                        mediaUrl = null,
                                        mediaType = null,
                                        youtubeUrl = null,
                                        source = "preview",
                                    ),
                            ),
                        ),
                ),
            onStartWorkout = {},
            onResolveStale = {},
            warmUp = WarmUp(elapsed = Duration.ofSeconds(252)),
        )
    }
}
