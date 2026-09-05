package com.gymtracker

import android.Manifest
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.gymtracker.app.MainActivity
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.KeepScreenOnPreference
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * US-59: the screen stays on while a workout runs, and only then.
 *
 * `KeepScreenOn` asks through [View.setKeepScreenOn] on the Compose host view, so the check here
 * is the same property read back off the view tree — not the window flag, which the view system
 * applies on its own schedule and which a test cannot reliably observe from outside a layout pass.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class KeepScreenOnTest {
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
    lateinit var currentMember: CurrentMember

    @Inject
    lateinit var keepScreenOn: KeepScreenOnPreference

    @Before
    fun onHomeWithNoWorkoutRunning() {
        runBlocking {
            hilt.inject()
            val member = currentMember.id()
            sessions.observeActiveSession(member).first()?.let { sessions.deleteSession(it.id) }
            keepScreenOn.set(true)
        }
    }

    /** Leaves no active session behind, for the same reason `CorrectingASetTest`'s does. */
    @After
    fun discardTheSession() {
        runBlocking {
            val member = currentMember.id()
            sessions.observeActiveSession(member).first()?.let { sessions.deleteSession(it.id) }
        }
    }

    @Test
    fun theScreenIsHeldOnOnlyWhileAWorkoutRuns() {
        awaitHome()
        assertFalse(screenIsHeldOn(), "no workout, no hold")

        compose.onNodeWithText(START).performClick()
        awaitSession()
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) { screenIsHeldOn() }
        assertTrue(screenIsHeldOn(), "a running workout holds the screen on")

        compose.onNodeWithText(FINISH).performClick()
        compose.onNodeWithText(CONFIRM_FINISH).performClick()

        // Released the moment the session ends — before the finish summary is dismissed, since
        // the summary is not a workout running.
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) { !screenIsHeldOn() }
        assertFalse(screenIsHeldOn(), "a finished workout releases it")
    }

    @Test
    fun withTheSettingOffAWorkoutDoesNotHoldTheScreen() {
        runBlocking { keepScreenOn.set(false) }
        awaitHome()

        compose.onNodeWithText(START).performClick()
        awaitSession()
        compose.waitForIdle()

        assertFalse(screenIsHeldOn(), "the member turned the hold off, so a workout must not take it")
    }

    private fun awaitHome() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(START).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitSession() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(FINISH).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Whether any view in the activity's tree is asking for the screen to stay on. */
    private fun screenIsHeldOn(): Boolean {
        val decorView = compose.activity.window.decorView
        return compose.runOnUiThread { decorView.holdsScreenOn() }
    }

    private fun View.holdsScreenOn(): Boolean =
        keepScreenOn || (this is ViewGroup && (0 until childCount).any { getChildAt(it).holdsScreenOn() })

    private companion object {
        const val READY_TIMEOUT_MILLIS = 10_000L
        const val START = "Start workout"

        /** The session header's finish control (ADR-0029), and its confirm dialog's own button. */
        const val FINISH = "FINISH"
        const val CONFIRM_FINISH = "Finish workout"
    }
}
