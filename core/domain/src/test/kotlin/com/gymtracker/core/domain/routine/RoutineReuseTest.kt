package com.gymtracker.core.domain.routine

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.RoutineOrigin
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.model.WorkoutWorkflow
import com.gymtracker.core.domain.session.FakeSessionRepository
import com.gymtracker.core.domain.sessionexercise.FakeSessionExerciseRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutineReuseTest {
    private val member = UserId("alice")
    private val id = SessionId("visit")
    private val origin = Routine(RoutineId("upper"), member, "Upper A", 1)
    private val target = MovementTarget(3, 12, 50.0, 90)
    private val baseline =
        listOf(
            RoutineItem(RoutineItemId("bench"), origin.id, ExerciseId("bench"), 1, target),
            RoutineItem(RoutineItemId("row"), origin.id, ExerciseId("row"), 2, target),
        )
    private val sessions = FakeSessionRepository()
    private val movements = FakeSessionExerciseRepository()
    private val items = FakeRoutineItemRepository()
    private val routines = FakeRoutineRepository()
    private var lastWrite: RoutineTemplateWrite? = null
    private var failSave = false
    private val store =
        object : RoutineTemplateStore {
            override suspend fun reviewed(
                session: WorkoutSession,
                signature: String,
            ): Boolean {
                sessions.updateSession(session.copy(workflow = session.workflow.copy(reviewedStructure = signature)))
                return true
            }

            override suspend fun apply(write: RoutineTemplateWrite): Boolean {
                if (failSave) error("disk full")
                lastWrite = write
                sessions.updateSession(
                    write.session.copy(workflow = write.session.workflow.copy(reviewedStructure = write.signature)),
                )
                return true
            }
        }
    private var sequence = 0
    private val reuse =
        RoutineReuse(
            sessions,
            movements,
            routines,
            items,
            store,
            { "new-${++sequence}" },
        )

    private suspend fun seed(changed: Boolean = true) {
        routines.add(origin)
        baseline.forEach { items.addItem(it) }
        sessions.startSession(
            WorkoutSession(
                id,
                member,
                null,
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(600),
                null,
                RoutineOrigin(origin.id.value, origin.name),
                WorkoutWorkflow(originalRoutineItems = baseline, structuralEdits = changed),
            ),
        )
        baseline.forEachIndexed { index, item ->
            val exercise = if (changed && index == 1) ExerciseId("curl") else item.exerciseId
            movements.add(
                SessionExercise(
                    SessionExerciseId("occurrence-$index"),
                    id,
                    exercise,
                    index + 1,
                    if (index ==
                        0
                    ) {
                        target
                    } else {
                        null
                    },
                    routineItemId = if (exercise == item.exerciseId) item.id.value else null,
                ),
            )
        }
    }

    @Test
    fun `selection and performance choices alone never offer a template save`() =
        runTest {
            seed(changed = false)
            assertNull(reuse.preview(id, member))
        }

    @Test
    fun `a net structure change is reviewed with three explicit choices`() =
        runTest {
            seed()
            val review = assertNotNull(reuse.preview(id, member))
            assertTrue(review.canUpdateOriginal)
            assertEquals(listOf("bench", "curl"), review.movements.map { it.exerciseId.value })
            assertNull(lastWrite)
        }

    @Test
    fun `updating preserves surviving occurrences authored targets and the finished workout`() =
        runTest {
            seed()
            val review = assertNotNull(reuse.preview(id, member))
            assertIs<RoutineReuseResult.Saved>(reuse.apply(review, RoutineReuseChoice.UPDATE_ORIGINAL))
            val write = assertNotNull(lastWrite)
            assertEquals(origin, write.replacement)
            assertEquals(baseline.first(), write.replacementItems.first())
            assertNull(write.replacementItems.last().target)
            assertEquals(Instant.EPOCH.plusSeconds(600), sessions.findSession(id)?.endedAt)
            assertNull(reuse.preview(id, member), "re-finishing does not repeat a handled prompt")
        }

    @Test
    fun `today only records the decision without writing the template`() =
        runTest {
            seed()
            val review = assertNotNull(reuse.preview(id, member))
            assertIs<RoutineReuseResult.TodayOnly>(reuse.apply(review, RoutineReuseChoice.TODAY_ONLY))
            assertNull(lastWrite)
            assertEquals(baseline, items.itemsOf(origin.id))
            assertNull(reuse.preview(id, member))
        }

    @Test
    fun `independently edited original refuses a blind overwrite and permits save as new`() =
        runTest {
            seed()
            items.updateItem(baseline.first().copy(target = target.copy(reps = 8)))
            val review = assertNotNull(reuse.preview(id, member))
            assertFalse(review.canUpdateOriginal)
            assertIs<RoutineReuseResult.Conflict>(reuse.apply(review, RoutineReuseChoice.UPDATE_ORIGINAL))
            assertNull(lastWrite)
            assertIs<RoutineReuseResult.Saved>(reuse.apply(review, RoutineReuseChoice.SAVE_AS_NEW, "Upper B"))
            assertEquals("Upper B", lastWrite?.replacement?.name)
            assertEquals(origin, routines.find(origin.id))
        }

    @Test
    fun `a template failure retains the finished workout original and retryable review`() =
        runTest {
            seed()
            failSave = true
            val review = assertNotNull(reuse.preview(id, member))
            assertIs<RoutineReuseResult.Failed>(reuse.apply(review, RoutineReuseChoice.UPDATE_ORIGINAL))
            assertNotNull(sessions.findSession(id)?.endedAt)
            assertEquals(baseline, items.itemsOf(origin.id))
            assertNotNull(reuse.preview(id, member))
        }
}
