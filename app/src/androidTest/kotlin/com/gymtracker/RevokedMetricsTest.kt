package com.gymtracker

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.gymtracker.app.MainActivity
import com.gymtracker.core.domain.health.HealthIntegration
import com.gymtracker.core.domain.health.HealthMetricsSource
import com.gymtracker.core.domain.health.HealthStatus
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.SessionMetrics
import com.gymtracker.core.domain.model.WorkoutSession
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
import java.time.Instant
import javax.inject.Inject
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * US-23 on a real device (ADR-0040).
 *
 * Two things the unit suites cannot reach. The first is deterministic and runs under either
 * binding: after clearing, a workout genuinely renders *no* health line — not "not recorded",
 * which under US-22 means a read happened and found nothing. The second drives the dialog
 * itself, and needs the health section to actually be on screen, so it is gated the way
 * `HealthSettingsTest` gates its own opposite assumption.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RevokedMetricsTest {
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

    @Inject
    lateinit var healthMetricsSource: HealthMetricsSource

    @Inject
    lateinit var healthIntegration: HealthIntegration

    @Before
    fun onHomeWithNoWorkoutRunning() {
        runBlocking {
            hilt.inject()
            val member = currentMember.id()
            sessions.observeActiveSession(member).first()?.let { sessions.deleteSession(it.id) }
        }
    }

    @Test
    fun clearingMetricsLeavesNoHealthLineAtAll() {
        runBlocking {
            val member = currentMember.id()
            val id = SessionId("revoke-test-session")
            sessions.startSession(
                WorkoutSession(
                    id = id,
                    userId = member,
                    gymName = null,
                    startedAt = Instant.now().minusSeconds(3600),
                    endedAt = null,
                    metrics = null,
                ),
            )
            sessions.endSession(id, Instant.now())
            sessions.saveMetrics(id, SessionMetrics(120, 160, 300, "health_connect"))
            assertNotNull(sessions.findSession(id)?.metrics, "fixture must start with metrics")

            sessions.clearMetrics(member)

            // Null outright, not SessionMetrics(null, null, null, null). Anything else means
            // metrics_source survived and every cleared workout renders "not recorded" forever.
            assertNull(sessions.findSession(id)?.metrics)
            sessions.deleteSession(id)
        }
    }

    @Test
    fun turningTheToggleOffOffersToDeleteWhatWasImported() {
        assumeTrue(
            "needs the health section on screen, which the no-op binding deliberately hides",
            BuildConfig.OPTIONAL_FEATURES_ENABLED,
        )
        val id = SessionId("revoke-dialog-session")
        runBlocking {
            val member = currentMember.id()
            assumeTrue(
                "needs a device that can use Health Connect",
                healthMetricsSource.status() != HealthStatus.Unavailable,
            )
            sessions.startSession(
                WorkoutSession(
                    id = id,
                    userId = member,
                    gymName = null,
                    startedAt = Instant.now().minusSeconds(3600),
                    endedAt = null,
                    metrics = null,
                ),
            )
            sessions.endSession(id, Instant.now())
            sessions.saveMetrics(id, SessionMetrics(120, 160, 300, "health_connect"))
            // The toggle defaults off (ADR-0038). Without this the tap below would turn it
            // *on*, which offers nothing — the first version of this test failed on device for
            // exactly that reason, plus a label that was not part of the control at all.
            healthIntegration.set(true)
        }

        awaitHome()
        compose.onNodeWithText(SETTINGS_BUTTON).performClick()
        awaitSettingsScreen()
        // testing-strategy.md's trap #1: scroll before touching anything on this screen — the
        // CI emulator is 320x640 and the health section sits well below the fold.
        compose.onNodeWithText(HEALTH_CONNECT_TITLE).performScrollTo()

        compose.onNodeWithText(HEALTH_CONNECT_TITLE).performClick()

        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(DIALOG_TITLE).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(DIALOG_TITLE).assertIsDisplayed()

        compose.onNodeWithText(DELETE).performClick()

        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(DIALOG_TITLE).fetchSemanticsNodes().isEmpty()
        }
        runBlocking {
            assertNull(sessions.findSession(id)?.metrics)
            sessions.deleteSession(id)
            healthIntegration.set(false)
        }
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
        const val HEALTH_CONNECT_TITLE = "Health Connect"
        const val DIALOG_TITLE = "Delete imported health data?"
        const val DELETE = "Delete"
    }
}
