package com.gymtracker

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import kotlin.test.assertNull

/**
 * US-60: RPE is one tap to record and reads back wherever the set is shown.
 *
 * Same fixture shape as `OneTapSetLoggingTest` — a prior week's set for the same exercise — with
 * one addition: that set carries an RPE, so the rest panel has an effort to read back beside the
 * number to beat. `TwoTapSetLoggingTest` stays unedited: the chips add nothing to `Add set` →
 * `Save set`, and this test's three-tap path (a chip in between) is the optional extra US-03
 * always allowed for.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RpeChipsTest {
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
    fun startedWorkoutWithAnEffortOnRecord() {
        runBlocking {
            hilt.inject()
            catalogSeeder.seedIfEmpty(Instant.now().toEpochMilli())

            val member = currentMember.id()
            val exercise = catalog.search(EXERCISE, member).first().first { it.name == EXERCISE }
            val now = Instant.now()

            sessions.observeActiveSession(member).first()?.let { sessions.deleteSession(it.id) }
            listOf(LAST_WEEK, TODAY_SESSION).forEach { sessions.deleteSession(it) }
            restTimerStore.setRestEndsAt(null)
            // On this emulator image, RestController.shouldAskForNotifications() still triggers
            // Android's system permission dialog on the first rest of a session even though
            // GrantPermissionRule has already granted it at the OS level — the check reads an
            // app-level "have we asked" flag, not the OS grant state. That dialog is a different
            // window the Compose semantics tree cannot see past. Marking it already-asked skips
            // the request path entirely, the state a real member's second-ever workout is in.
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
            val previously = SessionExerciseId("se-last-week-rpe")
            sessionExercises.add(SessionExercise(previously, LAST_WEEK, exercise.id, 1))
            // 61.23 kg is exactly 135 lb (ADR-0008); the @8 is what the rest panel reads back.
            sets.add(
                ExerciseSet(
                    id = "set-last-week-rpe",
                    sessionExerciseId = previously,
                    setIndex = 1,
                    weightKg = 61.23,
                    reps = 8,
                    rpe = LAST_WEEK_RPE,
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
            sessions.deleteSession(TODAY_SESSION)
            sessions.deleteSession(LAST_WEEK)
        }
    }

    @Test
    fun anRpeIsOneTapInTheSheetAndReadsBackOnTheRestPanelAndTheRow() {
        runBlocking {
            awaitReadyToLogASet()
            compose.onNodeWithText(ADD_SET).performScrollTo().performClick()
            awaitSheet()

            // "@8.5", not "@8": the sheet's reps field reads "8", and this is a chip test, not a
            // disambiguation exercise. Scrolled to first — eleven chips wrap the sheet's FlowRow
            // onto more than one line, and a chip below the fold needs the same performScrollTo()
            // "Add set" itself needs in a short `LazyColumn` (see addSetButton() there): found on
            // CI's emulator, once #81 made this test actually run — a chip that requires scrolling
            // to reach does not register a tap without it, silently. Persisted as 8.5, spelled
            // back as "@8.5".
            compose.onNodeWithText(CHOSEN_RPE).performScrollTo().performClick()
            compose.onNodeWithText(SAVE_SET).performClick()

            compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
                runBlocking { sets.observeForSessionExercise(TODAY).first().isNotEmpty() }
            }
            assertEquals(8.5, todaysRpe(), "one tap recorded it")

            // Resting now (ADR-0029): the comparison line carries last week's effort beside its
            // load and reps — the number to beat, with how hard it was.
            compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
                compose
                    .onAllNodesWithText(LAST_WEEK_READ_BACK, substring = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithText(LAST_WEEK_READ_BACK, substring = true).assertIsDisplayed()

            // Back on the set list, the row reads it back too.
            compose.onNodeWithText(SKIP_REST).performClick()
            compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
                compose.onAllNodesWithText(CHOSEN_READ_BACK).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(CHOSEN_READ_BACK).assertIsDisplayed()
        }
    }

    @Test
    fun anRpeIsCorrectedWithOneTapAndClearedWithASecond() {
        runBlocking {
            // A set already logged today, with no effort recorded — seeded rather than tapped, the
            // same way `CorrectingASetTest` builds its fixture.
            sets.add(ExerciseSet(TODAY_SET, TODAY, 1, 61.23, 8, null, Instant.now()))
            compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
                compose.onAllNodesWithContentDescription(EDIT_SET_1).fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithContentDescription(EDIT_SET_1).performClick()
            awaitEditor()
            // Scrolled to first — see the note on the "@8.5" tap above; the same chip row wraps
            // in the editor sheet too.
            compose.onNodeWithText(CORRECTED_RPE).performScrollTo().performClick()
            compose.onNodeWithText(SAVE_CHANGES).performClick()

            compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
                runBlocking { todaysRpe() == 9.0 }
            }
            compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
                compose.onAllNodesWithText(CORRECTED_READ_BACK).fetchSemanticsNodes().isNotEmpty()
            }

            // Tapping the selected chip again clears it: not recorded, not "easy" (constitution §2.4).
            compose.onNodeWithContentDescription(EDIT_SET_1).performClick()
            awaitEditor()
            compose.onNodeWithText(CORRECTED_RPE).performScrollTo().performClick()
            compose.onNodeWithText(SAVE_CHANGES).performClick()

            compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
                runBlocking { todaysRpe() == null }
            }
            assertNull(todaysRpe(), "a second tap clears it")
            compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
                compose.onAllNodesWithText(CORRECTED_READ_BACK).fetchSemanticsNodes().isEmpty()
            }
            compose.onAllNodesWithText(CORRECTED_READ_BACK).assertCountEquals(0)
        }
    }

    /** The one set logged today, and how hard it was recorded as. */
    private suspend fun todaysRpe(): Double? =
        sets
            .observeForSessionExercise(TODAY)
            .first()
            .single()
            .rpe

    private fun awaitReadyToLogASet() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(ADD_SET).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitSheet() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(SAVE_SET).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitEditor() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(SAVE_CHANGES).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 10_000L
        const val EXERCISE = "Bench Dips"
        const val ADD_SET = "Add set"
        const val SAVE_SET = "Save set"
        const val SAVE_CHANGES = "Save changes"
        const val SKIP_REST = "SKIP REST"
        const val EDIT_SET_1 = "Edit set 1"

        const val LAST_WEEK_RPE = 8.0

        /** The tail of the rest panel's comparison line: load, reps and last week's effort. */
        const val LAST_WEEK_READ_BACK = "× 8  @8"
        const val CHOSEN_RPE = "@8.5"
        const val CHOSEN_READ_BACK = "@8.5"
        const val CORRECTED_RPE = "@9"
        const val CORRECTED_READ_BACK = "@9"

        val LAST_WEEK = SessionId("last-week-rpe")
        val TODAY_SESSION = SessionId("today-rpe")
        val TODAY = SessionExerciseId("se-today-rpe")
        const val TODAY_SET = "set-today-rpe"
    }
}
