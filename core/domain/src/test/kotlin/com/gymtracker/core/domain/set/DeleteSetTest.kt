package com.gymtracker.core.domain.set

import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-04: deleting one set, with a 5-second undo.
 *
 * Follows [com.gymtracker.core.domain.sessionexercise.RemoveExerciseFromSession]'s pattern:
 * hard delete, in-memory snapshot, undo. A set is a leaf — nothing hangs off it — so unlike
 * that use case's `RemovedExercise`, the snapshot needs no wrapper type: the [ExerciseSet]
 * itself is everything undo needs.
 */
class DeleteSetTest {
    private val now: Instant = Instant.parse("2026-08-01T18:00:00Z")
    private val appearance = SessionExerciseId("se-1")

    private val setA = ExerciseSet("a", appearance, 1, 60.0, 10, 8.0, now)
    private val setB = ExerciseSet("b", appearance, 2, 60.0, 8, null, now)

    private val sets = FakeSetRepository()
    private val deleteSet = DeleteSet(sets)
    private val restoreSet = RestoreSet(sets)

    private suspend fun seed() {
        sets.add(setA)
        sets.add(setB)
    }

    @Test
    fun `deleting takes only the targeted set`() =
        runTest {
            seed()

            deleteSet(setA.id)

            assertEquals(listOf(setB), sets.all)
        }

    @Test
    fun `deleting one set leaves its siblings untouched`() =
        runTest {
            seed()

            deleteSet(setA.id)

            assertEquals(setB, sets.all.single())
        }

    @Test
    fun `undo puts the set back with the same id, index, values and timestamp`() =
        runTest {
            seed()

            val deleted = checkNotNull(deleteSet(setA.id))
            restoreSet(deleted)

            assertEquals(listOf(setB, setA).sortedBy { it.id }, sets.all.sortedBy { it.id })
        }

    @Test
    fun `the snapshot returned is the set itself, so undo needs nothing from the database`() =
        runTest {
            seed()

            val deleted = checkNotNull(deleteSet(setA.id))

            assertEquals(setA, deleted)
        }

    @Test
    fun `deleting one that is already gone reports nothing to undo`() =
        runTest {
            assertNull(deleteSet("never-existed"))
        }
}
