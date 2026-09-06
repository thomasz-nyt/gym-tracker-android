package com.gymtracker

import android.Manifest
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * US-56 as amended 2026-09-05: finishing the workout ends the rest that was running, so no
 * countdown — and no "Rest over" — outlives the session.
 *
 * `EndSessionTest` and `RestCountdownTest` pin the rule; this is the wiring only an instrumented
 * test can give — the real `FINISH` control, the real confirm dialog, the real DataStore. The
 * coordinator's own reaction to that write (alarms cancelled, both notifications dismissed) is
 * pinned by `RestNotificationCoordinatorTest`; a notification shade is not something a Compose
 * test can read.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FinishDuringRestTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

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

    /** A prior week's set, so a one-tap `LOG SET 1` exists to start the rest with (as in `RestExtendTest`). */
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
            val previously = SessionExerciseId("se-last-week-finish")
            sessionExercises.add(SessionExercise(previously, LAST_WEEK, exercise.id, 1))
            sets.add(
                ExerciseSet(
                    id = "set-last-week-finish",
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
    fun discardTheSessions() {
        runBlocking {
            restTimerStore.setRestEndsAt(null)
            sessions.deleteSession(TODAY_SESSION)
            sessions.deleteSession(LAST_WEEK)
        }
    }

    @Test
    fun finishingTheWorkoutEndsTheRestThatWasRunning() {
        runBlocking {
            awaitText(LOG_SET)
            compose.onNodeWithText(LOG_SET, substring = true, useUnmergedTree = true).performScrollTo().performClick()
            compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
                runBlocking { restTimerStore.restEndsAt.first() != null }
            }
            assertNotNull(restTimerStore.restEndsAt.first(), "the one-tap log started a rest")

            compose.onNodeWithText(FINISH).performClick()
            awaitText(CONFIRM_FINISH)
            compose.onNodeWithText(CONFIRM_FINISH).performClick()

            compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
                runBlocking { restTimerStore.restEndsAt.first() == null }
            }
            assertNull(restTimerStore.restTotal.first(), "a countdown to nothing is gone with its end time")
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

        /** The session header's finish control (ADR-0029), and its confirm dialog's own button. */
        const val FINISH = "FINISH"
        const val CONFIRM_FINISH = "Finish workout"
        val LAST_WEEK = SessionId("last-week-finish")
        val TODAY_SESSION = SessionId("today-finish")
        val TODAY = SessionExerciseId("se-today-finish")
    }
}
