package com.gymtracker

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import java.time.Instant
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * US-04, "Correct a mistake", at the level the story is written in: a logged set on screen, a
 * finger, and a wrong number.
 *
 * The unit suites cover the arithmetic, the validation and the undo window. What only an
 * instrumented test can show is that a logged set is *reachable* — ADR-0022 exists because it
 * was not. `LoggedSets` collapsed three sets into "3 × 12", a line with no set id behind it and
 * no tap target on it, so the story had nowhere to happen.
 *
 * Two rules from ADR-0019 are asserted here rather than left to review:
 * - Delete lives **inside** the editor that owns the set, never beside "Add set".
 * - Deleting is undoable for five seconds (US-04), and restores the row unchanged.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CorrectingASetTest {
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
     * Mid-workout with two sets already logged, and they are **identical** on purpose: before
     * ADR-0022 these two rendered as the single line "2 × 8", which is exactly the case where
     * "edit any set" had no way to say *which*.
     */
    @Before
    fun midWorkoutWithTwoIdenticalSets() {
        runBlocking {
            hilt.inject()
            catalogSeeder.seedIfEmpty(Instant.now().toEpochMilli())

            val member = currentMember.id()
            val exercise = catalog.search(EXERCISE, member).first().first { it.name == EXERCISE }
            val now = Instant.now()

            // The app database is a real file shared by every test on the device, and US-01
            // allows one active session per member. Any session another test left running would
            // be the one the screen resumes, so clear it rather than adding a second.
            sessions.observeActiveSession(member).first()?.let { sessions.deleteSession(it.id) }
            sessions.deleteSession(TODAY_SESSION)

            sessions.startSession(WorkoutSession(TODAY_SESSION, member, null, now, null, null))
            sessionExercises.add(SessionExercise(TODAY, TODAY_SESSION, exercise.id, 1))
            listOf(FIRST_SET, SECOND_SET).forEachIndexed { index, id ->
                sets.add(
                    ExerciseSet(
                        id = id,
                        sessionExerciseId = TODAY,
                        setIndex = index + 1,
                        // 61.23 kg is exactly 135 lb, the unit this household reads (ADR-0008).
                        weightKg = 61.23,
                        reps = 8,
                        rpe = null,
                        performedAt = now,
                    ),
                )
            }
        }
    }

    /**
     * Leaves no active session behind. Without this the next test to run finds two of them,
     * resumes the wrong one, and fails somewhere unrelated to what it is actually asserting —
     * which is exactly how this test first broke `TwoTapSetLoggingTest`.
     */
    @After
    fun discardTheSession() {
        runBlocking { sessions.deleteSession(TODAY_SESSION) }
    }

    @Test
    fun editingOneSetLeavesItsTwinAlone() {
        runBlocking {
            awaitSetsOnScreen()

            // The whole point of ADR-0022: set 2 is individually addressable even though it is
            // identical to set 1.
            compose.onNodeWithContentDescription("Edit set 2").performClick()
            compose.waitForIdle()

            compose.onNodeWithText("Save changes").assertIsDisplayed()
            compose.onNodeWithContentDescription("Increase Reps").performClick()
            compose.onNodeWithText("Save changes").performClick()
            awaitEditorClosed()

            val logged = sets.observeForSessionExercise(TODAY).first().sortedBy { it.setIndex }
            assertEquals(2, logged.size, "editing must not add or remove a row")
            assertEquals(8, logged.first().reps, "set 1 was not the one being edited")
            assertEquals(9, logged.last().reps, "set 2 should have gained a rep")
            assertEquals(SECOND_SET, logged.last().id, "editing keeps the row, it does not replace it")
        }
    }

    @Test
    fun deleteLivesInsideTheEditorAndIsUndoable() {
        runBlocking {
            awaitSetsOnScreen()

            // ADR-0019: a destructive control never shares a surface with a save. There is no
            // "Delete set" anywhere on the session screen until the editor for one is open.
            assertEquals(
                0,
                compose.onAllNodesWithText(DELETE).fetchSemanticsNodes().size,
                "delete must not sit on the card beside Add set",
            )

            compose.onNodeWithContentDescription("Edit set 1").performClick()
            compose.waitForIdle()
            compose.onNodeWithText(DELETE).performClick()
            awaitEditorClosed()

            assertNull(
                sets.observeForSessionExercise(TODAY).first().firstOrNull { it.id == FIRST_SET },
                "the set should be gone",
            )

            // US-04: undo, available for five seconds.
            compose.onNodeWithText("Undo").performClick()
            awaitSetCount(2)

            val restored = sets.observeForSessionExercise(TODAY).first().first { it.id == FIRST_SET }
            assertNotNull(restored, "undo restores the row")
            assertEquals(1, restored.setIndex, "restored unchanged, same index")
            assertEquals(8, restored.reps, "restored unchanged, same reps")
            assertEquals(61.23, restored.weightKg, "restored unchanged, same weight")
        }
    }

    private fun awaitSetsOnScreen() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithContentDescription("Edit set 1").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** `waitForIdle` synchronises Compose, not the coroutine doing the Room write. */
    private fun awaitEditorClosed() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("Save changes").fetchSemanticsNodes().isEmpty()
        }
    }

    private suspend fun awaitSetCount(expected: Int) {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            runBlocking { sets.observeForSessionExercise(TODAY).first().size == expected }
        }
    }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 10_000L
        const val EXERCISE = "Bench Dips"
        const val DELETE = "Delete set"
        val TODAY_SESSION = SessionId("today-correcting")
        val TODAY = SessionExerciseId("se-today-correcting")
        const val FIRST_SET = "set-one"
        const val SECOND_SET = "set-two"
    }
}
