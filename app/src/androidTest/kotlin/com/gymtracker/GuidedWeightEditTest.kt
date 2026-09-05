package com.gymtracker

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.gymtracker.app.MainActivity
import com.gymtracker.core.data.exercise.CatalogSeeder
import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.rest.RestTimerStore
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import com.gymtracker.core.domain.set.SetRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * US-05a as amended 2026-09-05: on the guided screen the weight is a stepper like the rep count,
 * and `LOG SET n` writes what the field shows.
 *
 * `GuidedFlowTest` pins the arithmetic and the carry-forward; this is the wiring check only an
 * instrumented test can give — that the control is actually on the running screen, that the log
 * button's detail line follows it, and that the set written is the stepped weight rather than the
 * start dialog's. Same fixture shape as `GuidedFlowScreenTest`: a prior week's set, so the dialog
 * opens with a real prefill (135 lb) and `Start` is enabled without typing.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class GuidedWeightEditTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    /** Same reason as `GuidedFlowScreenTest`: finishing a set starts the rest timer. */
    @get:Rule(order = 1)
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var sessions: SessionRepository

    @Inject
    lateinit var sessionExercises: SessionExerciseRepository

    @Inject
    lateinit var sets: SetRepository

    @Inject
    lateinit var catalog: ExerciseCatalog

    @Inject
    lateinit var catalogSeeder: CatalogSeeder

    @Inject
    lateinit var currentMember: CurrentMember

    @Inject
    lateinit var restTimerStore: RestTimerStore

    @Before
    fun startedWorkoutWithHistory() {
        runBlocking {
            hilt.inject()
            catalogSeeder.seedIfEmpty(Instant.now().toEpochMilli())

            val member = currentMember.id()
            val exercise = catalog.search(EXERCISE, member).first().first { it.name == EXERCISE }
            val now = Instant.now()

            sessions.observeActiveSession(member).first()?.let { sessions.deleteSession(it.id) }
            listOf(LAST_WEEK, TODAY_SESSION).forEach { sessions.deleteSession(it) }
            restTimerStore.setRestEndsAt(null)
            // See GuidedFlowScreenTest for why: the already-granted permission would otherwise
            // still be requested on this image, and that system dialog is outside the tree.
            restTimerStore.markNotificationPermissionAsked()

            sessions.startSession(
                WorkoutSession(
                    id = LAST_WEEK,
                    userId = member,
                    gymName = null,
                    startedAt = now.minus(Duration.ofDays(7)),
                    endedAt = now.minus(Duration.ofDays(7)).plus(Duration.ofHours(1)),
                    metrics = null,
                ),
            )
            val previously = SessionExerciseId("se-last-week-guided-weight")
            sessionExercises.add(SessionExercise(previously, LAST_WEEK, exercise.id, 1))
            sets.add(
                ExerciseSet(
                    id = "set-last-week-guided-weight",
                    sessionExerciseId = previously,
                    setIndex = 1,
                    weightKg = 61.23,
                    reps = 8,
                    rpe = null,
                    performedAt = now.minus(Duration.ofDays(7)),
                ),
            )

            sessions.startSession(WorkoutSession(TODAY_SESSION, member, null, now, null, null))
            sessionExercises.add(SessionExercise(TODAY, TODAY_SESSION, exercise.id, 1))
        }
    }

    @After
    fun discardTheSession() {
        runBlocking {
            restTimerStore.setRestEndsAt(null)
            sessions.deleteSession(TODAY_SESSION)
            sessions.deleteSession(LAST_WEEK)
        }
    }

    @Test
    fun plusOnTheRunningScreenChangesWhatLogSetWrites() {
        runBlocking {
            awaitText("Start exercise")
            compose.onNodeWithText("Start exercise", useUnmergedTree = true).performScrollTo().performClick()
            awaitText("Start")
            compose.onNodeWithText("Start").performClick()
            awaitText(LOG_SET)

            // 135 lb from last week's set, stepped once — the 5 lb set entry's own stepper moves.
            compose.onNodeWithContentDescription(INCREASE_WEIGHT).performScrollTo().performClick()
            awaitText(STEPPED_DETAIL)

            compose.onNodeWithText(LOG_SET, substring = true, useUnmergedTree = true).performScrollTo().performClick()
            awaitText("REST")

            val logged = sets.observeForSessionExercise(TODAY).first()
            val written = assertNotNull(logged.single().weightKg, "a loaded set, not bodyweight")
            assertEquals(63.5, written, 0.01, "140 lb in canonical kilograms — not the dialog's 135")
        }
    }

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose
                .onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 10_000L
        const val EXERCISE = "Bench Dips"
        const val LOG_SET = "LOG SET 1"

        /** `StepperField`'s own description for its `+`, for the label the running screen gives the weight. */
        const val INCREASE_WEIGHT = "Increase Weight (lb)"
        const val STEPPED_DETAIL = "140 lb × 12"
        val LAST_WEEK = SessionId("last-week-guided-weight")
        val TODAY_SESSION = SessionId("today-guided-weight")
        val TODAY = SessionExerciseId("se-today-guided-weight")
    }
}
