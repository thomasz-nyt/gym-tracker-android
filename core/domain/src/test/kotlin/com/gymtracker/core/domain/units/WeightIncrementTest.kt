package com.gymtracker.core.domain.units

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * ADR-0016: what one press of a stepper means, in the member's own unit.
 *
 * The numbers are a product decision — the smallest change most gyms can actually load — so
 * they are pinned here rather than left in the UI to drift.
 */
class WeightIncrementTest {
    @Test
    fun `a step is the smallest plate change each unit's gym stocks`() {
        assertEquals(2.5, WeightUnit.KG.weightIncrement())
        assertEquals(5.0, WeightUnit.LB.weightIncrement())
    }

    @Test
    fun `every unit has an increment, so a new one cannot ship without a decision`() {
        WeightUnit.entries.forEach { unit ->
            assert(unit.weightIncrement() > 0) { "$unit has no usable stepper increment" }
        }
    }
}
