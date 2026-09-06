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
 * US-05 as amended by ADR-0049: `+30S` on the rest band buys thirty more seconds, and the bar's
 * total moves with the end time so "of 1:30" reads true.
 *
 * `RestTimerTest` pins the arithmetic; this is the wiring check only an instrumented test can give
 * — that the control is actually on the resting screen and reaches the stored rest. Same fixture
 * shape as `OneTapSetLoggingTest`: a prior week's set, so a one-tap log button exists to start the
 * rest with.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RestExtendTest {
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
            val previously = SessionExerciseId("se-last-week-extend")
            sessionExercises.add(SessionExercise(previously, LAST_WEEK, exercise.id, 1))
            sets.add(
                ExerciseSet(
                    id = "set-last-week-extend",
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
    fun thirtySecondsMoreMovesTheEndAndTheTotalTogether() {
        runBlocking {
            compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
                compose
                    .onAllNodesWithText(LOG_SET, substring = true, useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose
                .onNodeWithText(LOG_SET, substring = true, useUnmergedTree = true)
                .performScrollTo()
                .performClick()
            compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
                runBlocking { restTimerStore.restEndsAt.first() != null }
            }
            val endsAtBefore = assertNotNull(restTimerStore.restEndsAt.first(), "the one-tap log started a rest")
            val totalBefore = assertNotNull(restTimerStore.restTotal.first())

            compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
                compose.onAllNodesWithText(EXTEND).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(EXTEND).performClick()

            compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
                runBlocking { restTimerStore.restEndsAt.first() != endsAtBefore }
            }
            assertEquals(endsAtBefore.plusSeconds(30), restTimerStore.restEndsAt.first(), "thirty seconds more")
            assertEquals(
                totalBefore.plusSeconds(30),
                restTimerStore.restTotal.first(),
                "and the bar's total moved with it",
            )

            // "of 1:30" — the default rest is 60 s (US-05), so the band now reads the extended total.
            compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
                compose.onAllNodesWithText(EXTENDED_TOTAL, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(EXTENDED_TOTAL, substring = true).assertIsDisplayed()
        }
    }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 10_000L
        const val EXERCISE = "Bench Dips"
        const val LOG_SET = "LOG SET 1"
        const val EXTEND = "+30S"
        const val EXTENDED_TOTAL = "of 1:30"
        val LAST_WEEK = SessionId("last-week-extend")
        val TODAY_SESSION = SessionId("today-extend")
        val TODAY = SessionExerciseId("se-today-extend")
    }
}
