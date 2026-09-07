package com.gymtracker.core.domain.set

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-61: the whole of last time, not its last set. Built on the two repository reads the rest
 * panel's comparison (ADR-0023) already relies on, so the two can never disagree about which
 * appearance "last time" was.
 */
class PreviousPerformanceOfTest {
    private val bench = ExerciseId("bench")
    private val alice = UserId("alice")
    private val today = SessionId("today")
    private val lastWeek = SessionExerciseId("se-last-week")
    private val start = java.time.Instant.parse("2026-08-25T18:00:00Z")

    private fun set(
        id: String,
        index: Int,
        reps: Int,
        rpe: Double? = null,
        offsetSeconds: Long = (index - 1) * 120L,
    ) = ExerciseSet(
        id = id,
        sessionExerciseId = lastWeek,
        setIndex = index,
        weightKg = 61.23,
        reps = reps,
        rpe = rpe,
        performedAt = start.plusSeconds(offsetSeconds),
    )

    /** Overrides only the two reads the use case makes, per `NoSets`'s own contract. */
    private class SetsWithHistory(
        private val lastBefore: ExerciseSet?,
        private val appearance: List<ExerciseSet>,
    ) : NoSets() {
        override suspend fun lastSetOfBefore(
            exerciseId: ExerciseId,
            member: UserId,
            excludingSessionId: SessionId,
        ): ExerciseSet? = lastBefore

        override fun observeForSessionExercise(sessionExerciseId: SessionExerciseId): Flow<List<ExerciseSet>> =
            flowOf(if (sessionExerciseId == lastBefore?.sessionExerciseId) appearance else emptyList())
    }

    @Test
    fun `never performed before this session reads as nothing`() =
        runTest {
            val previous = PreviousPerformanceOf(SetsWithHistory(lastBefore = null, appearance = emptyList()))

            assertNull(previous(bench, alice, today), "US-13's absence pattern: no numbers, not zeroes")
        }

    @Test
    fun `every set of the last appearance comes back, in set order, with the day it started`() =
        runTest {
            val first = set("s1", 1, reps = 8, rpe = 8.0)
            val second = set("s2", 2, reps = 8)
            val third = set("s3", 3, reps = 7, rpe = 9.0)
            // Returned out of order on purpose: the use case owns the ordering, not the fake.
            val sets = SetsWithHistory(lastBefore = third, appearance = listOf(third, first, second))

            val previous = PreviousPerformanceOf(sets)(bench, alice, today)

            assertEquals(listOf(first, second, third), previous?.sets, "all three, oldest set first")
            assertEquals(start, previous?.performedAt, "the day is the appearance's first set, not its last")
        }
}
