package com.gymtracker

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.test.assertEquals

/**
 * ADR-0033: the guided screen (US-05a) rebuilt on the shipped design system. Until this test,
 * no instrumented coverage reached guided mode at all — the five other UI tests all stay on the
 * session screen — which is part of how this screen went un-redesigned for as long as it did.
 *
 * Minimal on purpose: this proves the rebuilt screen renders and actually logs a set, not every
 * state. `GuidedFlowTest` (`feature:logging`'s unit suite) already covers the domain behaviour —
 * this is the wiring check a unit test cannot give.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class GuidedFlowScreenTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    /** Same reason as `TwoTapSetLoggingTest`: finishing a set starts the rest timer. */
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

    /** A prior set so the setup dialog's "Start" is enabled without typing (a real prefill). */
    @Before
    fun startedWorkoutWithHistory() {
        runBlocking {
            hilt.inject()
            catalogSeeder.seedIfEmpty(Instant.now().toEpochMilli())

            val member = currentMember.id()
            val benchPress = catalog.search(EXERCISE, member).first().first { it.name == EXERCISE }
            val now = Instant.now()

            listOf(LAST_WEEK, TODAY_SESSION).forEach { sessions.deleteSession(it) }
            restTimerStore.setRestEndsAt(null)
            // GrantPermissionRule pre-grants POST_NOTIFICATIONS at the OS level, but on this
            // emulator image RestNotifications still calls Activity.requestPermissions() for it
            // the first time a rest starts (RestController.shouldAskForNotifications reads its
            // own "have we asked" flag, not the OS grant state) — and requesting an
            // already-granted permission still surfaces the system dialog here, a real,
            // reproducible platform quirk, not a theory: confirmed by walking the exact same
            // flow by hand on this AVD. That dialog is a different window from a different
            // package, which the test's Compose semantics tree cannot see into or past. Marking
            // the permission already-asked skips the request path entirely, the same state a
            // real member's second-ever workout would be in.
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
            val previously = SessionExerciseId("se-last-week-guided")
            sessionExercises.add(SessionExercise(previously, LAST_WEEK, benchPress.id, 1))
            sets.add(
                ExerciseSet(
                    id = "set-last-week-guided",
                    sessionExerciseId = previously,
                    setIndex = 1,
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

    @Test
    fun theGuidedFlowRendersAndLogs() {
        runBlocking {
            awaitReadyToStart()

            startExerciseButton().performScrollTo().performClick()
            awaitSetupDialogOpen()

            compose.onNodeWithText(EXERCISE).assertExists("the exercise name reaches the running screen")
            // The setup dialog defaults to a target of one set (GuidedController.
            // DEFAULT_TARGET_SETS) — with that target, finishing set 1 also completes the
            // exercise, which skips straight to the Done summary and never rests. Bumping the
            // target to two is what makes the resting hero, the state this test exists to prove
            // renders, actually reachable.
            compose.onNodeWithText("1").performTextReplacement("2")
            compose.onNodeWithText("Start").performClick()
            awaitRunningScreen()

            compose.onNodeWithText("Log set 1", substring = true).performScrollTo().performClick()
            awaitResting()

            val logged = sets.observeForSessionExercise(TODAY).first()
            assertEquals(1, logged.size, "the hero's Log set button actually wrote a set")
        }
    }

    private fun startExerciseButton() = compose.onNodeWithText("Start exercise", useUnmergedTree = true)

    private fun awaitReadyToStart() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("Start exercise", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** `GuidedController.start` is synchronous, but the dialog's own compose pass still needs a wait. */
    private fun awaitSetupDialogOpen() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("Start").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** `GuidedController.begin` writes to DataStore before the plan appears — a real suspend read. */
    private fun awaitRunningScreen() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose
                .onAllNodesWithText("Log set 1", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    /**
     * The resting hero's `Rest` eyebrow — proof the set was written and the rest timer started.
     *
     * `EyebrowLabel` uppercases at the call site (display-only; the string passed in stays
     * `"Rest"`), so the semantics tree holds `"REST"` — matched case-sensitively here on purpose,
     * the same way `onNodeWithText("Add set")` elsewhere matches the exact drawn string rather
     * than a looser one.
     */
    private fun awaitResting() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("REST", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 10_000L
        const val EXERCISE = "Bench Dips"
        val LAST_WEEK = SessionId("last-week-guided")
        val TODAY_SESSION = SessionId("today-guided")
        val TODAY = SessionExerciseId("se-today-guided")
    }
}
