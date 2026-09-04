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
import java.time.Instant
import javax.inject.Inject

/**
 * US-05: "I can dismiss or skip it. It never blocks logging the next set." That promise is kept
 * by `RestSecondaryRow`'s `SKIP REST` button — except `RestingBody` (`RestPanel.kt`) drew it
 * only `if (upNext != null)`, the same guard around `UpNext` and the log button. `upNext` is
 * null whenever nothing is logged in the current session (`DetermineUpNextSet`'s own contract),
 * which a set deleted out from under an already-running rest produces directly: the rest itself
 * does not stop when its set does, but the one control that lets a member leave it does.
 *
 * That is not a hypothetical once M2's sync engine lands — a second household device deleting
 * the same row mid-rest is exactly this shape. It is reproduced here without sync by deleting
 * the set through the same [SetRepository] the app itself writes through, the same technique
 * `RestNotificationTest` and `TwoTapSetLoggingTest` already use to reach a specific state.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RestingWithNoUpNextTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

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

    /** A fresh session with one exercise and nothing logged yet — the same shape US-03's own tests start from. */
    @Before
    fun aFreshSessionWithOneExercise() {
        runBlocking {
            hilt.inject()
            catalogSeeder.seedIfEmpty(Instant.now().toEpochMilli())

            val member = currentMember.id()
            val benchDips = catalog.search(EXERCISE, member).first().first { it.name == EXERCISE }
            val now = Instant.now()

            sessions.deleteSession(TODAY_SESSION)
            restTimerStore.setRestEndsAt(null)

            sessions.startSession(WorkoutSession(TODAY_SESSION, member, null, now, null, null))
            sessionExercises.add(SessionExercise(TODAY, TODAY_SESSION, benchDips.id, 1))
        }
    }

    @Test
    fun skipRestStaysReachableAfterTheOnlySetIsDeletedMidRest() {
        runBlocking {
            // Log the session's first set through the real two-tap sheet (no history to prefill
            // from, so the default 12x1 goes straight through) — this is what starts the rest.
            awaitReadyToLogASet()
            compose.onNodeWithText("Add set", useUnmergedTree = true).performScrollTo().performClick()
            awaitSheetOpen()
            compose.onNodeWithText("Save set").performClick()
            awaitSheetClosed()

            // The set that was just written is the only one in the session — confirm the rest
            // actually started with something to show before breaking it.
            awaitTextOnScreen("UP NEXT")
            val justLogged = sets.observeForSessionExercise(TODAY).first().single()

            // Delete it out from under the running rest. `restTimerStore` is untouched: the rest
            // keeps counting down exactly as it would if a synced device had deleted the row.
            sets.delete(justLogged.id)

            awaitTextGone("UP NEXT")

            // The bug: SKIP REST used to disappear along with UP NEXT, leaving no way out of
            // the resting screen at all short of backgrounding the app.
            compose
                .onNodeWithText("SKIP REST", useUnmergedTree = true)
                .assertExists("SKIP REST must survive upNext going null — US-05's 'I can skip it' has no exception")
                .performClick()

            // Skipping must actually work, not just be present: back to a normal, unblocked
            // screen. `RestController.skip()` clears the timer on its own coroutine, so this
            // polls the same way `awaitTextGone`/`awaitReadyToLogASet` do rather than reading
            // state that has not necessarily settled by the next line.
            awaitTextGone("SKIP REST")
            awaitReadyToLogASet()
        }
    }

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

    private fun awaitReadyToLogASet() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("Add set", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitTextOnScreen(text: String) {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitTextGone(text: String) {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 10_000L
        const val EXERCISE = "Bench Dips"
        val TODAY_SESSION = SessionId("resting-no-upnext-today")
        val TODAY = SessionExerciseId("se-resting-no-upnext-today")
    }
}
