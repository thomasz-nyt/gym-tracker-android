package com.gymtracker.core.domain.units

import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals

/** ADR-0050 lifts the "m:ss" readout into the domain so a target's rest reads the same everywhere. */
class MinutesSecondsTest {
    @Test
    fun `ninety seconds reads as one minute thirty`() {
        assertEquals("1:30", MinutesSeconds.format(Duration.ofSeconds(90)))
    }

    @Test
    fun `seconds always take two digits, minutes take what they need`() {
        assertEquals("0:05", MinutesSeconds.format(Duration.ofSeconds(5)))
        assertEquals("3:00", MinutesSeconds.format(Duration.ofMinutes(3)))
        assertEquals("12:07", MinutesSeconds.format(Duration.ofSeconds(727)))
    }

    @Test
    fun `nothing reads as zero, not as blank`() {
        assertEquals("0:00", MinutesSeconds.format(Duration.ZERO))
    }
}
