package com.gymtracker

import android.Manifest
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
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
 * `specs/testing-strategy.md` §2, the two-tap assertion:
 *
 * > An instrumented test that opens the app with an active session and a prior set for the
 * > exercise, then asserts the set is persisted after at most two interactions. Treat a
 * > regression here as a broken build, not a nit.
 *
 * The count is enforced structurally rather than by a counter: this test performs exactly two
 * `performClick` calls before asserting. Adding a third interaction to the path — a unit
 * picker, a confirm step, an "are you sure" — means editing this test, which is the point.
 *
 * Everything is set up through the same interfaces the app uses, so the test cannot pass by
 * reaching around the production path.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TwoTapSetLoggingTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    /**
     * Granted up front so US-05's one-time notification prompt never appears.
     *
     * Without this the dialog opens the moment the first rest starts — which is the moment a
     * set is saved — and the next test finds a system window over the app instead of the
     * session. It passed locally, where the permission had already been answered, and failed
     * on CI's clean emulator. This test is about logging a set in two taps, not about the
     * permission flow.
     */
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
     * Puts the member mid-workout with a prior set already logged: someone between sets with
     * the phone in their hand, which is the state the criterion describes.
     */
    @Before
    fun startedWorkoutWithHistory() {
        runBlocking {
            hilt.inject()
            // HiltTestApplication does not run the real Application, so seed explicitly.
            catalogSeeder.seedIfEmpty(Instant.now().toEpochMilli())

            val member = currentMember.id()
            val benchPress = catalog.search(EXERCISE, member).first().first { it.name == EXERCISE }
            val now = Instant.now()

            // The app database is a real file that outlives a single test method, so start from
            // a known state. Discarding a session cascades to its exercises and sets.
            listOf(LAST_WEEK, TODAY_SESSION).forEach { sessions.deleteSession(it) }

            val lastWeek = LAST_WEEK
            sessions.startSession(
                WorkoutSession(
                    id = lastWeek,
                    userId = member,
                    gymName = null,
                    startedAt = now.minus(Duration.ofDays(7)),
                    endedAt = now.minus(Duration.ofDays(7)).plus(Duration.ofHours(1)),
                    metrics = null,
                ),
            )
            val previously = SessionExerciseId("se-last-week")
            sessionExercises.add(SessionExercise(previously, lastWeek, benchPress.id, 1))
            sets.add(
                ExerciseSet(
                    id = "set-last-week",
                    sessionExerciseId = previously,
                    setIndex = 1,
                    // 61.23 kg is exactly 135 lb, the unit this household reads (ADR-0008).
                    weightKg = 61.23,
                    reps = 8,
                    rpe = null,
                    performedAt = now.minus(Duration.ofDays(7)),
                ),
            )

            val today = TODAY_SESSION
            sessions.startSession(WorkoutSession(today, member, null, now, null, null))
            sessionExercises.add(SessionExercise(TODAY, today, benchPress.id, 1))
        }
    }

    @Test
    fun aSetIsPersistedAfterTwoTaps() {
        runBlocking {
            awaitReadyToLogASet()

            // Tap 1 — open set entry. It arrives prefilled from last week.
            // ListItem merges its descendants' semantics, so the button lives in the unmerged tree.
            compose.onNodeWithText("Add set", useUnmergedTree = true).performClick()
            awaitSheetOpen()

            // The whole reason two taps is possible: last week's numbers are already there.
            compose.onNodeWithText("135").assertExists("weight should be prefilled from last week")
            compose.onNodeWithText("8").assertExists("reps should be prefilled from last week")

            // Tap 2 — confirm. Nothing is typed: the prefilled values were already right.
            compose.onNodeWithText("Save set").performClick()
            awaitSheetClosed()

            val logged = sets.observeForSessionExercise(TODAY).first()
            assertEquals(1, logged.size, "one set, logged in two taps")
            assertEquals(61.23, logged.single().weightKg, "prefilled weight, unchanged")
            assertEquals(8, logged.single().reps, "prefilled reps, unchanged")
        }
    }

    @Test
    fun theSetIsOnDiskBeforeTheSheetCloses() {
        runBlocking {
            // US-03: "persisted locally before any UI transition. Killing the app immediately
            // after does not lose it." LogSet is awaited before the sheet closes, so once the
            // dialog is gone the row is already committed — there is no window to lose it in.
            awaitReadyToLogASet()
            // ListItem merges its descendants' semantics, so the button lives in the unmerged tree.
            compose.onNodeWithText("Add set", useUnmergedTree = true).performClick()
            awaitSheetOpen()
            compose.onNodeWithText("Save set").performClick()
            awaitSheetClosed()

            assertNotNull(
                sets.observeForSessionExercise(TODAY).first().singleOrNull(),
                "the sheet closed, so the set must already be on disk",
            )
        }
    }

    /**
     * Waits for the entry sheet to appear, which is the same synchronisation problem as
     * [awaitSheetClosed] at the other end of the sheet's life.
     *
     * `SetEntryController.open` reads the unit preference and then the member's last set of
     * this exercise — a Room query — and only then sets the state the sheet renders from. So
     * the sheet cannot appear un-prefilled: **the sheet being up is itself the proof that the
     * prefill landed.** What `waitForIdle` does not do is wait for that coroutine; it
     * synchronises Compose, and a suspend read is not Compose. Asserting straight after it
     * therefore looked for "135" before the sheet existed — which passed on a fast machine
     * and failed on CI's emulator, on every PR from #19 onward.
     *
     * This adds no interaction, so US-03's two-tap count is untouched: the test still performs
     * exactly two `performClick` calls before asserting.
     */
    private fun awaitSheetOpen() {
        try {
            compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
                compose.onAllNodesWithText("Save set").fetchSemanticsNodes().isNotEmpty()
            }
        } catch (timeout: ComposeTimeoutException) {
            // TEMPORARY DIAGNOSTIC: this reproduces only on CI, so the tree has to come from
            // CI. Remove once the cause is known.
            throw AssertionError("sheet never opened. Tree was:\n${compose.onRoot().printToString()}", timeout)
        }
    }

    /**
     * Waits for the entry sheet to close, which is the production guarantee being tested:
     * `LogSet` is awaited before the sheet closes, so once it is gone the row is committed.
     *
     * `waitForIdle` is not enough — it synchronises Compose, not the ViewModel coroutine
     * doing the Room write. Asserting straight after it read the database before the write
     * landed, which passed most of the time and failed on CI.
     */
    private fun awaitSheetClosed() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("Save set").fetchSemanticsNodes().isEmpty()
        }
    }

    /**
     * The activity launches when the compose rule applies, which is before `@Before` seeds the
     * database. The screen therefore starts on "no workout" and catches up when Room emits, so
     * the test waits for the state it is actually about rather than assuming it is there.
     */
    private fun awaitReadyToLogASet() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("Add set", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 10_000L
        const val EXERCISE = "Bench Dips"
        val LAST_WEEK = SessionId("last-week")
        val TODAY_SESSION = SessionId("today")
        val TODAY = SessionExerciseId("se-today")
    }
}
