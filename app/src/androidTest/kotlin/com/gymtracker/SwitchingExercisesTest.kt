package com.gymtracker

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import java.time.Instant
import javax.inject.Inject
import kotlin.test.assertEquals

/**
 * US-45 (ADR-0037): the machine was taken, so a set landed on the second exercise before the
 * first was ever touched. `ExerciseSelectionTest` (`feature:logging`'s unit suite) covers the
 * ViewModel's derivation and its stickiness; this is the wiring check only an instrumented test
 * can give — that the earlier exercise is actually reachable and tappable on screen, not just
 * present in `SessionUiState`. Reproduces the exact bug as reported: before this story, the
 * skipped exercise had no row, no button, nothing to tap anywhere on the session screen.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SwitchingExercisesTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    /** As in `TwoTapSetLoggingTest`: US-05's one-time rest prompt must not cover the screen. */
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
     * The second exercise already has a set logged; the first has none — exactly the shape a
     * member gets from "the first machine was taken, so I started on the second one." Seeded
     * directly through the repositories, the same way `CorrectingASetTest` builds a mid-workout
     * fixture, rather than driven through the UI.
     */
    @Before
    fun secondExerciseLoggedBeforeTheFirst() {
        runBlocking {
            hilt.inject()
            catalogSeeder.seedIfEmpty(Instant.now().toEpochMilli())

            val member = currentMember.id()
            val first = catalog.search(FIRST_EXERCISE, member).first().first { it.name == FIRST_EXERCISE }
            val second = catalog.search(SECOND_EXERCISE, member).first().first { it.name == SECOND_EXERCISE }
            val now = Instant.now()

            // As in CorrectingASetTest: the app database is a real file shared by every test on
            // the device, and US-01 allows one active session per member.
            sessions.observeActiveSession(member).first()?.let { sessions.deleteSession(it.id) }
            sessions.deleteSession(TODAY_SESSION)
            restTimerStore.setRestEndsAt(null)

            sessions.startSession(WorkoutSession(TODAY_SESSION, member, null, now, null, null))
            sessionExercises.add(SessionExercise(FIRST_ID, TODAY_SESSION, first.id, 1))
            sessionExercises.add(SessionExercise(SECOND_ID, TODAY_SESSION, second.id, 2))
            sets.add(ExerciseSet("set-second", SECOND_ID, 1, 61.23, 8, null, now))
        }
    }

    @After
    fun discardTheSession() {
        runBlocking { sessions.deleteSession(TODAY_SESSION) }
    }

    @Test
    fun switchingBackToTheSkippedExerciseOpensItAndLogsAgainstIt() {
        runBlocking {
            awaitSecondExerciseOpen()

            // The bug this story fixes: before ADR-0037, the first exercise had no row and
            // nothing to tap once the second became current -- it must be on screen now.
            val firstExerciseRow = compose.onNodeWithText(FIRST_EXERCISE)
            firstExerciseRow.assertIsDisplayed()
            firstExerciseRow.performClick()

            // Proof the tap *selected* it rather than firing a sheet blind: its own empty set
            // list renders, which only the row genuinely open can show (the second exercise,
            // the only other one in this fixture, already has a set and would say so instead).
            awaitFirstExerciseOpen()

            addSetButton().performScrollTo().performClick()
            awaitSheetOpen()
            compose.onNodeWithText("Save set").performClick()
            awaitSheetClosed()

            val logged = sets.observeForSessionExercise(FIRST_ID).first()
            assertEquals(1, logged.size, "the set was written against the exercise switched to")
        }
    }

    private fun addSetButton() = compose.onNodeWithText("Add set", useUnmergedTree = true)

    private fun awaitSheetOpen() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("Save set").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitSheetClosed() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("Save set").fetchSemanticsNodes().isEmpty()
        }
    }

    /** The fixture's own set (`SECOND_ID`'s "Edit set 1") is on screen once the session loads. */
    private fun awaitSecondExerciseOpen() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose
                .onAllNodesWithContentDescription("Edit set 1", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    /** Only the row genuinely open renders "No sets yet" -- see the test's own comment. */
    private fun awaitFirstExerciseOpen() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("No sets yet").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 10_000L
        const val FIRST_EXERCISE = "Bench Dips"
        const val SECOND_EXERCISE = "Ab Crunch Machine"
        val TODAY_SESSION = SessionId("today-switching")
        val FIRST_ID = SessionExerciseId("se-first-switching")
        val SECOND_ID = SessionExerciseId("se-second-switching")
    }
}
