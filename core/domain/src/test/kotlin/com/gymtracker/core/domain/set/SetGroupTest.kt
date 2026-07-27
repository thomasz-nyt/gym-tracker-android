package com.gymtracker.core.domain.set

import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

/** ADR-0009: identical consecutive sets read as "3 × 12" rather than three near-identical lines. */
class SetGroupTest {
    private val now: Instant = Instant.parse("2026-07-26T18:00:00Z")

    private fun set(
        index: Int,
        weight: Double?,
        reps: Int,
    ) = ExerciseSet("s$index", SessionExerciseId("se"), index, weight, reps, null, now)

    @Test
    fun `nothing logged groups into nothing`() {
        assertEquals(emptyList(), SetGroup.of(emptyList()))
    }

    @Test
    fun `three identical sets become one group of three`() {
        val groups = SetGroup.of(listOf(set(1, 60.0, 12), set(2, 60.0, 12), set(3, 60.0, 12)))

        assertEquals(1, groups.size)
        assertEquals(3, groups.single().count)
        assertEquals(12, groups.single().reps)
        assertEquals(60.0, groups.single().weightKg)
    }

    @Test
    fun `a working set that drops off stays three separate groups`() {
        // The normal shape of real lifting, and the reason a `sets` column was rejected.
        val groups = SetGroup.of(listOf(set(1, 60.0, 8), set(2, 60.0, 6), set(3, 55.0, 8)))

        assertEquals(3, groups.size)
        assertEquals(listOf(1, 1, 1), groups.map { it.count })
        assertEquals(listOf(8, 6, 8), groups.map { it.reps })
    }

    @Test
    fun `only consecutive identical sets merge`() {
        val groups = SetGroup.of(listOf(set(1, 60.0, 12), set(2, 70.0, 8), set(3, 60.0, 12)))

        assertEquals(listOf(1, 1, 1), groups.map { it.count }, "the first and last are not adjacent")
    }

    @Test
    fun `bodyweight sets group on reps alone`() {
        val groups = SetGroup.of(listOf(set(1, null, 15), set(2, null, 15)))

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().count)
        assertEquals(null, groups.single().weightKg)
    }

    @Test
    fun `the first set index of a group is kept for numbering`() {
        val groups = SetGroup.of(listOf(set(1, 60.0, 12), set(2, 60.0, 12), set(3, 70.0, 8)))

        assertEquals(listOf(1, 3), groups.map { it.firstSetIndex })
    }
}
