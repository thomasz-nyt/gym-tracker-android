package com.gymtracker.core.domain.set

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.UserId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-29: what a routine shows beside a movement is what you actually lifted.
 *
 * Deliberately separate from [PrefillFromLastSet], which answers a different question. A
 * prefill is a number about to be *typed*, so it is converted to the member's unit and has no
 * date. This is a number about to be *read as history*, so it keeps kilograms for the
 * formatter and carries the date that makes it honest — "Last Tue" rather than a bare figure
 * that could be mistaken for a target.
 */
class LastPerformanceOfTest {
    private val now: Instant = Instant.parse("2026-08-08T18:00:00Z")
    private val alice = UserId("alice")
    private val bench = ExerciseId("bench")
    private val appearance = SessionExerciseId("se-1")

    private val sets = FakeSetRepository()
    private val lastPerformanceOf = LastPerformanceOf(sets)

    @Test
    fun `a movement never performed has no last time at all`() =
        runTest {
            // The US-13 absence pattern: nothing rendered, nothing fabricated (§2.4).
            assertNull(lastPerformanceOf(bench, alice))
        }

    @Test
    fun `the last performance is the member's most recent set of that movement`() =
        runTest {
            val logged = ExerciseSet("s1", appearance, 1, 61.23, 8, null, now)
            sets.add(logged)
            sets.lastFor[bench] = logged.id

            val last = lastPerformanceOf(bench, alice)

            assertEquals(61.23, last?.weightKg, "kilograms, so the formatter decides the unit")
            assertEquals(8, last?.reps)
            assertEquals(now, last?.performedAt, "the date is what makes it read as history")
        }

    @Test
    fun `a bodyweight movement keeps its null weight rather than reporting zero`() =
        runTest {
            val logged = ExerciseSet("s1", appearance, 1, null, 12, null, now)
            sets.add(logged)
            sets.lastFor[bench] = logged.id

            val last = lastPerformanceOf(bench, alice)

            assertNull(last?.weightKg, "no weight is not a weight of zero (§2.4)")
            assertEquals(12, last?.reps)
        }
}
