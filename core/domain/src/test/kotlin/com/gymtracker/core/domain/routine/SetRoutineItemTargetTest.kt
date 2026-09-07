package com.gymtracker.core.domain.routine

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.RoutineItemId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * US-30 (ADR-0027): a movement in a routine may carry a target, set and cleared independently
 * of every other movement.
 */
class SetRoutineItemTargetTest {
    private val routineId = RoutineId("r1")
    private val bench = ExerciseId("bench")
    private val items = FakeRoutineItemRepository()
    private val setTarget = SetRoutineItemTarget(items)

    private suspend fun benchItem(): RoutineItem {
        val item = RoutineItem(RoutineItemId("i1"), routineId, bench, 1)
        items.addItem(item)
        return item
    }

    @Test
    fun `a target can be set on a movement that had none`() =
        runTest {
            val item = benchItem()
            val target = MovementTarget(sets = 3, reps = 8, weightKg = 47.6)

            val updated = setTarget(item, target)

            assertEquals(target, updated.target)
            assertEquals(target, items.itemsOf(routineId).single().target)
        }

    @Test
    fun `each field of a target is independently nullable`() =
        runTest {
            val item = benchItem()

            val updated = setTarget(item, MovementTarget(sets = 3, reps = null, weightKg = null))

            assertEquals(3, updated.target?.sets)
            assertNull(updated.target?.reps)
            assertNull(updated.target?.weightKg)
        }

    @Test
    fun `a target can be cleared back to null`() =
        runTest {
            val item = benchItem()
            setTarget(item, MovementTarget(sets = 3, reps = 8, weightKg = 47.6))

            val cleared = setTarget(items.itemsOf(routineId).single(), null)

            assertNull(cleared.target)
            assertNull(items.itemsOf(routineId).single().target)
        }

    @Test
    fun `editing one movement's target changes no other movement`() =
        runTest {
            val squat = RoutineItem(RoutineItemId("i2"), routineId, ExerciseId("squat"), 2)
            items.addItem(squat)
            val bench = benchItem()

            setTarget(bench, MovementTarget(sets = 3, reps = 8, weightKg = 47.6))

            assertNull(items.itemsOf(routineId).first { it.id == squat.id }.target)
        }

    @Test
    fun `a target of fewer than one set is rejected`() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                setTarget(benchItem(), MovementTarget(sets = 0, reps = 8, weightKg = 47.6))
            }
        }

    @Test
    fun `a target of fewer than one rep is rejected`() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                setTarget(benchItem(), MovementTarget(sets = 3, reps = 0, weightKg = 47.6))
            }
        }

    @Test
    fun `a negative target load is rejected`() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                setTarget(benchItem(), MovementTarget(sets = 3, reps = 8, weightKg = -1.0))
            }
        }

    // ADR-0050: the rest joins the target as a fourth independently optional field. A target
    // that names only a rest is still a target — "bench, take two minutes" is a real plan.

    @Test
    fun `a target may name the rest that follows each set, on its own if need be`() =
        runTest {
            val item = benchItem()

            val updated = setTarget(item, MovementTarget(sets = null, reps = null, weightKg = null, restSeconds = 120))

            assertEquals(120, updated.target?.restSeconds)
            assertEquals(Duration.ofMinutes(2), updated.target?.rest, "what the rest timer will take")
            assertEquals(
                120,
                items
                    .itemsOf(routineId)
                    .single()
                    .target
                    ?.restSeconds,
            )
        }

    @Test
    fun `a rest of fewer than one second is rejected`() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                setTarget(benchItem(), MovementTarget(sets = 3, reps = 8, weightKg = 47.6, restSeconds = 0))
            }
        }
}
