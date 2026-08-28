package com.gymtracker

import android.Manifest
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.gymtracker.app.MainActivity
import com.gymtracker.core.data.exercise.CatalogSeeder
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.model.ExerciseSet
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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * ADR-0011's Turn 4 amendment, section 3's "the part that stops it coming back": two of the
 * pass's own regression tests, run on CI's real 320x640 emulator
 * (`testing-strategy.md`) — the narrowest device the redesign's wraps were ever measured
 * against, the same reasoning `WarmUpPanelScreenTest` (Turn 3's own precedent) already gives.
 *
 * Both assertions cover the 320dp-width half of the amendment's own "320dp × 1.3 font scale"
 * preview rule. The 1.3× font-scale half is **not** covered here: `MainActivity`'s `setContent`
 * is fixed, and there is no supported way to override the system font scale for a real,
 * dependency-injected, navigation-graph-driven Activity from an instrumented test without
 * replacing the Activity under test — `WarmUpPanelScreenTest` doesn't attempt it either, for the
 * same reason. That half is what the amendment's `@Preview(widthDp = 320, fontScale = 1.3f)`
 * previews exist for instead; the two mechanisms are complementary, not redundant, matching the
 * two separate bullets the amendment's "how to stop this coming back" section names.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TurnFourLayoutTest {
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

    @Before
    fun cleanSlate() {
        runBlocking {
            hilt.inject()
            catalogSeeder.seedIfEmpty(Instant.now().toEpochMilli())
            val member = currentMember.id()
            sessions.observeActiveSession(member).first()?.let { sessions.deleteSession(it.id) }
            sessions.deleteSession(TODAY_SESSION)
            sessions.deleteSession(LAST_WEEK)
            restTimerStore.setRestEndsAt(null)
        }
    }

    @After
    fun discardTheSession() {
        runBlocking {
            sessions.deleteSession(TODAY_SESSION)
            sessions.deleteSession(LAST_WEEK)
        }
    }

    /**
     * Frame `4a`: the picker row's floor, retuned 88dp → 80dp by
     * [GymDimens.CatalogRowHeight]. A row with a short, single-line name and equipment label —
     * [SHORT_EXERCISE] — has no content reason to grow past that floor, so its rendered height
     * pins the floor exactly. A regression that reintroduced the old unbounded name or the
     * variable-width "Added" label (both of which used to push the row past 88dp for names far
     * shorter than this one) would push this short row's height up too, failing the equality.
     *
     * Reached from Home's "Browse exercises" shortcut — a tab, not a push
     * ([TabNavigationTest] pins that distinction — irrelevant here, just the fastest way in) —
     * then filtered to one row via the search field, so this test does not depend on the
     * catalog's own sort order or on `LazyColumn` scrolling to find the target row.
     */
    @Test
    fun thePickerRowDoesNotGrowPastItsFloor() {
        runBlocking {
            awaitHome()
            compose.onNodeWithText("Browse exercises").performClick()

            awaitSearchField()
            compose.onNodeWithText("Search exercises").performTextInput(SHORT_EXERCISE)
            awaitFilteredToOneResult()

            compose.onNodeWithText(SHORT_EXERCISE).assertHeightIsEqualTo(GymDimens.CatalogRowHeight)
        }
    }

    /**
     * Frame `4c`: `RestingBody`'s log button used to read `"LOG SET n — DON'T WAIT"` as its
     * eyebrow over a three-unit sentence detail line — the longest string on the resting screen,
     * on a panel that had already spent 74dp of its own height on a metadata sentence set in
     * display weight. `assertIsDisplayed()` is the same check `WarmUpPanelScreenTest` already
     * established for this class of bug: it fails on a node clipped to zero size or pushed off
     * the bottom of a 320×640 viewport, not merely one present somewhere in the semantics tree.
     *
     * Reaches a real resting state through the production path — two taps to log a set, the
     * same sequence [TwoTapSetLoggingTest] performs — rather than poking [RestTimerStore]
     * directly, so [UpNextSet][com.gymtracker.core.domain.rest.UpNextSet]'s own derived fields
     * (the next set number, the prefill) come from the same computation a real session produces
     * rather than a hand-built fixture that could drift from it.
     */
    @Test
    fun theRestPanelsLogButtonStaysOnScreen() {
        runBlocking {
            val member = currentMember.id()
            val exercise = catalog.search(REST_EXERCISE, member).first().first { it.name == REST_EXERCISE }
            val now = Instant.now()

            // A prior set from last week, purely for the "Add set" sheet's own prefill —
            // TwoTapSetLoggingTest's exact fixture shape. Nothing is logged in TODAY_SESSION
            // yet, so the set the test logs below is the session's first: DetermineUpNextSet
            // returns setNumber = alreadyLogged(0) + 1 = 2 for the one still to come after it.
            sessions.deleteSession(LAST_WEEK)
            sessions.startSession(
                WorkoutSession(LAST_WEEK, member, null, now.minus(Duration.ofDays(7)), null, null),
            )
            val lastWeekAppearance = SessionExerciseId("se-turn4-layout-last-week")
            sessionExercises.add(SessionExercise(lastWeekAppearance, LAST_WEEK, exercise.id, 1))
            sets.add(
                ExerciseSet(
                    id = "set-turn4-layout-last-week",
                    sessionExerciseId = lastWeekAppearance,
                    setIndex = 1,
                    weightKg = 61.23,
                    reps = 8,
                    rpe = null,
                    performedAt = now.minus(Duration.ofDays(7)),
                ),
            )

            sessions.startSession(WorkoutSession(TODAY_SESSION, member, null, now, null, null))
            sessionExercises.add(SessionExercise(SessionExerciseId("se-turn4-layout"), TODAY_SESSION, exercise.id, 1))

            awaitReadyToLogASet()
            addSetButton().performClick()
            awaitSheetOpen()
            compose.onNodeWithText("Save set").performClick()
            awaitResting()

            compose.onNodeWithText("LOG SET 2", substring = true).assertIsDisplayed()
        }
    }

    private fun awaitHome() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("Browse exercises").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitSearchField() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("Search exercises").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitFilteredToOneResult() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(SHORT_EXERCISE).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun addSetButton() = compose.onNodeWithText("Add set", useUnmergedTree = true)

    private fun awaitReadyToLogASet() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("Add set", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitSheetOpen() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("Save set").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitResting() {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("LOG SET 2", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 10_000L

        /** Short enough that neither its name nor "Bodyweight" can push the row past the floor. */
        const val SHORT_EXERCISE = "Air Bike"
        const val REST_EXERCISE = "Bench Dips"
        val TODAY_SESSION = SessionId("today-turn4-layout")
        val LAST_WEEK = SessionId("last-week-turn4-layout")
    }
}
