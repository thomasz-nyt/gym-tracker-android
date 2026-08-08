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
    }
}
