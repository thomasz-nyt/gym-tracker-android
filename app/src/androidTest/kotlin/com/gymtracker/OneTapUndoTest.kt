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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * US-35's undo, at the level the story is written in: a thumb lands on `LOG SET`, and the set
 * it wrote — and the rest it started — can be taken back.
 *
 * `OneTapLogUndoTest` (`feature:logging`'s unit suite) pins the window's rules; this is the
 * wiring check only an instrumented test can give — that a `Set logged · Undo` bar actually
 * appears on the resting screen and that its `Undo` reaches the row. Same fixture as
 * `OneTapSetLoggingTest`, which stays unedited: a prior week's set is what makes the one-tap
 * button exist at all.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OneTapUndoTest {
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
            val previously = SessionExerciseId("se-last-week-undo")
            sessionExercises.add(SessionExercise(previously, LAST_WEEK, exercise.id, 1))
            sets.add(ExerciseSet("set-last-week-undo", previously, 1, 61.23, 8, null, now.minus(Duration.ofDays(7))))

            sessions.startSession(WorkoutSession(TODAY_SESSION, member, null, now, null, null))
            sessionExercises.add(SessionExercise(TODAY, TODAY_SESSION, exercise.id, 1))
        }
    }

    @After
    fun discardTheSession() {
        runBlocking {
            sessions.deleteSession(TODAY_SESSION)
            sessions.deleteSession(LAST_WEEK)
        }
    }

    @Test
    fun aOneTapLogCanBeTakenBackAndTakesItsRestWithIt() {
        runBlocking {
            awaitReadyToLogASet()
            compose.onNodeWithText(LOG_SET, substring = true, useUnmergedTree = true).performScrollTo().performClick()
            compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
                runBlocking { sets.observeForSessionExercise(TODAY).first().isNotEmpty() }
            }

            compose.onNodeWithText(SET_LOGGED).assertIsDisplayed()
            compose.onNodeWithText(UNDO).performClick()

            compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
                runBlocking { sets.observeForSessionExercise(TODAY).first().isEmpty() }
            }
            assertTrue(sets.observeForSessionExercise(TODAY).first().isEmpty(), "the row that tap wrote is gone")
            assertNull(restTimerStore.restEndsAt.first(), "and the rest it started is ended")
        }
    }

    private fun awaitReadyToLogASet() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose
                .onAllNodesWithText(LOG_SET, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 10_000L
        const val EXERCISE = "Bench Dips"
        const val LOG_SET = "LOG SET 1"
        const val SET_LOGGED = "Set logged"
        const val UNDO = "Undo"
        val LAST_WEEK = SessionId("last-week-undo")
        val TODAY_SESSION = SessionId("today-undo")
        val TODAY = SessionExerciseId("se-today-undo")
    }
}
