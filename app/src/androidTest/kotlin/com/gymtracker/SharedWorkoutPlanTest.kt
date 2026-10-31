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
import com.gymtracker.core.domain.model.MovementTarget
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

/** US-63: copied plan counts actual sets; completing it never finishes the workout implicitly. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SharedWorkoutPlanTest {
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
            sessionExercises.add(SessionExercise(TODAY, TODAY_SESSION, benchPress.id, 1, MovementTarget(3, 12, 80.0)))
        }
    }

    @Test
    fun copiedPlanCountsActualSetsAndAllowsAnExplicitExtraSet(): Unit =
        runBlocking {
            for (number in 1..3) {
                awaitText("LOG SET $number")
                compose.onNodeWithText("LOG SET $number").assertIsDisplayed().performClick()
                compose.waitUntil(
                    10_000,
                ) { runBlocking { sets.observeForSessionExercise(TODAY).first().size == number } }
            }
            awaitText("Plan met")
            compose.onNodeWithText("Review workout").assertIsDisplayed()
            assertEquals(TODAY_SESSION, sessions.findActiveSession(currentMember.id())?.id)
            assertEquals(listOf(8, 8, 8), sets.observeForSessionExercise(TODAY).first().map { it.reps })
            compose.onNodeWithText("Add an extra set").performScrollTo().performClick()
            awaitText("Save set")
            compose.onNodeWithText("Save set").performClick()
            compose.waitUntil(10_000) { runBlocking { sets.observeForSessionExercise(TODAY).first().size == 4 } }
            assertEquals(TODAY_SESSION, sessions.findActiveSession(currentMember.id())?.id)
        }

    private fun awaitText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 10_000L
        const val EXERCISE = "Bench Dips"
        val LAST_WEEK = SessionId("last-week-guided")
        val TODAY_SESSION = SessionId("today-guided")
        val TODAY = SessionExerciseId("se-today-guided")
    }
}
