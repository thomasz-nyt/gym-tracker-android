package com.gymtracker

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.rest.UpNextSet
import com.gymtracker.core.domain.set.SetPrefill
import com.gymtracker.core.domain.units.WeightUnit
import com.gymtracker.feature.logging.FocusActions
import com.gymtracker.feature.logging.FocusWorkoutScreen
import com.gymtracker.feature.logging.SessionExerciseRow
import com.gymtracker.feature.logging.SessionUiState
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

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
            exercises = listOf(SessionExerciseRow(movement, null, actual)),
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

    private fun capture(name: String) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name).outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
