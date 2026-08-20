package com.gymtracker

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.gymtracker.app.MainActivity
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.session.SessionRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * The mechanical form of US-47: no live-heart-rate chip anywhere under the no-op
 * [com.gymtracker.core.domain.health.LiveHeartRateSource] binding (testing-strategy.md §1, the
 * optional-feature suite) — the same shape [HealthSettingsTest] uses for Health Connect.
 *
 * [BuildConfig.OPTIONAL_FEATURES_ENABLED] is baked into `:app` at compile time by
 * `-Pgymtracker.optionalFeatures=off` — this test enforces the absence only in that specific
 * build, via [assumeFalse]. In the default (real-bindings) run it skips rather than asserting on
 * whatever Bluetooth state the CI emulator happens to have, which this test does not control.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LiveHeartRateChipTest {
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
        assumeFalse("only meaningful under the no-op binding", BuildConfig.OPTIONAL_FEATURES_ENABLED)
        runBlocking {
            hilt.inject()
            val member = currentMember.id()
            sessions.observeActiveSession(member).first()?.let { sessions.deleteSession(it.id) }
        }
    }

    @Test
    fun noChipAnywhereOnTrainHome() {
        awaitHome()

        assertNoChip()
    }

    @Test
    fun noChipAnywhereOnSettings() {
        awaitHome()
        compose.onNodeWithText(SETTINGS_BUTTON).performClick()
        awaitSettingsScreen()

        assertNoChip()
    }

    private fun assertNoChip() {
        compose.onAllNodesWithText(BPM_SUBSTRING, substring = true).assertCountEquals(0)
        compose.onAllNodesWithText(SEARCHING_TEXT).assertCountEquals(0)
        compose.onAllNodesWithText(LOST_TEXT).assertCountEquals(0)
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
        const val SETTINGS_BUTTON = "Settings"
        const val SETTINGS_TITLE = "Settings"
        const val BPM_SUBSTRING = "bpm"
        const val SEARCHING_TEXT = "Heart rate: searching…"
        const val LOST_TEXT = "Heart rate: lost"
    }
}
