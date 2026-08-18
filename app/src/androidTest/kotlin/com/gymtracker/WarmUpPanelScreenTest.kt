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
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.rest.RestTimerStore
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
import java.time.Instant
import javax.inject.Inject

/**
 * `Redesign.dc.html` Turn 3, finding 01, and its fix, frame `3a`: the warm-up row used to ask
 * `Done` to fit in −14dp beside a `displayLarge` (104sp) countdown and an inline `RepMascot` —
 * numbers no unit test caught, because none rendered the panel at all. This is the first
 * instrumented coverage to reach it, and it runs on CI's own 320x640 emulator
 * (`testing-strategy.md`'s own note), which is the narrowest device the overflow was ever
 * measured against.
 *
 * What this proves: `Done` is on screen and clickable while the countdown is running, not merely
 * present somewhere in the semantics tree — `assertIsDisplayed()` fails on a node clipped to zero
 * width the same way the old `Row` clipped it, and the click that follows only succeeds if the
 * tap actually lands (`testing-strategy.md`'s "a node in the tree is not a node on screen").
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WarmUpPanelScreenTest {
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
    lateinit var catalogSeeder: CatalogSeeder

    @Inject
    lateinit var currentMember: CurrentMember

    @Inject
    lateinit var restTimerStore: RestTimerStore

    @Before
    fun activeSessionWithNoWarmUpYet() {
        runBlocking {
            hilt.inject()
            catalogSeeder.seedIfEmpty(Instant.now().toEpochMilli())

            val member = currentMember.id()
            // As in CorrectingASetTest: the app database is a real file shared by every test on
            // the device, and US-01 allows one active session per member.
            sessions.observeActiveSession(member).first()?.let { sessions.deleteSession(it.id) }
            sessions.deleteSession(TODAY_SESSION)
            restTimerStore.setRestEndsAt(null)

            sessions.startSession(WorkoutSession(TODAY_SESSION, member, null, Instant.now(), null, null))
        }
    }

    @After
    fun discardTheSession() {
        runBlocking { sessions.deleteSession(TODAY_SESSION) }
    }

    @Test
    fun doneStaysReachableWhileTheWarmUpIsRunning() {
        runBlocking {
            awaitStartWarmUpButton()
            compose.onNodeWithText("Start warm-up").performClick()
            awaitWarmUpRunning()

            val done = compose.onNodeWithText("Done")
            done.assertIsDisplayed()
            done.performClick()

            awaitWarmUpStopped()
        }
    }

    private fun awaitStartWarmUpButton() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("Start warm-up").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** `EyebrowLabel` uppercases at the call site, so the semantics tree holds `"WARM-UP"`. */
    private fun awaitWarmUpRunning() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose
                .onAllNodesWithText("WARM-UP", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun awaitWarmUpStopped() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("Start warm-up").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 10_000L
        val TODAY_SESSION = SessionId("today-warmup-panel")
    }
}
