package com.gymtracker.core.domain.rest

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** ADR-0049: the pre-cue is ten seconds before the end, and only when that is still ahead. */
class RestCueScheduleTest {
    private val now: Instant = Instant.parse("2026-09-05T18:00:00Z")

    @Test
    fun `the cue lands ten seconds before the rest ends`() {
        val endsAt = now.plusSeconds(60)

        assertEquals(now.plusSeconds(50), RestCueSchedule.cueAt(endsAt, now))
    }

    @Test
    fun `a rest already inside its last ten seconds gets no pre-cue`() {
        // Firing it now would be a cue for a moment that is not "ten seconds out"; firing it in
        // the past is a cue for nothing. Absent is the honest answer.
        assertNull(RestCueSchedule.cueAt(now.plusSeconds(10), now), "exactly ten seconds left is not ahead")
        assertNull(RestCueSchedule.cueAt(now.plusSeconds(4), now))
    }

    @Test
    fun `a rest shorter than the lead gets no pre-cue either`() {
        assertNull(RestCueSchedule.cueAt(now.plusSeconds(8), now.minusSeconds(1)))
    }
}
