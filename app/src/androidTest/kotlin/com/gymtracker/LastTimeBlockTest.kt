package com.gymtracker

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
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
import com.gymtracker.core.domain.rest.RestTimerStore
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import com.gymtracker.core.domain.set.SetRepository
import com.gymtracker.core.domain.units.WeightFormatter
import com.gymtracker.core.domain.units.WeightUnit
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
 * US-61: the whole of last time, under the open movement, while logging.
 *
 * `PreviousPerformanceOfTest` pins what "last time" is; this is the wiring check only an
 * instrumented test can give — that the block is actually on the session screen with every set
 * of the previous appearance, and absent for a movement never done before (US-13's absence
 * pattern, not an empty label).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LastTimeBlockTest {
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

    /**
     * Last week: three sets of Bench Dips — eight at @8, eight, then seven at @9 — the shape a
     * lifter needs to see in full to know whether to add load today. Today: Bench Dips, no sets
     * yet, and a movement never done before.
     */
    @Before
    fun startedWorkoutWithLastWeeksThreeSets() {
        runBlocking {
            hilt.inject()
            catalogSeeder.seedIfEmpty(Instant.now().toEpochMilli())

            val member = currentMember.id()
            val done = catalog.search(DONE_BEFORE, member).first().first { it.name == DONE_BEFORE }
            val fresh = catalog.search(NEVER_DONE, member).first().first { it.name == NEVER_DONE }
            val now = Instant.now()
            val lastWeek = now.minus(Duration.ofDays(7))

            sessions.observeActiveSession(member).first()?.let { sessions.deleteSession(it.id) }
            listOf(LAST_WEEK, TODAY_SESSION).forEach { sessions.deleteSession(it) }
            restTimerStore.setRestEndsAt(null)

            sessions.startSession(
                WorkoutSession(
                    id = LAST_WEEK,
                    userId = member,
                    gymName = null,
                    startedAt = lastWeek,
                    endedAt = lastWeek.plus(Duration.ofHours(1)),
                    metrics = null,
                ),
            )
            val previously = SessionExerciseId("se-last-week-lasttime")
            sessionExercises.add(SessionExercise(previously, LAST_WEEK, done.id, 1))
            // 61.23 kg is exactly 135 lb, the unit this household reads (ADR-0008).
            sets.add(ExerciseSet("lt-1", previously, 1, 61.23, 8, 8.0, lastWeek))
            sets.add(ExerciseSet("lt-2", previously, 2, 61.23, 8, null, lastWeek.plusSeconds(120)))
            sets.add(ExerciseSet("lt-3", previously, 3, 61.23, 7, 9.0, lastWeek.plusSeconds(240)))

            sessions.startSession(WorkoutSession(TODAY_SESSION, member, null, now, null, null))
            sessionExercises.add(SessionExercise(TODAY_DONE, TODAY_SESSION, done.id, 1))
            sessionExercises.add(SessionExercise(TODAY_FRESH, TODAY_SESSION, fresh.id, 2))
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
    fun theOpenMovementShowsEverySetOfLastTime() {
        // The open exercise is the first by position (nothing logged yet, US-45's default), the
        // one done last week — so the block is on screen as soon as the session is.
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(LAST_TIME, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText(LAST_TIME, substring = true).assertIsDisplayed()
        // All three sets, in order, each with its effort where one was recorded — not the last
        // set alone, which is the rest panel's job (ADR-0023).
        val load = WeightFormatter.format(61.23, WeightUnit.LB).primary
        compose.onNodeWithText("$load × 8 @8  ·  $load × 8  ·  $load × 7 @9").assertIsDisplayed()
    }

    @Test
    fun aMovementNeverDoneBeforeShowsNoBlock() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(NEVER_DONE).fetchSemanticsNodes().isNotEmpty()
        }

        // US-45: tapping the other exercise opens it fully. Its own header then carries no
        // "last time" at all — absence, not an empty label (US-13, constitution §2.4).
        compose.onNodeWithText(NEVER_DONE).performClick()
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(LAST_TIME, substring = true).fetchSemanticsNodes().isEmpty()
        }
        compose.onAllNodesWithText(LAST_TIME, substring = true).assertCountEquals(0)
    }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 10_000L
        const val DONE_BEFORE = "Bench Dips"
        const val NEVER_DONE = "Ab Crunch Machine"
        const val LAST_TIME = "LAST TIME"

        val LAST_WEEK = SessionId("last-week-lasttime")
        val TODAY_SESSION = SessionId("today-lasttime")
        val TODAY_DONE = SessionExerciseId("se-today-lasttime-done")
        val TODAY_FRESH = SessionExerciseId("se-today-lasttime-fresh")
    }
}
