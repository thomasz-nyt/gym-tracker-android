package com.gymtracker.core.domain.history

import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-06's "total volume", as decided by the maintainer: the sum of weight × reps over
 * weighted sets only. Bodyweight sets are excluded rather than counted as zero, because we
 * do not know what the member weighs and constitution §2 forbids inventing it.
 */
class VolumeTest {
    private val now: Instant = Instant.parse("2026-07-28T18:00:00Z")

    private fun set(
        weight: Double?,
        reps: Int,
    ) = ExerciseSet("s", SessionExerciseId("se"), 1, weight, reps, null, now)

    @Test
    fun `no sets is no volume`() {
        assertNull(Volume.of(emptyList()))
    }

    @Test
    fun `volume is weight times reps, summed`() {
        val volume = Volume.of(listOf(set(60.0, 5), set(60.0, 5), set(50.0, 10)))

        assertEquals(1100.0, volume)
    }

    @Test
    fun `a session of only bodyweight sets has no volume, not zero volume`() {
        // Showing "0 kg" would claim the member lifted nothing, which is false — they did
        // press-ups. Null means "we cannot say", which the UI renders as a dash.
        assertNull(Volume.of(listOf(set(null, 20), set(null, 15))))
    }

    @Test
    fun `bodyweight sets are skipped without dragging the total down`() {
        val volume = Volume.of(listOf(set(60.0, 5), set(null, 20)))

        assertEquals(300.0, volume, "the press-ups neither add to nor subtract from it")
    }

    @Test
    fun `an unloaded bar counts, because zero was actually logged`() {
        // Zero weight is a value someone recorded. Absent is not.
        assertEquals(0.0, Volume.of(listOf(set(0.0, 10))))
    }

    @Test
    fun `fractional kilograms carry through`() {
        assertEquals(306.15, Volume.of(listOf(set(61.23, 5))))
    }
}
