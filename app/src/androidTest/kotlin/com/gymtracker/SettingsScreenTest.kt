package com.gymtracker

import android.Manifest
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.gymtracker.app.MainActivity
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.session.SessionRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Settings (US-40, US-41, US-42) had no instrumented coverage at all before this file — the one
 * screen in the app to ship without any.
 *
 * [importDataIsReachableByScrolling] pins the defect that motivated it: `SettingsScreen`'s root
 * `Column` had no `verticalScroll`, the only screen in the app in that state (every other long
 * screen either weights a `LazyColumn` or scrolls). At 200% font scale, or on a short emulator
 * profile, "Import data" sat below the fold with nothing to pull it into view — exactly
 * `testing-strategy.md`'s trap #1 ("a node in the tree is not a node on screen"), except here
 * there was no scrollable ancestor for `performScrollTo()` to find at all, so the call itself
 * threw rather than landing on a clipped node. `performScrollTo()` is not an interaction, so it
 * does not count against any two-tap budget.
 *
 * Neither test drives the system file picker `Export data`/`Import data` launch — that UI is
 * outside the app process and outside what Compose's test APIs can see, so this only asserts the
 * controls are reached and enabled, not that a real export or import completes end to end (that
 * is covered on-device, per the M3c roadmap entry).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var sessions: SessionRepository

    @Inject
    lateinit var currentMember: CurrentMember

    @Before
    fun onHomeWithNoWorkoutRunning() {
        runBlocking {
            hilt.inject()
            val member = currentMember.id()
            sessions.observeActiveSession(member).first()?.let { sessions.deleteSession(it.id) }
        }
    }

    @Test
    fun importDataIsReachableByScrolling() {
        awaitHome()
        compose.onNodeWithText(SETTINGS_BUTTON).performClick()
        awaitSettingsScreen()

        // Would throw here — "Failed to perform scroll on the node … no scrollable parent could
        // be found" — before the root Column carried verticalScroll.
        compose.onNodeWithText(IMPORT_DATA).performScrollTo()

        compose.onNodeWithText(IMPORT_DATA).assertIsDisplayed()
        compose.onNodeWithText(IMPORT_DATA).assertIsEnabled()
    }

    @Test
    fun exportDataIsReachableByScrolling() {
        awaitHome()
        compose.onNodeWithText(SETTINGS_BUTTON).performClick()
        awaitSettingsScreen()

        compose.onNodeWithText(EXPORT_DATA).performScrollTo()

        compose.onNodeWithText(EXPORT_DATA).assertIsDisplayed()
        compose.onNodeWithText(EXPORT_DATA).assertIsEnabled()
    }

    @Test
    fun defaultRestFieldDoesNotOfferAnActionItSilentlyIgnores() {
        // The field's own comment already says typing was never wired to anything — a rest
        // default is small and bounded, and the +/- steppers cover the whole range US-05 needs.
        // The bug is that it still *rendered* as an ordinary editable OutlinedTextField, cursor
        // and all, inviting a tap-to-type that would then do nothing. This asserts the field is
        // now honestly read-only rather than only behaviourally so.
        awaitHome()
        compose.onNodeWithText(SETTINGS_BUTTON).performClick()
        awaitSettingsScreen()

        compose.onNodeWithText(DEFAULT_REST_VALUE).assert(hasSetTextAction().not())
    }

    @Test
    fun liveHeartRateRowIsOperableByItsLabel() {
        // Found by the 2026-09-04 review. `HealthSection`'s row was made `toggleable` for US-23
        // and its own comment named this row as carrying "the identical defect", left for M7's
        // sweep: only a precise tap on the Switch itself did anything, and TalkBack read an
        // unnamed "off, switch" beside a label that was not part of it. Fixed the same way.
        awaitHome()
        compose.onNodeWithText(SETTINGS_BUTTON).performClick()
        awaitSettingsScreen()

        // The section is absent by design below API 31 or with no Bluetooth adapter (US-46);
        // that absence is its own rule, asserted elsewhere, not something to fail here.
        val rows = compose.onAllNodesWithText(LIVE_HEART_RATE).fetchSemanticsNodes()
        assumeTrue("no live-heart-rate section on this device, so no row to check", rows.isNotEmpty())

        compose.onNodeWithText(LIVE_HEART_RATE).performScrollTo().assert(isToggleable())
    }

    @Test
    fun backReturnsToTrainHome() {
        awaitHome()
        compose.onNodeWithText(SETTINGS_BUTTON).performClick()
        awaitSettingsScreen()

        compose.onNodeWithText(BACK).performClick()

        awaitHome()
        compose.onNodeWithText(START).assertIsDisplayed()
    }

    private fun awaitHome() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(START).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitSettingsScreen() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(SETTINGS_TITLE).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 10_000L
        const val START = "Start workout"

        /** Train's one entry point to Settings (US-42, ADR-0030's drill-down shape). */
        const val SETTINGS_BUTTON = "Settings"

        /** The screen's own title, distinct from the header button that opens it. */
        const val SETTINGS_TITLE = "Settings"
        const val EXPORT_DATA = "Export data"
        const val IMPORT_DATA = "Import data"
        const val LIVE_HEART_RATE = "Live heart rate"
        const val BACK = "Back"

        /** DataStoreRestTimerStore's own default (US-05), read fresh in the isolated test DB. */
        const val DEFAULT_REST_VALUE = "60"
    }
}
