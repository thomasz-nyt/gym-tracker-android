package com.gymtracker.core.domain.routine

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.UserId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** US-29: a routine is a name and an order, and the editor can change both. */
class RoutineEditingTest {
    private val alice = UserId("alice")
    private val bench = ExerciseId("bench")
    private val squat = ExerciseId("squat")
    private val row = ExerciseId("row")

    private val items = FakeRoutineItemRepository()
    private val routines = FakeRoutineRepository(cascade = { items.cascadeDelete(it) })
    private var nextRoutine = 1
    private var nextItem = 1

    private val createRoutine = CreateRoutine(routines) { RoutineId("r-${nextRoutine++}") }
    private val addExercise = AddExerciseToRoutine(items) { RoutineItemId("i-${nextItem++}") }
    private val removeExercise = RemoveExerciseFromRoutine(items)
    private val moveExercise = MoveExerciseInRoutine(items)
    private val renameRoutine = RenameRoutine(routines)
    private val deleteRoutine = DeleteRoutine(routines)

    @Test
    fun `a new routine is a name and nothing else`() =
        runTest {
            val created = createRoutine(alice, "Upper A")

            assertEquals("Upper A", created.name)
            assertEquals(1, created.position)
            assertTrue(items.itemsOf(created.id).isEmpty(), "a routine starts empty")
        }

    @Test
    fun `routines are appended to the member's list`() =
        runTest {
            createRoutine(alice, "Upper A")

            val second = createRoutine(alice, "Lower B")

            assertEquals(2, second.position)
        }

    @Test
    fun `one member's routines do not take positions from another's`() =
        runTest {
            createRoutine(UserId("bob"), "Bob's push day")

            val alices = createRoutine(alice, "Upper A")

            assertEquals(1, alices.position, "Alice's first routine is her first, not her second")
        }

    @Test
    fun `exercises are appended in the order they are added`() =
        runTest {
            val routine = createRoutine(alice, "Upper A")

            addExercise(routine.id, bench)
            addExercise(routine.id, row)

            assertEquals(listOf(bench, row), items.itemsOf(routine.id).map { it.exerciseId })
        }

    @Test
    fun `the same exercise may appear twice, as it may in a session`() =
        runTest {
            // US-02 allows it in a session, so a routine that shapes a session must allow it too.
            val routine = createRoutine(alice, "Upper A")

            addExercise(routine.id, bench)
            addExercise(routine.id, bench)

            val added = items.itemsOf(routine.id)
            assertEquals(listOf(bench, bench), added.map { it.exerciseId })
            assertEquals(2, added.map { it.id }.toSet().size, "two rows, so they can be removed separately")
        }

    @Test
    fun `removing a movement leaves the rest in order`() =
        runTest {
            val routine = createRoutine(alice, "Upper A")
            addExercise(routine.id, bench)
            val middle = addExercise(routine.id, squat)
            addExercise(routine.id, row)

            removeExercise(middle.id)

            assertEquals(listOf(bench, row), items.itemsOf(routine.id).map { it.exerciseId })
        }

    @Test
    fun `a movement appended after a removal does not collide with a surviving position`() =
        runTest {
            // MAX(position) + 1, never a count: the same rule session_exercises follows.
            val routine = createRoutine(alice, "Upper A")
            val first = addExercise(routine.id, bench)
            addExercise(routine.id, squat)
            removeExercise(first.id)

            addExercise(routine.id, row)

            val surviving = items.itemsOf(routine.id)
            assertEquals(surviving.map { it.position }.toSet().size, surviving.size, "positions stay unique")
            assertEquals(listOf(squat, row), surviving.map { it.exerciseId })
        }

    @Test
    fun `a movement can be dragged earlier`() =
        runTest {
            val routine = createRoutine(alice, "Upper A")
            addExercise(routine.id, bench)
            addExercise(routine.id, squat)
            addExercise(routine.id, row)

            moveExercise(routine.id, from = 2, to = 0)

            assertEquals(listOf(row, bench, squat), items.itemsOf(routine.id).map { it.exerciseId })
        }

    @Test
    fun `a movement can be dragged later`() =
        runTest {
            val routine = createRoutine(alice, "Upper A")
            addExercise(routine.id, bench)
            addExercise(routine.id, squat)
            addExercise(routine.id, row)

            moveExercise(routine.id, from = 0, to = 2)

            assertEquals(listOf(squat, row, bench), items.itemsOf(routine.id).map { it.exerciseId })
        }

    @Test
    fun `reordering renumbers positions contiguously from one`() =
        runTest {
            val routine = createRoutine(alice, "Upper A")
            addExercise(routine.id, bench)
            addExercise(routine.id, squat)
            addExercise(routine.id, row)

            moveExercise(routine.id, from = 2, to = 0)

            assertEquals(listOf(1, 2, 3), items.itemsOf(routine.id).map { it.position })
        }

    @Test
    fun `a move that goes nowhere changes nothing`() =
        runTest {
            val routine = createRoutine(alice, "Upper A")
            addExercise(routine.id, bench)
            addExercise(routine.id, squat)

            moveExercise(routine.id, from = 1, to = 1)

            assertEquals(listOf(bench, squat), items.itemsOf(routine.id).map { it.exerciseId })
        }

    @Test
    fun `a move with an index off the end is ignored rather than throwing`() =
        runTest {
            // The editor is a drag surface; an out-of-range index is a UI bug, not a reason to
            // crash on the gym floor.
            val routine = createRoutine(alice, "Upper A")
            addExercise(routine.id, bench)
            addExercise(routine.id, squat)

            moveExercise(routine.id, from = 5, to = 0)

            assertEquals(listOf(bench, squat), items.itemsOf(routine.id).map { it.exerciseId })
        }

    @Test
    fun `renaming keeps the movements`() =
        runTest {
            val routine = createRoutine(alice, "Upper A")
            addExercise(routine.id, bench)

            renameRoutine(routine.id, "Push A")

            assertEquals("Push A", routines.find(routine.id)?.name)
            assertEquals(listOf(bench), items.itemsOf(routine.id).map { it.exerciseId })
        }

    @Test
    fun `deleting a routine takes its movements with it`() =
        runTest {
            val routine = createRoutine(alice, "Upper A")
            addExercise(routine.id, bench)
            addExercise(routine.id, squat)

            deleteRoutine(routine.id)

            assertNull(routines.find(routine.id))
            assertTrue(items.all.isEmpty(), "ON DELETE CASCADE, not orphaned rows")
        }

    @Test
    fun `deleting one routine leaves another alone`() =
        runTest {
            val kept = createRoutine(alice, "Upper A")
            addExercise(kept.id, bench)
            val dropped = createRoutine(alice, "Lower B")
            addExercise(dropped.id, squat)

            deleteRoutine(dropped.id)

            assertEquals(listOf(bench), items.itemsOf(kept.id).map { it.exerciseId })
        }

    @Test
    fun `the member's routines are listed in order`() =
        runTest {
            createRoutine(alice, "Upper A")
            createRoutine(alice, "Lower B")

            assertEquals(listOf("Upper A", "Lower B"), routines.observeRoutines(alice).first().map { it.name })
        }
}
