package com.gymtracker.feature.routines

import app.cash.turbine.test
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.routine.AddExerciseToRoutine
import com.gymtracker.core.domain.routine.MoveExerciseInRoutine
import com.gymtracker.core.domain.routine.RemoveExerciseFromRoutine
import com.gymtracker.core.domain.routine.RenameRoutine
import com.gymtracker.core.domain.set.LastPerformanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * US-29's editor: a name, an order, and — critically — numbers that are history.
 *
 * The last two tests are the ones ADR-0020 turns on. Everything a movement row shows must be
 * something someone lifted, and a movement never performed must show nothing at all rather
 * than a zero standing in for a target.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoutineEditorViewModelTest {
    private val now: Instant = Instant.parse("2026-08-08T18:00:00Z")
    private val alice = UserId("alice")
    private val bench = ExerciseId("bench")
    private val squat = ExerciseId("squat")
    private val row = ExerciseId("row")
    private val upperA = RoutineId("r-1")

    private val items = FakeRoutineItems()
    private val routines = FakeRoutines(cascade = { items.cascadeDelete(it) })
    private val sets = FakeSets()
    private var nextItem = 1

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        RoutineEditorViewModel(
            routines = routines,
            items = items,
            catalog = FakeCatalog(),
            currentMember = FakeCurrentMember(alice),
            unitPreference = FakeUnitPreference(),
            addExerciseToRoutine = AddExerciseToRoutine(items) { RoutineItemId("i-${nextItem++}") },
            removeExerciseFromRoutine = RemoveExerciseFromRoutine(items),
            moveExerciseInRoutine = MoveExerciseInRoutine(items),
            renameRoutine = RenameRoutine(routines),
            lastPerformanceOf = LastPerformanceOf(sets),
        ).also { it.open(upperA) }

    private suspend fun givenUpperA() {
        routines.add(Routine(upperA, alice, "Upper A", 1))
    }

    @Test
    fun `the editor opens on the routine's name`() =
        runTest {
            givenUpperA()

            viewModel().uiState.test {
                assertEquals("Upper A", expectMostRecentItem().name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `movements are listed in order, with their catalog names`() =
        runTest {
            givenUpperA()
            val viewModel = viewModel()
            viewModel.onAddExercise(bench)
            viewModel.onAddExercise(squat)

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertEquals(listOf("Bench Press", "Squat"), state.movements.map { it.exerciseName })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `renaming is applied`() =
        runTest {
            givenUpperA()
            val viewModel = viewModel()

            viewModel.onNameChanged("Push A")

            assertEquals("Push A", routines.find(upperA)?.name)
        }

    @Test
    fun `a movement can be removed`() =
        runTest {
            givenUpperA()
            val viewModel = viewModel()
            viewModel.onAddExercise(bench)
            viewModel.onAddExercise(squat)

            viewModel.onRemoveMovement(RoutineItemId("i-1"))

            assertEquals(listOf(squat), items.itemsOf(upperA).map { it.exerciseId })
        }

    @Test
    fun `a movement can be moved up and down`() =
        runTest {
            givenUpperA()
            val viewModel = viewModel()
            viewModel.onAddExercise(bench)
            viewModel.onAddExercise(squat)
            viewModel.onAddExercise(row)

            viewModel.onMoveDown(0)
            assertEquals(listOf(squat, bench, row), items.itemsOf(upperA).map { it.exerciseId })

            viewModel.onMoveUp(2)
            assertEquals(listOf(squat, row, bench), items.itemsOf(upperA).map { it.exerciseId })
        }

    @Test
    fun `moving the first movement up does nothing`() =
        runTest {
            givenUpperA()
            val viewModel = viewModel()
            viewModel.onAddExercise(bench)
            viewModel.onAddExercise(squat)

            viewModel.onMoveUp(0)

            assertEquals(listOf(bench, squat), items.itemsOf(upperA).map { it.exerciseId })
        }

    @Test
    fun `moving the last movement down does nothing`() =
        runTest {
            givenUpperA()
            val viewModel = viewModel()
            viewModel.onAddExercise(bench)
            viewModel.onAddExercise(squat)

            viewModel.onMoveDown(1)

            assertEquals(listOf(bench, squat), items.itemsOf(upperA).map { it.exerciseId })
        }

    @Test
    fun `a movement shows what was actually lifted last time, with its date`() =
        runTest {
            givenUpperA()
            val logged = ExerciseSet("s1", SessionExerciseId("se-1"), 1, 61.23, 8, null, now)
            sets.seed(logged)
            sets.lastFor[bench] = logged.id
            val viewModel = viewModel()
            viewModel.onAddExercise(bench)

            viewModel.uiState.test {
                val movement = expectMostRecentItem().movements.single()
                assertEquals(8, movement.lastTime?.reps)
                assertEquals(61.23, movement.lastTime?.weightKg)
                assertEquals(now, movement.lastTime?.performedAt)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a movement never performed shows no numbers at all`() =
        runTest {
            // ADR-0020's whole bargain: with no history there is nothing honest to show, so
            // nothing is shown — not a zero, and not a target (US-13's absence pattern).
            givenUpperA()
            val viewModel = viewModel()
            viewModel.onAddExercise(bench)

            viewModel.uiState.test {
                assertNull(expectMostRecentItem().movements.single().lastTime)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the editor state carries no target field for a screen to render`() =
        runTest {
            // Structural, and the reason this test exists: if a future edit adds a target to
            // MovementRow, this breaks rather than quietly shipping a prescription.
            givenUpperA()
            val viewModel = viewModel()
            viewModel.onAddExercise(bench)

            viewModel.uiState.test {
                val movement = expectMostRecentItem().movements.single()
                val fields = movement.javaClass.declaredFields.map { it.name }
                assertTrue(
                    fields.none { it.contains("target", true) || it.contains("planned", true) },
                    "MovementRow gained a target field: $fields",
                )
                cancelAndIgnoreRemainingEvents()
            }
        }
}
