package com.gymtracker.feature.routines

import app.cash.turbine.test
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.routine.AddExerciseToRoutine
import com.gymtracker.core.domain.routine.DeleteRoutine
import com.gymtracker.core.domain.routine.MoveExerciseInRoutine
import com.gymtracker.core.domain.routine.RemoveExerciseFromRoutine
import com.gymtracker.core.domain.routine.RenameRoutine
import com.gymtracker.core.domain.routine.SetRoutineItemTarget
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
import kotlin.test.assertNotNull
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
            deleteRoutine = DeleteRoutine(routines),
            lastPerformanceOf = LastPerformanceOf(sets),
            setRoutineItemTarget = SetRoutineItemTarget(items),
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
    fun `deleting the routine removes it and its movements`() =
        runTest {
            // ADR-0019: destructive lives in the editor now, not on the Routines list row —
            // see RoutinesScreen.kt for why. The cascade itself is unchanged from what
            // RoutinesViewModelTest used to cover.
            givenUpperA()
            val viewModel = viewModel()
            viewModel.onAddExercise(bench)

            viewModel.onDeleteRoutine()

            assertTrue(routines.all.isEmpty())
            assertTrue(items.all.isEmpty(), "cascade, not orphans")
        }

    @Test
    fun `deleting reports that it happened, so the screen can leave`() =
        runTest {
            givenUpperA()
            val viewModel = viewModel()

            viewModel.isDeleted.test {
                assertEquals(false, awaitItem())
                viewModel.onDeleteRoutine()
                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a movement with no target carries none, same absence pattern as lastTime`() =
        runTest {
            // ADR-0027 replaces the old structural test ("no target field exists") with this
            // one: the field exists now, and a movement nobody has set a target for shows none —
            // the same US-13 absence this class already tests for `lastTime`, not a zero.
            givenUpperA()
            val viewModel = viewModel()
            viewModel.onAddExercise(bench)

            viewModel.uiState.test {
                assertNull(expectMostRecentItem().movements.single().target)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a movement's target is entered and reaches the screen`() =
        runTest {
            // The fake member reads pounds (ADR-0006): 135 lb typed is 61.23 kg stored, the
            // same round trip `Add set`'s own weight field uses.
            givenUpperA()
            val viewModel = viewModel()
            viewModel.onAddExercise(bench)
            val itemId = items.itemsOf(upperA).single().id

            viewModel.target.onEdit(itemId)
            viewModel.target.onFieldChanged(sets = "3", reps = "8", weight = "135")
            viewModel.target.onSave()

            viewModel.uiState.test {
                val target = expectMostRecentItem().movements.single().target
                assertEquals(MovementTarget(sets = 3, reps = 8, weightKg = 61.23), target)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `editing an existing target shows its load back in the member's own unit`() =
        runTest {
            // Found on a device, not in a test: the load field showed the stored kilograms
            // verbatim regardless of the member's unit, so entering "105" meant to be pounds
            // was saved as 105 kg. This is the round trip that must hold instead.
            givenUpperA()
            val viewModel = viewModel()
            viewModel.onAddExercise(bench)
            val itemId = items.itemsOf(upperA).single().id
            viewModel.target.onEdit(itemId)
            viewModel.target.onFieldChanged(sets = "3", reps = "8", weight = "135")
            viewModel.target.onSave()

            viewModel.target.onEdit(itemId)

            viewModel.target.editor.test {
                assertEquals("135", awaitItem()?.weight, "61.23 kg read back as the 135 lb it was typed as")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a target can be entered with some fields left blank`() =
        runTest {
            // US-30: "3 x 8, load unrecorded is a plan" — each field is optional on its own.
            givenUpperA()
            val viewModel = viewModel()
            viewModel.onAddExercise(bench)
            val itemId = items.itemsOf(upperA).single().id

            viewModel.target.onEdit(itemId)
            viewModel.target.onFieldChanged(sets = "3", reps = "8")
            viewModel.target.onSave()

            viewModel.uiState.test {
                val target = expectMostRecentItem().movements.single().target
                assertEquals(MovementTarget(sets = 3, reps = 8, weightKg = null), target)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `editing one movement's target changes no other movement`() =
        runTest {
            givenUpperA()
            val viewModel = viewModel()
            viewModel.onAddExercise(bench)
            viewModel.onAddExercise(squat)
            val benchId = items.itemsOf(upperA).first { it.exerciseId == bench }.id

            viewModel.target.onEdit(benchId)
            viewModel.target.onFieldChanged(sets = "3", reps = "8", weight = "135")
            viewModel.target.onSave()

            viewModel.uiState.test {
                val movements = expectMostRecentItem().movements
                assertEquals(MovementTarget(3, 8, 61.23), movements.first { it.exerciseId == bench }.target)
                assertNull(movements.first { it.exerciseId == squat }.target, "squat's target is untouched")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an existing target reopens the editor prefilled, and can be changed`() =
        runTest {
            givenUpperA()
            val viewModel = viewModel()
            viewModel.onAddExercise(bench)
            val itemId = items.itemsOf(upperA).single().id
            viewModel.target.onEdit(itemId)
            viewModel.target.onFieldChanged(sets = "3", reps = "8", weight = "135")
            viewModel.target.onSave()

            viewModel.target.onEdit(itemId)
            viewModel.target.editor.test {
                val editor = awaitItem()
                assertEquals("3", editor?.sets)
                assertEquals("8", editor?.reps)
                assertEquals("135", editor?.weight)
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.target.onFieldChanged(reps = "6")
            viewModel.target.onSave()

            viewModel.uiState.test {
                assertEquals(MovementTarget(3, 6, 61.23), expectMostRecentItem().movements.single().target)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a target can be cleared`() =
        runTest {
            givenUpperA()
            val viewModel = viewModel()
            viewModel.onAddExercise(bench)
            val itemId = items.itemsOf(upperA).single().id
            viewModel.target.onEdit(itemId)
            viewModel.target.onFieldChanged(sets = "3", reps = "8", weight = "105")
            viewModel.target.onSave()

            viewModel.target.onEdit(itemId)
            viewModel.target.onClear()

            viewModel.uiState.test {
                assertNull(expectMostRecentItem().movements.single().target)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an out-of-range target is rejected rather than saved`() =
        runTest {
            givenUpperA()
            val viewModel = viewModel()
            viewModel.onAddExercise(bench)
            val itemId = items.itemsOf(upperA).single().id

            viewModel.target.onEdit(itemId)
            viewModel.target.onFieldChanged(sets = "0", reps = "8")
            viewModel.target.onSave()

            viewModel.uiState.test {
                assertNull(expectMostRecentItem().movements.single().target, "0 sets is not a valid target")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a refused save says which fields it could not read, and saves nothing`() =
        runTest {
            // Found by the 2026-09-04 UI/UX review, the same class of defect PR #74 fixed on the
            // set sheet ("Save set stayed enabled on unparseable input"): the test above proves
            // the write was refused, but the dialog then stayed open saying nothing at all, so
            // a member had no way to tell a refused save from a tap that did not register.
            givenUpperA()
            val viewModel = viewModel()
            viewModel.onAddExercise(bench)
            val itemId = items.itemsOf(upperA).single().id

            viewModel.target.onEdit(itemId)
            viewModel.target.onFieldChanged(sets = "abc", reps = "8", weight = "-5")
            viewModel.target.onSave()

            viewModel.target.editor.test {
                val editor = expectMostRecentItem()
                assertNotNull(editor, "a refused save leaves the editor open")
                assertEquals(
                    listOf("Sets needs a whole number, 1 or more.", "Load needs a number, 0 or more."),
                    editor.errors,
                    "one line per unreadable field, in field order; reps was fine and is not named",
                )
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.uiState.test {
                assertNull(expectMostRecentItem().movements.single().target, "nothing was saved")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `typing again clears the reason a save was refused`() =
        runTest {
            givenUpperA()
            val viewModel = viewModel()
            viewModel.onAddExercise(bench)
            val itemId = items.itemsOf(upperA).single().id
            viewModel.target.onEdit(itemId)
            viewModel.target.onFieldChanged(sets = "abc")
            viewModel.target.onSave()

            viewModel.target.onFieldChanged(sets = "3")

            viewModel.target.editor.test {
                assertEquals(
                    emptyList<String>(),
                    expectMostRecentItem()?.errors,
                    "the reason described a form that no longer exists",
                )
                cancelAndIgnoreRemainingEvents()
            }
        }
}
