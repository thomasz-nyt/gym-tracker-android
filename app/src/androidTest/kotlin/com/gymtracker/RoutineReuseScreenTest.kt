package com.gymtracker

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.rule.GrantPermissionRule
import com.gymtracker.app.MainActivity
import com.gymtracker.core.data.exercise.CatalogSeeder
import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.RoutineOrigin
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.model.WorkoutWorkflow
import com.gymtracker.core.domain.routine.RoutineItemRepository
import com.gymtracker.core.domain.routine.RoutineRepository
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import com.gymtracker.core.domain.set.SetRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@HiltAndroidTest
class RoutineReuseScreenTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val notifications = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var seeder: CatalogSeeder

    @Inject lateinit var catalog: ExerciseCatalog

    @Inject lateinit var member: CurrentMember

    @Inject lateinit var sessions: SessionRepository

    @Inject lateinit var movements: SessionExerciseRepository

    @Inject lateinit var sets: SetRepository

    @Inject lateinit var routines: RoutineRepository

    @Inject lateinit var items: RoutineItemRepository
    private val routineId = RoutineId("review-source")
    private val sessionId = SessionId("review-workout")
    private lateinit var original: List<RoutineItem>
    private var changedExercise = ExerciseId("unused")

    @Before fun fixture() =
        runBlocking {
            hilt.inject()
            seeder.seedIfEmpty(Instant.now().toEpochMilli())
            val owner = member.id()
            val exercises = catalog.observeRanked(owner).first().take(3)
            original =
                exercises.take(2).mapIndexed { i, exercise ->
                    RoutineItem(RoutineItemId("source-$i"), routineId, exercise.id, i + 1, MovementTarget(3, 12, 50.0))
                }
            changedExercise = exercises.last().id
            routines.add(Routine(routineId, owner, "Upper A", 1))
            original.forEach { items.addItem(it) }
            val now = Instant.now()
            sessions.startSession(
                WorkoutSession(
                    sessionId,
                    owner,
                    null,
                    now.minusSeconds(60),
                    null,
                    null,
                    RoutineOrigin(routineId.value, "Upper A"),
                    WorkoutWorkflow(originalRoutineItems = original, structuralEdits = true),
                ),
            )
            val first =
                SessionExercise(
                    SessionExerciseId("kept"),
                    sessionId,
                    original.first().exerciseId,
                    1,
                    original.first().target,
                    routineItemId = original.first().id.value,
                )
            movements.add(first)
            movements.add(SessionExercise(SessionExerciseId("new"), sessionId, changedExercise, 2))
            sets.add(ExerciseSet("actual", first.id, 1, 50.0, 10, null, now.minusSeconds(10)))
        }

    private fun finishAndReview() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("More exercise actions").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("More exercise actions").performClick()
        compose.onNodeWithText("Finish workout").performScrollTo().performClick()
        compose.onNode(hasText("Finish workout") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(
            10_000,
        ) { compose.onAllNodesWithText("Reuse workout changes?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Reuse workout changes?").assertIsDisplayed()
        compose.waitForIdle()
        captureNativeReview("routine-reuse.png")
    }

    @Test fun originalChangesOnlyAfterExplicitSaveAndActualWorkoutStaysSaved() =
        runBlocking {
            finishAndReview()
            assertEquals(original, items.itemsOf(routineId))
            assertNotNull(sessions.findSession(sessionId)?.endedAt)
            compose.onNodeWithText("Update original").performClick()
            compose.waitUntil(
                10_000,
            ) { compose.onAllNodesWithText("Reuse workout changes?").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("Workout saved").assertIsDisplayed()
            compose.waitForIdle()
            captureNativeReview("workout-saved.png")
            compose.onNodeWithText("10 reps", substring = true).assertExists()
            val updated = items.itemsOf(routineId)
            assertEquals(original.first(), updated.first())
            assertEquals(changedExercise, updated.last().exerciseId)
            assertNull(updated.last().target)
            assertEquals(
                10,
                sets
                    .observeForSessionExercise(SessionExerciseId("kept"))
                    .first()
                    .single()
                    .reps,
            )
            assertEquals("Upper A", sessions.findSession(sessionId)?.routine?.name)
        }

    @Test fun todayOnlyKeepsOriginalAndRetainsActualSets(): Unit =
        runBlocking {
            finishAndReview()
            compose.onNodeWithText("Today only").performClick()
            compose.waitUntil(
                10_000,
            ) { compose.onAllNodesWithText("Reuse workout changes?").fetchSemanticsNodes().isEmpty() }
            assertEquals(original, items.itemsOf(routineId))
            assertNotNull(sessions.findSession(sessionId)?.workflow?.reviewedStructure)
        }

    @Test fun savingAsNewKeepsOriginalAndWorksWithTheKeyboardOpen(): Unit =
        runBlocking {
            finishAndReview()
            compose.onNodeWithText("Save as new").performClick()
            compose.onNodeWithText("New routine name").performClick().performTextReplacement("Upper B")
            compose.onNodeWithText("Save new routine").assertIsDisplayed()
            compose.waitForIdle()
            captureNativeReview("routine-new-name.png")
            assertNativeActionAboveKeyboard("Save new routine")
            compose.onNodeWithText("Save new routine").performClick()
            compose.waitUntil(
                10_000,
            ) { compose.onAllNodesWithText("Reuse workout changes?").fetchSemanticsNodes().isEmpty() }
            assertEquals(original, items.itemsOf(routineId))
            val created = routines.observeRoutines(member.id()).first().single { it.name == "Upper B" }
            val saved = items.itemsOf(created.id)
            assertEquals(original.first().target, saved.first().target)
            assertEquals(changedExercise, saved.last().exerciseId)
            assertNull(saved.last().target)
            assertNotNull(sessions.findSession(sessionId)?.endedAt)
        }
}
