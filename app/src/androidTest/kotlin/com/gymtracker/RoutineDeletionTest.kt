package com.gymtracker

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.gymtracker.app.MainActivity
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.routine.RoutineRepository
import com.gymtracker.core.domain.session.SessionRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * ADR-0019 on the Routines tab: a destructive control never shares a surface with a
 * constructive one. "Delete routine" used to sit on the same list row as "Start", forcing the
 * row onto a second line to fit it (redesign audit finding 04). It now lives in the routine
 * editor instead.
 *
 * The two rules this pins: the Routines list never renders "Delete routine" at all, and
 * deleting from inside the editor returns to a list that no longer has the routine in it.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RoutineDeletionTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    /** As in `TwoTapSetLoggingTest`: US-05's one-time prompt must not cover the screen. */
    @get:Rule(order = 1)
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var routines: RoutineRepository

    @Inject
    lateinit var sessions: SessionRepository

    @Inject
    lateinit var currentMember: CurrentMember

    @Before
    fun aRoutineToDelete() {
        runBlocking {
            hilt.inject()
            val member = currentMember.id()
            // US-01 allows one active session; a device-shared test database might have one
            // left over from another test, and it would keep Train from showing the bar this
            // test needs to reach the Routines tab.
            sessions.observeActiveSession(member).first()?.let { sessions.deleteSession(it.id) }
            routines.add(Routine(ROUTINE_ID, member, ROUTINE_NAME, 1))
        }
    }

    @After
    fun discardTheRoutine() {
        runBlocking { routines.delete(ROUTINE_ID) }
    }

    @Test
    fun theRoutinesListNeverRendersDeleteRoutine() {
        awaitRoutinesTab()

        compose.onNodeWithText(ROUTINES_TAB).performClick()
        awaitRoutineOnScreen()

        assertEqualsZeroNodes("Delete routine", "Delete routine must not render on the list row")
    }

    @Test
    fun deletingFromTheEditorReturnsToAListWithoutTheRoutine() {
        awaitRoutinesTab()
        compose.onNodeWithText(ROUTINES_TAB).performClick()
        awaitRoutineOnScreen()

        // Content-described with the routine's own name — a device shared with other tests
        // (or another routine of the same name) could otherwise make plain "Edit" ambiguous.
        compose.onNodeWithContentDescription("Edit $ROUTINE_NAME").performClick()
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("Delete routine").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Delete routine").performClick()

        // A device-shared database may hold other routines, so "the list is now empty" is not
        // a safe signal — only that this one is gone, and that navigation landed back on a
        // screen that still offers to make another (present regardless of list content, unlike
        // "Routines", which by now matches both the screen title and the tab).
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(ROUTINE_NAME).fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithText("New routine").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("New routine").assertIsDisplayed()
    }

    private fun assertEqualsZeroNodes(
        text: String,
        message: String,
    ) {
        compose.waitForIdle()
        kotlin.test.assertEquals(0, compose.onAllNodesWithText(text).fetchSemanticsNodes().size, message)
    }

    private fun awaitRoutinesTab() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(ROUTINES_TAB).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitRoutineOnScreen() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(ROUTINE_NAME).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 10_000L
        const val ROUTINES_TAB = "Routines"
        const val ROUTINE_NAME = "Delete Me Routine"
        val ROUTINE_ID = RoutineId("routine-to-delete")
    }
}
