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
import kotlin.test.assertEquals

/**
 * ADR-0029, US-35: the session screen's one-tap log button writes the prefilled set directly,
 * with no sheet in between — a single interaction, under `TwoTapSetLoggingTest`'s two-tap
 * ceiling rather than at it.
 *
 * This is the *addition* US-35 describes, not a replacement: `TwoTapSetLoggingTest` still
 * exercises `Add set` → `Save set` unedited, and this test exercises the new control sitting
 * beside it. Same fixture shape as `TwoTapSetLoggingTest` — a prior week's set for the same
 * exercise, so a prefill (and therefore a one-tap target) exists — deliberately, so the two
 * suites are read together as proof neither path broke the other.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OneTapSetLoggingTest {
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
     * Identical shape to `TwoTapSetLoggingTest`'s fixture: see that class for why. One addition:
     * `TwoTapSetLoggingTest`'s own `@Before` only deletes its own two hardcoded session ids, not
     * "whatever is currently active" — reasonable for a test written before this one existed,
     * but it means this suite needs to be the well-behaved neighbour. This clears any session
     * left active by a *different* test class (the same defensive read `CorrectingASetTest`'s
     * `@Before` already does), and [discardTheSession] below returns the favour for whoever
     * runs after this one.
     */
    @Before
    fun startedWorkoutWithHistory() {
        runBlocking {
            hilt.inject()
            catalogSeeder.seedIfEmpty(Instant.now().toEpochMilli())

            val member = currentMember.id()
            val benchPress = catalog.search(EXERCISE, member).first().first { it.name == EXERCISE }
            val now = Instant.now()

            sessions.observeActiveSession(member).first()?.let { sessions.deleteSession(it.id) }
            listOf(LAST_WEEK, TODAY_SESSION).forEach { sessions.deleteSession(it) }
            // The rest timer (ADR-0010) is stored global, not per-session, and this test's own
            // one-tap log button starts one (US-35's whole point). Left running, ADR-0029's
            // exclusive resting mode would hide the very button the *next* test method is
            // looking for — this bit it in its own two-method class before any other test
            // class was even involved.
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
            val previously = SessionExerciseId("se-last-week-onetap")
            sessionExercises.add(SessionExercise(previously, LAST_WEEK, benchPress.id, 1))
            sets.add(
                ExerciseSet(
                    id = "set-last-week-onetap",
                    sessionExerciseId = previously,
                    setIndex = 1,
                    // 61.23 kg is exactly 135 lb, the unit this household reads (ADR-0008).
                    weightKg = 61.23,
                    reps = 8,
                    rpe = null,
                    performedAt = now.minus(Duration.ofDays(7)),
                ),
            )

            sessions.startSession(WorkoutSession(TODAY_SESSION, member, null, now, null, null))
            sessionExercises.add(SessionExercise(TODAY, TODAY_SESSION, benchPress.id, 1))
        }
    }

    /** Leaves no active session behind, for the same reason `CorrectingASetTest`'s does. */
    @After
    fun discardTheSession() {
        runBlocking {
            sessions.deleteSession(TODAY_SESSION)
            sessions.deleteSession(LAST_WEEK)
        }
    }

    @Test
    fun aSetIsPersistedAfterOneTap() {
        runBlocking {
            awaitReadyToLogASet()
            logSetButton().performScrollTo().performClick()

            compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
                runBlocking { sets.observeForSessionExercise(TODAY).first().isNotEmpty() }
            }

            val logged = sets.observeForSessionExercise(TODAY).first()
            assertEquals(1, logged.size, "one set, logged in one tap")
            assertEquals(61.23, logged.single().weightKg, "prefilled weight, unchanged")
            assertEquals(8, logged.single().reps, "prefilled reps, unchanged")
        }
    }

    @Test
    fun theOneTapButtonNeverOpensASheet() {
        runBlocking {
            awaitReadyToLogASet()
            logSetButton().performScrollTo().performClick()

            // If a sheet had opened, "Save set" would be on screen; the whole point of the
            // one-tap button is that it never is.
            compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
                runBlocking { sets.observeForSessionExercise(TODAY).first().isNotEmpty() }
            }
            assertEquals(
                0,
                compose.onAllNodesWithText("Save set").fetchSemanticsNodes().size,
                "one tap writes the set directly; it must never open the sheet",
            )
        }
    }

    /**
     * "LOG SET 1" — the eyebrow line of the two-part log button. The fixture's prior week's set
     * is what makes this button exist at all (ADR-0029: no prefill, no one-tap target), and it
     * is a `LazyColumn` item like `Add set` is in `TwoTapSetLoggingTest`, so it can start below
     * the fold on a short screen — the same reason that test scrolls first.
     */
    private fun logSetButton() = compose.onNodeWithText("LOG SET 1", substring = true, useUnmergedTree = true)

    /**
     * The mirror of `TwoTapSetLoggingTest`'s `awaitReadyToLogASet`: the button this test taps
     * only exists once `nextLoggableSet` has resolved, which — like `SetEntryController.open` —
     * is a suspend Room read (`PrefillFromLastSet`, `lastSetOfBefore`) that `waitForIdle` alone
     * does not wait for. Without this, the very first interaction can land on the loading frame
     * before the fixture's prior-week set has been read back out, and "LOG SET 1" is not there
     * to find yet — not a real absence, just too early to look.
     */
    private fun awaitReadyToLogASet() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose
                .onAllNodesWithText("LOG SET 1", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 10_000L
        const val EXERCISE = "Bench Dips"
        val LAST_WEEK = SessionId("last-week-onetap")
        val TODAY_SESSION = SessionId("today-onetap")
        val TODAY = SessionExerciseId("se-today-onetap")
    }
}
