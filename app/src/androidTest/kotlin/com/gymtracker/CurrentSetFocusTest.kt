package com.gymtracker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import com.gymtracker.core.domain.model.BodyPart
import com.gymtracker.core.domain.model.Equipment
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.rest.UpNextSet
import com.gymtracker.core.domain.set.PreviousPerformance
import com.gymtracker.core.domain.set.SetPrefill
import com.gymtracker.core.domain.units.WeightUnit
import com.gymtracker.feature.logging.FocusActions
import com.gymtracker.feature.logging.FocusWorkoutScreen
import com.gymtracker.feature.logging.SessionExerciseRow
import com.gymtracker.feature.logging.SessionUiState
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** US-63: Focus is a sparse lens over the actual workout, not a separate guided plan. */
class CurrentSetFocusTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-10-29T14:00:00Z")
    private val session = WorkoutSession(SessionId("focus"), UserId("alice"), null, now, null, null)
    private val movement =
        SessionExercise(SessionExerciseId("bench"), session.id, ExerciseId("bench"), 1, MovementTarget(3, 12, 50.0))
    private val actual =
        listOf(12, 10).mapIndexed {
            i,
            reps,
            ->
            ExerciseSet("set-$i", movement.id, i + 1, 50.0, reps, null, now)
        }
    private val state =
        SessionUiState(
            isLoading = false,
            activeSession = session,
            exercises =
                listOf(
                    SessionExerciseRow(
                        movement,
                        Exercise(
                            movement.exerciseId,
                            "Barbell bench press",
                            emptyList(),
                            listOf(BodyPart.CHEST),
                            emptyList(),
                            Equipment.BARBELL,
                            emptyList(),
                            null,
                            null,
                            null,
                            "review",
                        ),
                        actual,
                    ),
                ),
            unit = WeightUnit.KG,
            openSessionExerciseId = movement.id,
            nextLoggableSet = UpNextSet(movement.id, movement.exerciseId, 3, SetPrefill(50.0, 10), null),
            restRemaining = Duration.ofSeconds(72),
        )

    @Test
    fun onlyTheCurrentSetIsVisibleAndOneTapLogsIt() {
        var logs = 0
        compose.setContent { GymTrackerTheme { FocusWorkoutScreen(state, FocusActions(onLogSet = { logs++ })) } }

        compose.onNodeWithText("Set 3 of 3").assertIsDisplayed()
        compose.onNodeWithText("50").assertIsDisplayed()
        compose.onNodeWithText("10").assertIsDisplayed()
        compose.onNodeWithText("1:12").assertIsDisplayed()
        compose.onNodeWithText("Last time", substring = true).assertDoesNotExist()
        compose.onNodeWithText("This exercise’s sets").assertDoesNotExist()
        compose.onNodeWithText("Start exercise").assertDoesNotExist()
        compose.onNodeWithText("LOG SET 3").assertIsDisplayed()
        capture("focus-current-set.png")
        compose.onNodeWithText("LOG SET 3").performClick()

        assertEquals(1, logs)
    }

    @Test
    fun correctionsAndTargetsOpenOnlyWhenRequested() {
        compose.setContent { GymTrackerTheme { FocusWorkoutScreen(state) } }

        compose.onNodeWithContentDescription("More exercise actions").performClick()
        compose.onNodeWithText("Sets, history & target").performClick()

        compose.onNodeWithText("This exercise’s sets").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit set 1").assertIsDisplayed()
        compose.onNodeWithText("Target", substring = true).assertIsDisplayed()
        capture("focus-records.png")
    }

    @Test
    fun largeTextKeepsTheCurrentExerciseAndLogReachable() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                GymTrackerTheme { FocusWorkoutScreen(state) }
            }
        }
        compose.onNodeWithText("Barbell bench press").assertIsDisplayed()
        compose.onNodeWithText("LOG SET 3").assertIsDisplayed()
        capture("focus-large-text.png")
    }

    @Test
    fun returningFromLongHistoryShowsTheCurrentSetAtTheTopOnAShortScreen() {
        val previous = (1..20).map { ExerciseSet("old-$it", SessionExerciseId("old"), it, 50.0, 8, null, now) }
        val sample = state.copy(lastTime = PreviousPerformance(now.minusSeconds(86_400), previous))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                GymTrackerTheme {
                    Box(Modifier.size(width = 320.dp, height = 600.dp)) { FocusWorkoutScreen(sample) }
                }
            }
        }
        compose.onNodeWithContentDescription("More exercise actions").performClick()
        compose.onNodeWithText("Sets, history & target").performClick()
        compose.onNodeWithText("Set 20 · 50 kg × 8").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Back to set").performClick()
        val title = compose.onNodeWithText("Barbell bench press").getUnclippedBoundsInRoot()
        val header = compose.onNodeWithText("Workout").getUnclippedBoundsInRoot()
        assertTrue(
            title.top >= header.bottom,
            "Returning to Focus must reveal the exercise and current set from the top",
        )
        compose.onNodeWithText("LOG SET 3").assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        captureNativeReview(name)
    }
}
