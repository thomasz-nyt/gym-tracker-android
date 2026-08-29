package com.gymtracker

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.gymtracker.app.MainActivity
import com.gymtracker.core.data.exercise.CatalogSeeder
import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.WorkoutSession
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
import kotlin.test.assertTrue

/**
 * ADR-0044 / US-52, `Redesign.dc.html` Turn 5 file `01-insets-and-spacing.md`, gate 1.1: "status
 * bar bottom -> screen title top, session screen, 393dp, expected <= 40dp." File `01`'s own step
 * 0 says to reproduce the claimed ~210dp double-inset before changing anything, and to say so if
 * the diagnosis is wrong — this test is that reproduction, not an assumed fix. A source grep
 * (see ADR-0044) already found no second `statusBarsPadding()`/`windowInsetsPadding(statusBars)`
 * call anywhere in the feature modules' main source tree; this measures the real, composed
 * layout instead of trusting that absence to mean the gap is small, since `Scaffold`'s own
 * default `contentWindowInsets` could still double-count against `LiveHeartRateChip`'s `topBar`
 * slot in a way no grep would show.
 *
 * **Not one test per screen.** `WindowInsets.statusBars` is consumed in exactly one place —
 * `GymTrackerNavHost`'s root `Scaffold` — shared by every destination in the nav graph (ADR-0044).
 * There is no per-screen inset logic to differ between the session screen, Progress, or a history
 * detail screen, so one measurement against that shared mechanism stands for all three gate rows
 * (1.1-1.3); the roadmap entry for this story records that reasoning rather than three near-
 * identical instrumented tests against one Scaffold.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class InsetConsumptionTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var sessions: SessionRepository

    @Inject
    lateinit var catalog: ExerciseCatalog

    @Inject
    lateinit var catalogSeeder: CatalogSeeder

    @Inject
    lateinit var currentMember: CurrentMember

    @Before
    fun cleanSlate() {
        runBlocking {
            hilt.inject()
            catalogSeeder.seedIfEmpty(Instant.now().toEpochMilli())
            val member = currentMember.id()
            sessions.observeActiveSession(member).first()?.let { sessions.deleteSession(it.id) }
            sessions.deleteSession(TODAY_SESSION)
        }
    }

    @Test
    fun theSessionTitleSitsWithin40dpOfTheStatusBar() {
        runBlocking {
            val member = currentMember.id()
            sessions.startSession(WorkoutSession(TODAY_SESSION, member, null, Instant.now(), null, null))

            compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
                compose.onAllNodesWithTag(SESSION_TITLE_TEST_TAG).fetchSemanticsNodes().isNotEmpty()
            }

            val density = compose.density
            val titleTopPx =
                compose
                    .onNodeWithTag(SESSION_TITLE_TEST_TAG)
                    .fetchSemanticsNode()
                    .positionInRoot.y

            val statusBarTopPx =
                ViewCompat
                    .getRootWindowInsets(compose.activity.window.decorView)
                    ?.getInsets(WindowInsetsCompat.Type.statusBars())
                    ?.top
                    ?.toFloat()
                    ?: error("No root window insets available — is the activity attached?")

            val gapDp = (titleTopPx - statusBarTopPx) / density.density

            assertTrue(
                gapDp <= 40f,
                "Status bar bottom to session title top measured ${gapDp}dp, expected <= 40dp " +
                    "(titleTopPx=$titleTopPx, statusBarTopPx=$statusBarTopPx, density=${density.density})",
            )
        }
    }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 10_000L

        /** Matches `SessionScaffold.kt`'s private `SESSION_TITLE_TEST_TAG` — see that file's own comment. */
        const val SESSION_TITLE_TEST_TAG = "session-title"

        val TODAY_SESSION = SessionId("today-inset-consumption")
    }
}
