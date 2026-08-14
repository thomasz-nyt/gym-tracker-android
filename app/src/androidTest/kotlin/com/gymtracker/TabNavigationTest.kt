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
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.session.SessionRepository
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
 * ADR-0024's bottom bar, and the bug it shipped with.
 *
 * Home offers "Past workouts" and "Browse exercises" as shortcuts to two places that are also
 * tabs. Reaching a **top-level destination by pushing it** put it on Train's own back stack, and
 * the bar's `popUpTo(start) { saveState } … restoreState` then saved that pushed entry on the
 * way out and restored it on the way back in — so tapping Train from history saved history and
 * immediately put it back. The tab looked dead: History kept the focus and there was no way to
 * return to Train short of killing the app.
 *
 * The rule these tests pin: **a top-level destination is always reached as a tab**, never
 * pushed, wherever the tap comes from. Drill-downs (exercise detail, workout detail, picking an
 * exercise into a running session) are still pushes, and are not what this is about.
 *
 * ADR-0030 moved Routines out of that top-level set entirely — it is a drill-down now, reached
 * from Train's header rather than the bar, and [routinesIsAPushFromTrainsHeaderButtonNotATab]
 * pins that it behaves like one.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TabNavigationTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var sessions: SessionRepository

    @Inject
    lateinit var catalogSeeder: CatalogSeeder

    @Inject
    lateinit var currentMember: CurrentMember

    /**
     * No workout running, so home shows its shortcuts and the bar is visible — ADR-0024 hides
     * the bar during a session, and this test is about the bar.
     */
    @Before
    fun onHomeWithNoWorkoutRunning() {
        runBlocking {
            hilt.inject()
            catalogSeeder.seedIfEmpty(Instant.now().toEpochMilli())
            val member = currentMember.id()
            sessions.observeActiveSession(member).first()?.let { sessions.deleteSession(it.id) }
        }
    }

    @Test
    fun theTrainTabComesBackAfterOpeningPastWorkoutsFromHome() {
        awaitHome()

        compose.onNodeWithText("Past workouts").performClick()
        compose.waitForIdle()
        awaitLeftHome()

        compose.onNodeWithText(TRAIN_TAB).performClick()
        awaitHome()

        compose.onNodeWithText(START).assertIsDisplayed()
    }

    @Test
    fun theTrainTabComesBackAfterOpeningBrowseFromHome() {
        // Same shape as the history shortcut: Exercises is a tab too, so reaching it from home
        // must not bury it in Train's stack.
        awaitHome()

        compose.onNodeWithText("Browse exercises").performClick()
        compose.waitForIdle()
        awaitLeftHome()

        compose.onNodeWithText(TRAIN_TAB).performClick()
        awaitHome()

        compose.onNodeWithText(START).assertIsDisplayed()
    }

    @Test
    fun routinesIsAPushFromTrainsHeaderButtonNotATab() {
        // ADR-0030: Routines dropped out of the bottom bar and is reached only from Train's
        // outlined header button now — a drill-down, exited the same way the browse detail
        // screen is in the test above, not a tab the bar remembers.
        awaitHome()

        compose.onNodeWithText(ROUTINES_BUTTON).performClick()
        awaitLeftHome()

        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(NEW_ROUTINE).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(BACK).performClick()

        awaitHome()
        compose.onNodeWithText(START).assertIsDisplayed()
    }

    @Test
    fun aDetailScreenCanBeLeftWithoutTheSystemBackGesture() {
        // ADR-0024 removed the dead-end "Done" from the drill-downs and left them with no
        // affordance at all: no bar (they are drill-downs) and no up arrow, so an edge swipe was
        // the only exit. Finding 06 was about a button that was the *only* way out, not about a
        // detail screen having a way out at all.
        awaitHome()
        compose.onNodeWithText("Browse exercises").performClick()
        awaitLeftHome()

        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(AN_EXERCISE).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText(AN_EXERCISE)[0].performClick()
        compose.waitForIdle()

        compose.onNodeWithText(BACK).performClick()

        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(SEARCH_FIELD).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(SEARCH_FIELD).assertIsDisplayed()
    }

    private fun awaitHome() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(START).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitLeftHome() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(START).fetchSemanticsNodes().isEmpty()
        }
    }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 10_000L

        /** Only ever on the home screen, so it is the signal that Train is what is showing. */
        const val START = "Start workout"
        const val TRAIN_TAB = "Train"

        /** First alphabetically in the seeded catalog, so it is on screen without scrolling. */
        const val AN_EXERCISE = "Ab Crunch Machine"

        /** Only on the browse screen, so it is the signal that the list is what is showing. */
        const val SEARCH_FIELD = "Search exercises"
        const val BACK = "Back"

        /** Train's one entry point to Routines (ADR-0030), present regardless of routine count. */
        const val ROUTINES_BUTTON = "Routines"

        /** Present on the Routines screen whether or not it has any routines yet. */
        const val NEW_ROUTINE = "New routine"
    }
}
