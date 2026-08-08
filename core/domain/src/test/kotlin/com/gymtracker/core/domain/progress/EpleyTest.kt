package com.gymtracker.core.domain.progress

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-16: estimated 1RM, by Epley, against a hand-computed table.
 *
 * `specs/testing-strategy.md` asks for progression math to be table-driven with figures
 * worked out by hand rather than by running the code and pasting the output, which proves
 * only that the code agrees with itself.
 *
 * Epley: `1RM = w × (1 + reps / 30)`.
 */
class EpleyTest {
    @Test
    fun `the table, computed by hand`() {
        // weight, reps, expected — each expected value derived from w × (1 + reps/30):
        //   100 × (1 + 5/30)  = 100 × 1.1666… = 116.666…
        //   100 × (1 + 10/30) = 100 × 1.3333… = 133.333…
        //    60 × (1 + 8/30)  =  60 × 1.2666… =  76.0
        //    61.23 × (1 + 8/30) = 61.23 × 1.26666… = 77.558
        //   140 × (1 + 3/30)  = 140 × 1.1     = 154.0
        val table =
            listOf(
                Triple(100.0, 5, 116.66666666666667),
                Triple(100.0, 10, 133.33333333333334),
                Triple(60.0, 8, 76.0),
                Triple(61.23, 8, 77.558),
                Triple(140.0, 3, 154.0),
            )

        table.forEach { (weight, reps, expected) ->
            assertEquals(expected, Epley.oneRepMax(weight, reps)!!, 1e-9, "$weight kg × $reps")
        }
    }

    @Test
    fun `a single rep is its own one-rep max, not one point three percent more`() =
        // The formula would say 103.33 for a 100 kg single, which is a heavier number than the
        // one actually lifted. Reporting that as a *maximum* would be inventing a lift
        // (constitution §2.4), so a single is returned unchanged.
        assertEquals(100.0, Epley.oneRepMax(100.0, 1)!!, 1e-9)

    @Test
    fun `a bodyweight set has no estimate at all`() =
        // No load, so there is nothing to estimate from. Null rather than zero: absence is a
        // state, and zero would claim a one-rep max of nothing.
        assertNull(Epley.oneRepMax(null, 8))

    @Test
    fun `zero or negative reps have no estimate`() {
        assertNull(Epley.oneRepMax(100.0, 0))
        assertNull(Epley.oneRepMax(100.0, -3))
    }

    @Test
    fun `the estimate rises with reps at the same load`() {
        val five = Epley.oneRepMax(100.0, 5)!!
        val eight = Epley.oneRepMax(100.0, 8)!!

        assert(eight > five) { "more reps at the same weight implies a higher maximum" }
    }

    @Test
    fun `very high reps are still computed rather than refused`() =
        // Epley is unreliable past about 10 reps, which is a labelling problem for the screen
        // (US-16 says the number is shown as an estimate), not a reason for the domain to
        // start deciding which of the member's real sets count.
        assertEquals(200.0, Epley.oneRepMax(100.0, 30)!!, 1e-9)
}
