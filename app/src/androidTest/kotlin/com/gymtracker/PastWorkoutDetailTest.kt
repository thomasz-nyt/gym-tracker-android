package com.gymtracker

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/**
 * Two things Progress said differently from the session screen, both found by the 2026-09-04
 * UI/UX review and neither covered by any test until now.
 *
 * [aPastWorkoutShowsTheIntervalBetweenItsSets] pins US-44 on the workout detail: the active
 * session had shown "+1:30" on every set row since Turn 3, and the same session opened from
 * Progress the next day showed none — `PastLoggedSets` copied `LoggedSets` "row for row" (its own
 * words) before the interval existed and was never brought back in line. Seeded through the
 * repositories, the way `CorrectingASetTest` builds its fixture, with two sets exactly ninety
 * seconds apart so the expected string is not a guess.
 *
 * [weeklyVolumeSpellsMultiplicationTheWayEveryLoadLineDoes] pins one character: the weekly volume
 * subtitle wrote "sets x reps x load" with the letter x where every load line in the app writes
 * "135 lb × 8" with the sign.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PastWorkoutDetailTest {
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

    /**
     * One finished freestyle workout, two sets of one exercise ninety seconds apart. Started and
     * ended through the repository's own two calls rather than inserted pre-finished, so the
     * fixture is a session the app itself could have produced.
     */
    @Before
    fun aFinishedWorkoutWithTwoSetsNinetySecondsApart() {
        runBlocking {
            hilt.inject()
            catalogSeeder.seedIfEmpty(Instant.now().toEpochMilli())

            val member = currentMember.id()
            val exercise = catalog.search(EXERCISE, member).first().first { it.name == EXERCISE }

            // No workout running, so Train home shows its shortcuts and the bar is visible.
            sessions.observeActiveSession(member).first()?.let { sessions.deleteSession(it.id) }
            sessions.deleteSession(PAST_SESSION)

            val startedAt = Instant.now().minus(Duration.ofHours(2))
            val secondSetAt = startedAt.plus(INTERVAL)
            sessions.startSession(WorkoutSession(PAST_SESSION, member, null, startedAt, null, null))
            sessionExercises.add(SessionExercise(PAST_EXERCISE, PAST_SESSION, exercise.id, 1))
            // 61.23 kg is exactly 135 lb, the unit this household reads (ADR-0008).
            sets.add(ExerciseSet(FIRST_SET, PAST_EXERCISE, 1, 61.23, 8, null, startedAt))
            sets.add(ExerciseSet(SECOND_SET, PAST_EXERCISE, 2, 61.23, 8, null, secondSetAt))
            sessions.endSession(PAST_SESSION, secondSetAt)
        }
    }

    @After
    fun discardTheWorkout() {
        runBlocking { sessions.deleteSession(PAST_SESSION) }
    }

    @Test
    fun aPastWorkoutShowsTheIntervalBetweenItsSets() {
        awaitHome()
        compose.onNodeWithText(PROGRESS_TAB).performClick()
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(FREESTYLE_ROW).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText(FREESTYLE_ROW).performClick()

        // The second set's row carries the gap since the first; the first set's row carries
        // none (US-44: nothing before it to measure from), so exactly one row says "+1:30".
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(INTERVAL_TEXT, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(INTERVAL_TEXT, substring = true).assertIsDisplayed()
    }

    @Test
    fun weeklyVolumeSpellsMultiplicationTheWayEveryLoadLineDoes() {
        awaitHome()
        compose.onNodeWithText(PROGRESS_TAB).performClick()
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(WEEKLY_VOLUME_ROW).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText(WEEKLY_VOLUME_ROW).performClick()

        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(WEEKLY_VOLUME_SUBTITLE).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(WEEKLY_VOLUME_SUBTITLE).assertIsDisplayed()
    }

    private fun awaitHome() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(START).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 10_000L
        const val START = "Start workout"

        /** The bar's label (ADR-0030), the only "Progress" on screen while Train home is showing. */
        const val PROGRESS_TAB = "Progress"

        /** US-32: a session started without a routine leads its Progress row with this. */
        const val FREESTYLE_ROW = "Freestyle"
        const val WEEKLY_VOLUME_ROW = "Weekly volume by muscle"
        const val WEEKLY_VOLUME_SUBTITLE = "By muscle. Weight moved: sets × reps × load."

        const val EXERCISE = "Barbell Bench Press - Medium Grip"
        val PAST_SESSION = SessionId("past-workout-detail-test")
        val PAST_EXERCISE = SessionExerciseId("past-workout-detail-test-bench")
        const val FIRST_SET = "past-workout-detail-test-set-1"
        const val SECOND_SET = "past-workout-detail-test-set-2"

        val INTERVAL: Duration = Duration.ofSeconds(90)
        const val INTERVAL_TEXT = "+1:30"
    }
}
