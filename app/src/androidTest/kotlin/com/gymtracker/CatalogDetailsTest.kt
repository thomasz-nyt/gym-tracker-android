package com.gymtracker

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
 * Two catalog gaps found by the 2026-09-04 UI/UX review, neither covered by a test until now.
 *
 * [anExerciseFoundByAnAliasSaysSoOnItsDetailScreen]: ADR-0015's hand-authored aliases were
 * searchable (US-12) and shown nowhere — a member who typed "ohp" landed on a screen with no
 * word confirming it was the movement they meant. US-13 now lists them.
 *
 * [anEmptyResultOffersToClearTheNarrowingWhereTheEmptinessShows]: "Nothing matches. Try fewer
 * filters." named the remedy without offering it; the only `Clear` sat in the count row above,
 * where an eye drawn to an empty list does not go.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CatalogDetailsTest {
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
    lateinit var catalogSeeder: CatalogSeeder

    @Inject
    lateinit var currentMember: CurrentMember

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
    fun anExerciseFoundByAnAliasSaysSoOnItsDetailScreen() {
        openBrowseAndSearchFor(ALIAS)
        compose.waitUntil(timeoutMillis = FILTER_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(ALIASED_EXERCISE).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onAllNodesWithText(ALIASED_EXERCISE)[0].performClick()

        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(ALSO_CALLED).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(ALSO_CALLED).assertIsDisplayed()
    }

    @Test
    fun anEmptyResultOffersToClearTheNarrowingWhereTheEmptinessShows() {
        openBrowseAndSearchFor(NO_SUCH_EXERCISE)
        compose.waitUntil(timeoutMillis = FILTER_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(NOTHING_MATCHES).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText(CLEAR_SEARCH_AND_FILTERS).performClick()

        // `onFiltersCleared` resets the query as well as the chips, so the whole catalog is back.
        compose.waitUntil(timeoutMillis = FILTER_TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag(CATALOG_ROW_TEST_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText(NOTHING_MATCHES).assertCountEquals(0)
    }

    private fun openBrowseAndSearchFor(query: String) {
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(START).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(BROWSE_EXERCISES).performClick()
        compose.waitUntil(timeoutMillis = READY_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(SEARCH_FIELD).fetchSemanticsNodes().isNotEmpty()
        }
        // performClick first, as `TurnFourLayoutTest` does: performTextInput delivers through
        // the field's SetText semantics, which needs the field focused to take on a real device.
        compose.onNodeWithText(SEARCH_FIELD).performClick()
        compose.onNodeWithText(SEARCH_FIELD).performTextInput(query)
    }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 10_000L

        /** The catalog's 150 ms debounce plus a real device's filter over 873 rows. */
        const val FILTER_TIMEOUT_MILLIS = 20_000L
        const val START = "Start workout"
        const val BROWSE_EXERCISES = "Browse exercises"
        const val SEARCH_FIELD = "Search exercises"

        /** One of ADR-0015's hand-authored aliases, and the movement it names. */
        const val ALIAS = "ohp"
        const val ALIASED_EXERCISE = "Barbell Shoulder Press"
        const val ALSO_CALLED = "Also called: ohp, overhead press, military press"

        const val NO_SUCH_EXERCISE = "zzqqxx"
        const val NOTHING_MATCHES = "Nothing matches. Try fewer filters."
        const val CLEAR_SEARCH_AND_FILTERS = "Clear search and filters"

        /** Matches `BrowseScreen.kt`'s private tag literally, as `TurnFourLayoutTest` does. */
        const val CATALOG_ROW_TEST_TAG = "catalog-row"
    }
}
