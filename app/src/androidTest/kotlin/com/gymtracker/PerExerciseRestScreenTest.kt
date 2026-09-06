package com.gymtracker

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.gymtracker.app.MainActivity
import com.gymtracker.core.data.exercise.CatalogSeeder
import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.rest.RestTimerStore
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
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

/**
 * US-05 and US-30 as amended by ADR-0050: a movement whose target names a rest shows it on its
 * target line, and the rest that follows its set is that long — read off the band's "of 1:30".
 *
 * `PerExerciseRestTest` pins the wiring in the ViewModel; this is the check only an instrumented
 * test can give — that the rest a routine copied into the session reaches the screen and the
 * stored countdown through the real one-tap button. The movement is seeded with its target the
 * way `StartSessionFromRoutine` leaves it, so no routine has to be built in the UI first.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PerExerciseRestScreenTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    /** As in `TwoTapSetLoggingTest`: US-05's one-time prompt must not cover the screen. */
    @get:Rule(order = 1)
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var sessions: SessionRepository

    @Inject
    lateinit var sessionExercises: SessionExerciseRepository

    @Inject
    lateinit var catalog: ExerciseCatalog

    @Inject
    lateinit var catalogSeeder: CatalogSeeder

    @Inject
    lateinit var currentMember: CurrentMember

    @Inject
    lateinit var restTimerStore: RestTimerStore

    @Before
    fun startedWorkoutWithATargetThatNamesItsRest() {
        runBlocking {
            hilt.inject()
            catalogSeeder.seedIfEmpty(Instant.now().toEpochMilli())

            val member = currentMember.id()
            val exercise = catalog.search(EXERCISE, member).first().first { it.name == EXERCISE }

            sessions.observeActiveSession(member).first()?.let { sessions.deleteSession(it.id) }
            sessions.deleteSession(TODAY_SESSION)
            restTimerStore.setRestEndsAt(null)
            // See GuidedFlowScreenTest: the already-granted permission would otherwise still be
            // requested on this image, and that system dialog is outside the tree.
            restTimerStore.markNotificationPermissionAsked()

            sessions.startSession(WorkoutSession(TODAY_SESSION, member, null, Instant.now(), null, null))
            // 61.23 kg is 135 lb, the member's reading unit; a 90-second rest is the point.
            sessionExercises.add(
                SessionExercise(
                    id = TODAY,
                    sessionId = TODAY_SESSION,
                    exerciseId = exercise.id,
                    position = 1,
                    target = MovementTarget(sets = 3, reps = 8, weightKg = 61.23, restSeconds = 90),
                ),
            )
        }
    }

    @After
    fun discardTheSession() {
        runBlocking {
            restTimerStore.setRestEndsAt(null)
            sessions.deleteSession(TODAY_SESSION)
        }
    }

    @Test
    fun theTargetLineNamesTheRestAndTheRestThatFollowsIsThatLong() {
        runBlocking {
            awaitText(LOG_SET)
            compose.onNodeWithText(TARGET_LINE, substring = true, useUnmergedTree = true).assertIsDisplayed()

            compose.onNodeWithText(LOG_SET, substring = true, useUnmergedTree = true).performScrollTo().performClick()

            compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
                runBlocking { restTimerStore.restEndsAt.first() != null }
            }
            assertEquals(
                Duration.ofSeconds(90),
                restTimerStore.restTotal.first(),
                "the movement's own rest, not the default",
            )
            awaitText(BAND_TOTAL)
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
        const val TARGET_LINE = "1:30 rest"
        const val BAND_TOTAL = "of 1:30"
        val TODAY_SESSION = SessionId("today-per-exercise-rest")
        val TODAY = SessionExerciseId("se-today-per-exercise-rest")
    }
}
