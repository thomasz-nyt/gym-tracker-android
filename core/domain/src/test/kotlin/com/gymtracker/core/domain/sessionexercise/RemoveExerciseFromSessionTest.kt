package com.gymtracker.core.domain.sessionexercise

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.set.FakeSetRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-02c: the machine was taken, or the exercise was added by mistake.
 *
 * Follows ADR-0012's pattern rather than inventing one — hard delete, in-memory snapshot,
 * 5-second undo — because US-04, US-06a and this are the same destructive action at three
 * granularities and should not behave differently.
 */
class RemoveExerciseFromSessionTest {
    private val now: Instant = Instant.parse("2026-08-02T18:00:00Z")
    private val session = SessionId("s1")

    private val sets = FakeSetRepository()

    // The schema takes the sets with the appearance, so the fake has to as well.
    private val sessionExercises = FakeSessionExerciseRepository(cascade = sets::cascadeDeleteExercise)

    private val remove = RemoveExerciseFromSession(sessionExercises, sets)
    private val restore = RestoreExerciseToSession(sessionExercises, sets)

    private val bench = SessionExercise(SessionExerciseId("se-1"), session, ExerciseId("bench"), 1)
    private val squat = SessionExercise(SessionExerciseId("se-2"), session, ExerciseId("squat"), 2)
    private val row = SessionExercise(SessionExerciseId("se-3"), session, ExerciseId("row"), 3)

    private suspend fun seed() {
        listOf(bench, squat, row).forEach {
            sessionExercises.add(it)
            sets.belongsTo(it)
        }
        sets.add(ExerciseSet("a", bench.id, 1, 60.0, 10, 8.0, now))
        sets.add(ExerciseSet("b", bench.id, 2, 60.0, 8, null, now))
        sets.add(ExerciseSet("c", squat.id, 1, null, 12, null, now))
    }

    @Test
    fun `removing takes the exercise and the sets logged against it`() =
        runTest {
            seed()

            remove(bench.id)

            assertEquals(listOf(squat, row), sessionExercises.forSession(session))
            assertEquals(listOf("c"), sets.forSession(session).map { it.id })
        }

    @Test
    fun `removing one exercise leaves the others untouched`() =
        runTest {
            seed()

            remove(squat.id)

            assertEquals(listOf(bench, row), sessionExercises.forSession(session))
            assertEquals(listOf("a", "b"), sets.forSession(session).map { it.id }.sorted())
        }

    @Test
    fun `undo puts the exercise back with its sets unchanged`() =
        runTest {
            seed()
            val before = sessionExercises.forSession(session)
            val setsBefore = sets.forSession(session)

            val removed = checkNotNull(remove(bench.id))
            restore(removed)

            assertEquals(before, sessionExercises.forSession(session), "same rows, not copies")
            assertEquals(setsBefore.sortedBy { it.id }, sets.forSession(session).sortedBy { it.id })
        }

    @Test
    fun `a restored exercise keeps its position, so the order is the one I performed`() =
        runTest {
            // US-02c: "same ids, same values, same position". The displayed list closes the
            // gap while it is gone (US-02b); the stored position does not move.
            seed()

            val removed = checkNotNull(remove(squat.id))
            restore(removed)

            assertEquals(listOf(1, 2, 3), sessionExercises.forSession(session).map { it.position })
        }

    @Test
    fun `the snapshot carries everything undo needs, so it reads nothing back`() =
        runTest {
            seed()

            val removed = checkNotNull(remove(bench.id))

            assertEquals(bench, removed.sessionExercise)
            assertEquals(listOf("a", "b"), removed.sets.map { it.id }.sorted())
        }

    @Test
    fun `an exercise with no sets removes and restores without special handling`() =
        runTest {
            seed()

            val removed = checkNotNull(remove(row.id))
            assertEquals(listOf(bench, squat), sessionExercises.forSession(session))

            restore(removed)

            assertEquals(listOf(bench, squat, row), sessionExercises.forSession(session))
        }

    @Test
    fun `removing one that is already gone reports nothing to undo`() =
        runTest {
            assertNull(remove(SessionExerciseId("never-existed")))
        }

    @Test
    fun `the next exercise added after a removal does not collide with a surviving position`() =
        runTest {
            // MAX(position) + 1, not count + 1: removing the middle of three and adding one
            // would otherwise mint position 3 while the original 3 is still there.
            seed()

            remove(squat.id)

            assertEquals(4, sessionExercises.nextPosition(session))
        }
}
