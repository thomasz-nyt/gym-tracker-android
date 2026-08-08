package com.gymtracker.core.domain.set

import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals

/**
 * US-04: correcting weight, reps, or RPE of a set already logged.
 *
 * Validation must be identical to [LogSet]'s, because a correction should never be able to
 * produce a set that logging one fresh could not (shared via [SetValidation]).
 */
class UpdateSetTest {
    private val performedAt: Instant = Instant.parse("2026-08-01T18:00:00Z")
    private val appearance = SessionExerciseId("se-1")

    private val original =
        ExerciseSet(
            id = "set-1",
            sessionExerciseId = appearance,
            setIndex = 1,
            weightKg = 60.0,
            reps = 8,
            rpe = 7.0,
            performedAt = performedAt,
        )

    private val sets = FakeSetRepository()
    private val updateSet = UpdateSet(sets)

    private suspend fun seed() = sets.add(original)

    @Test
    fun `editing a set changes weight, reps and rpe but keeps id, index and timestamp`() =
        runTest {
            seed()

            val updated = updateSet(original, weight = 65.0, unit = WeightUnit.KG, reps = 6, rpe = 8.5)

            assertEquals("set-1", updated.id)
            assertEquals(1, updated.setIndex)
            assertEquals(performedAt, updated.performedAt)
            assertEquals(65.0, updated.weightKg)
            assertEquals(6, updated.reps)
            assertEquals(8.5, updated.rpe)
            assertEquals(listOf(updated), sets.all)
        }

    @Test
    fun `editing converts weight into canonical kilograms`() =
        runTest {
            seed()

            val updated = updateSet(original, weight = 135.0, unit = WeightUnit.LB, reps = 5, rpe = null)

            assertEquals(61.23, updated.weightKg)
        }

    @Test
    fun `editing to a bodyweight set clears the weight rather than storing zero`() =
        runTest {
            seed()

            val updated = updateSet(original, weight = null, unit = WeightUnit.KG, reps = 10, rpe = null)

            assertEquals(null, updated.weightKg)
        }

    @Test
    fun `reps must be at least one`() =
        runTest {
            seed()

            assertThrows<IllegalArgumentException> {
                updateSet(original, weight = 60.0, unit = WeightUnit.KG, reps = 0, rpe = null)
            }
            assertThrows<IllegalArgumentException> {
                updateSet(original, weight = 60.0, unit = WeightUnit.KG, reps = -1, rpe = null)
            }
        }

    @Test
    fun `rpe must be between 5 and 10 in half steps`() =
        runTest {
            seed()

            assertEquals(7.5, updateSet(original, 60.0, WeightUnit.KG, 8, rpe = 7.5).rpe)
            assertEquals(null, updateSet(original, 60.0, WeightUnit.KG, 8, rpe = null).rpe)
            assertThrows<IllegalArgumentException> { updateSet(original, 60.0, WeightUnit.KG, 8, rpe = 4.5) }
            assertThrows<IllegalArgumentException> { updateSet(original, 60.0, WeightUnit.KG, 8, rpe = 10.5) }
            assertThrows<IllegalArgumentException> { updateSet(original, 60.0, WeightUnit.KG, 8, rpe = 7.25) }
        }
}
