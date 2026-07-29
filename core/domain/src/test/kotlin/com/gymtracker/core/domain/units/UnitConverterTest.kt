package com.gymtracker.core.domain.units

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

/**
 * The rounding-behaviour test table `data-model.md` § Units asks for, and the round-trip
 * guarantee ADR-0006 rests on.
 */
class UnitConverterTest {
    @Test
    fun `kilograms are stored as entered, to two decimal places`() {
        val table =
            listOf(
                60.0 to 60.0,
                60.5 to 60.5,
                2.5 to 2.5,
                100.0 to 100.0,
                // A third decimal is not something the UI can produce, but the converter
                // must still be total.
                60.555 to 60.56,
                0.0 to 0.0,
            )

        table.forEach { (entered, stored) ->
            assertEquals(stored, UnitConverter.toKilograms(entered, WeightUnit.KG), "$entered kg")
        }
    }

    @Test
    fun `pounds convert to kilograms at two decimal places`() {
        val table =
            listOf(
                45.0 to 20.41,
                135.0 to 61.23,
                225.0 to 102.06,
                315.0 to 142.88,
                2.5 to 1.13,
                1.0 to 0.45,
            )

        table.forEach { (pounds, kilograms) ->
            assertEquals(kilograms, UnitConverter.toKilograms(pounds, WeightUnit.LB), "$pounds lb")
        }
    }

    @Test
    fun `display rounds to one decimal place in the members unit`() {
        assertEquals(20.4, UnitConverter.fromKilograms(20.41, WeightUnit.KG))
        assertEquals(45.0, UnitConverter.fromKilograms(20.41, WeightUnit.LB))
        assertEquals(135.0, UnitConverter.fromKilograms(61.23, WeightUnit.LB))
        assertEquals(60.0, UnitConverter.fromKilograms(60.0, WeightUnit.KG))
    }

    @Test
    fun `every plate increment a pound user can load survives the round trip`() {
        // The failure ADR-0006 exists to prevent: type 45, save, reopen, see 44.98.
        var pounds = 2.5
        while (pounds <= MAX_POUNDS) {
            val stored = UnitConverter.toKilograms(pounds, WeightUnit.LB)
            assertEquals(pounds, UnitConverter.fromKilograms(stored, WeightUnit.LB), "$pounds lb")
            pounds += 2.5
        }
    }

    @Test
    fun `every tenth of a kilogram survives the round trip`() {
        var tenths = 25
        while (tenths <= MAX_KILOGRAM_TENTHS) {
            val entered = tenths / 10.0
            val stored = UnitConverter.toKilograms(entered, WeightUnit.KG)
            assertEquals(entered, UnitConverter.fromKilograms(stored, WeightUnit.KG), "$entered kg")
            tenths++
        }
    }

    @Test
    fun `converting a weight to the other unit and back is stable`() {
        val kilograms = UnitConverter.toKilograms(100.0, WeightUnit.KG)

        val asPounds = UnitConverter.fromKilograms(kilograms, WeightUnit.LB)
        val backToKilograms = UnitConverter.toKilograms(asPounds, WeightUnit.LB)

        assertEquals(100.0, UnitConverter.fromKilograms(backToKilograms, WeightUnit.KG))
    }

    @Test
    fun `a negative weight is rejected rather than stored`() {
        // Postgres has `check (weight_kg >= 0)`; the domain should not wait for the database
        // to find out.
        assertThrows<IllegalArgumentException> { UnitConverter.toKilograms(-1.0, WeightUnit.KG) }
    }

    private companion object {
        const val MAX_POUNDS = 1000.0
        const val MAX_KILOGRAM_TENTHS = 3000
    }
}
