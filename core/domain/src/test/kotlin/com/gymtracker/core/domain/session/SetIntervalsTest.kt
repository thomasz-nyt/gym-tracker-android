package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * US-44, `Redesign.dc.html` Turn 3 (frame `3g`): "how long since the last set", derived from
 * `performed_at` alone — no schema change, and correct retroactively on every session already
 * logged. Table-driven against hand-computed figures, per `specs/testing-strategy.md`.
 */
class SetIntervalsTest {
    private val start: Instant = Instant.parse("2026-08-01T17:00:00Z")

    private fun set(
        id: String,
        appearance: String,
        index: Int,
        offsetSeconds: Long,
    ) = ExerciseSet(
        id = id,
        sessionExerciseId = SessionExerciseId(appearance),
        setIndex = index,
        weightKg = 60.0,
        reps = 8,
        rpe = null,
        performedAt = start.plusSeconds(offsetSeconds),
    )

    @Test
    fun `the first set of a session has no interval`() {
        val sets = listOf(set("s1", "se-1", 1, offsetSeconds = 0))

        assertEquals(emptyMap(), SetIntervals.of(sets))
    }

    @Test
    fun `each set's interval is the gap since the one logged before it`() {
        val sets =
            listOf(
                set("s1", "se-1", 1, offsetSeconds = 0),
                set("s2", "se-1", 2, offsetSeconds = 124), // +2:04
                set("s3", "se-1", 3, offsetSeconds = 236), // +1:52 after that
            )

        val intervals = SetIntervals.of(sets)

        assertEquals(Duration.ofSeconds(124), intervals["s2"])
        assertEquals(Duration.ofSeconds(112), intervals["s3"])
        assertEquals(2, intervals.size, "the first set has no predecessor to measure from")
    }

    @Test
    fun `intervals span movements -- the walk to the next machine counts`() {
        val sets =
            listOf(
                set("s1", "se-bench", 1, offsetSeconds = 0),
                set("s2", "se-bench", 2, offsetSeconds = 100),
                // +3:41 walking to the cable stack after the last bench set.
                set("s3", "se-rows", 1, offsetSeconds = 100 + 221),
            )

        assertEquals(Duration.ofSeconds(221), SetIntervals.of(sets)["s3"])
    }

    @Test
    fun `sets logged within a few seconds of each other -- bulk entry -- get no interval`() {
        // ADR-0009: LogSets loops logSet() N times with near-identical clock.instant() values.
        // Rendering "+0:00" for that would read as a bug, the same complaint the redesign audit
        // made about the history summary line.
        val sets =
            listOf(
                set("s1", "se-1", 1, offsetSeconds = 0),
                set("s2", "se-1", 2, offsetSeconds = 1),
                set("s3", "se-1", 3, offsetSeconds = 2),
            )

        assertEquals(emptyMap(), SetIntervals.of(sets))
    }

    @Test
    fun `an interval right at the suppression floor is kept, one second under it is not`() {
        val atFloor =
            listOf(
                set("s1", "se-1", 1, offsetSeconds = 0),
                set("s2", "se-1", 2, offsetSeconds = 5),
            )
        val underFloor =
            listOf(
                set("s1", "se-1", 1, offsetSeconds = 0),
                set("s2", "se-1", 2, offsetSeconds = 4),
            )

        assertEquals(Duration.ofSeconds(5), SetIntervals.of(atFloor)["s2"])
        assertTrue(SetIntervals.of(underFloor)["s2"] == null)
    }

    @Test
    fun `an out-of-order input is sorted by performedAt, not trusted`() {
        val sets =
            listOf(
                set("s3", "se-1", 3, offsetSeconds = 236),
                set("s1", "se-1", 1, offsetSeconds = 0),
                set("s2", "se-1", 2, offsetSeconds = 124),
            )

        val intervals = SetIntervals.of(sets)

        assertEquals(Duration.ofSeconds(124), intervals["s2"])
        assertEquals(Duration.ofSeconds(112), intervals["s3"])
    }

    @Test
    fun `the average excludes the lead-in from whatever exercise came before it`() {
        val benchSets =
            listOf(
                set("s1", "se-bench", 1, offsetSeconds = 0),
                set("s2", "se-bench", 2, offsetSeconds = 124),
                set("s3", "se-bench", 3, offsetSeconds = 236),
            )
        // A set from another exercise, ten minutes earlier -- s1's own interval, if it had
        // one, would be a ten-minute lead-in, not this exercise's internal pace.
        val sessionSets = benchSets + set("s0", "se-other", 1, offsetSeconds = -600)
        val intervals = SetIntervals.of(sessionSets)

        // (2:04 + 1:52) / 2 = 1:58, matching Redesign.dc.html's 3g frame exactly.
        assertEquals(Duration.ofSeconds(118), SetIntervals.average(benchSets, intervals))
    }

    @Test
    fun `an exercise with only one set has no average`() {
        val sets = listOf(set("s1", "se-1", 1, offsetSeconds = 0))

        assertNull(SetIntervals.average(sets, SetIntervals.of(sets)))
    }

    @Test
    fun `an exercise with no sets has no average`() {
        assertNull(SetIntervals.average(emptyList(), emptyMap()))
    }

    @Test
    fun `a suppressed interval inside an exercise is excluded from its average too`() {
        val sets =
            listOf(
                set("s1", "se-1", 1, offsetSeconds = 0),
                set("s2", "se-1", 2, offsetSeconds = 2), // suppressed, bulk-logged
                set("s3", "se-1", 3, offsetSeconds = 122), // +2:00, real
            )

        assertEquals(Duration.ofSeconds(120), SetIntervals.average(sets, SetIntervals.of(sets)))
    }
}
