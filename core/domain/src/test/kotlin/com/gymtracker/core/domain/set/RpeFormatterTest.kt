package com.gymtracker.core.domain.set

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** US-60: one spelling of an RPE for every surface that shows a set. */
class RpeFormatterTest {
    @Test
    fun `a whole number drops its point zero`() {
        assertEquals("8", RpeFormatter.number(8.0))
        assertEquals("10", RpeFormatter.number(10.0))
        assertEquals("5", RpeFormatter.number(5.0))
    }

    @Test
    fun `a half step keeps its half`() {
        assertEquals("8.5", RpeFormatter.number(8.5))
        assertEquals("9.5", RpeFormatter.number(9.5))
    }

    @Test
    fun `the notation's prefix is the at sign`() {
        assertEquals("@8", RpeFormatter.at(8.0))
        assertEquals("@7.5", RpeFormatter.at(7.5))
    }

    @Test
    fun `the scale is exactly what a write accepts, and nothing more`() {
        // Eleven half steps from 5 to 10 — every one passes SetValidation, and the values just
        // outside it are exactly the ones the scale stops short of, so a chip row built from
        // this list can never offer a value LogSet would then throw on.
        assertEquals(11, RpeFormatter.scale.size)
        assertEquals(SetValidation.MIN_RPE, RpeFormatter.scale.first())
        assertEquals(SetValidation.MAX_RPE, RpeFormatter.scale.last())
        RpeFormatter.scale.forEach { SetValidation.requireValidRpe(it) }
        assertEquals(
            listOf("5", "5.5", "6", "6.5", "7", "7.5", "8", "8.5", "9", "9.5", "10"),
            RpeFormatter.scale.map(RpeFormatter::number),
        )
    }
}
