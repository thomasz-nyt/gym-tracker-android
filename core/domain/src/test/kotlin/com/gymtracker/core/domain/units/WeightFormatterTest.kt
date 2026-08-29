package com.gymtracker.core.domain.units

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ADR-0008: every weight reads in both units, primary first. */
class WeightFormatterTest {
    @Test
    fun `pounds first for a member who thinks in pounds`() {
        val shown = WeightFormatter.format(61.23, WeightUnit.LB)

        assertEquals("135 lb", shown.primary)
        assertEquals("61.2 kg", shown.secondary)
    }

    @Test
    fun `kilograms first for a member who thinks in kilograms`() {
        val shown = WeightFormatter.format(61.23, WeightUnit.KG)

        assertEquals("61.2 kg", shown.primary)
        assertEquals("135 lb", shown.secondary)
    }

    @Test
    fun `a trailing zero is dropped so it reads like a number a person would say`() {
        assertEquals("100 kg", WeightFormatter.format(100.0, WeightUnit.KG).primary)
        assertEquals("60 kg", WeightFormatter.format(60.0, WeightUnit.KG).primary)
    }

    @Test
    fun `a real decimal is kept`() {
        assertEquals("60.5 kg", WeightFormatter.format(60.5, WeightUnit.KG).primary)
        assertEquals("2.5 kg", WeightFormatter.format(2.5, WeightUnit.KG).primary)
    }

    @Test
    fun `no weight reads as bodyweight, never as zero`() {
        // constitution §2: absent is a first-class state.
        val shown = WeightFormatter.format(null, WeightUnit.LB)

        assertEquals("Bodyweight", shown.primary)
        assertNull(shown.secondary)
    }

    @Test
    fun `an actual zero is still zero, which is not the same as absent`() {
        // An unloaded bar or a machine's lightest setting is a real, logged value.
        assertEquals("0 kg", WeightFormatter.format(0.0, WeightUnit.KG).primary)
    }

    @Test
    fun `both numbers come from the stored kilograms, not from each other`() {
        // Deriving the secondary from the rounded primary would compound the rounding.
        val shown = WeightFormatter.format(102.06, WeightUnit.LB)

        assertEquals("225 lb", shown.primary)
        assertEquals("102.1 kg", shown.secondary)
    }

    @Test
    fun `a session's volume reads as a whole grouped number`() {
        // US-06's history row. Nobody cares about a tenth of a pound across a whole workout,
        // and "9,083 lb" is readable at a glance where "9083.0 lb" is not.
        assertEquals("9,083 lb", WeightFormatter.formatVolume(4120.0, WeightUnit.LB))
        assertEquals("4,120 kg", WeightFormatter.formatVolume(4120.0, WeightUnit.KG))
        assertEquals("600 kg", WeightFormatter.formatVolume(600.0, WeightUnit.KG))
    }

    @Test
    fun `a session with nothing weighed has no volume rather than a volume of zero`() {
        // constitution §2 again: a workout of bodyweight sets moved a load nobody recorded.
        assertNull(WeightFormatter.formatVolume(null, WeightUnit.LB))
    }

    @Test
    fun `the entry field shows a bare number in the primary unit only`() {
        // What goes into the text box the member types in — no unit suffix to delete.
        assertEquals("135", WeightFormatter.forEntry(61.23, WeightUnit.LB))
        assertEquals("61.2", WeightFormatter.forEntry(61.23, WeightUnit.KG))
        assertEquals("", WeightFormatter.forEntry(null, WeightUnit.LB))
    }

    // ADR-0011's Turn 4 amendment: a load line draws as a baseline Row of separate Texts —
    // a numeral role for the number, a word role for the unit — rather than one formatted
    // string, so a digit run and a unit word can each carry their own type role and neither
    // has to be parsed back out of `primary` at the call site.

    @Test
    fun `number and unit split apart from primary, for a member who thinks in pounds`() {
        val shown = WeightFormatter.format(61.23, WeightUnit.LB)

        assertEquals("135", shown.number)
        assertEquals("lb", shown.unit)
        assertFalse(shown.isBodyweight)
    }

    @Test
    fun `number and unit split apart from primary, for a member who thinks in kilograms`() {
        val shown = WeightFormatter.format(61.23, WeightUnit.KG)

        assertEquals("61.2", shown.number)
        assertEquals("kg", shown.unit)
        assertFalse(shown.isBodyweight)
    }

    @Test
    fun `a trailing zero is dropped from the split number too`() {
        assertEquals("100", WeightFormatter.format(100.0, WeightUnit.KG).number)
    }

    @Test
    fun `bodyweight has no number or unit to split — it is a word, not a numeral`() {
        val shown = WeightFormatter.format(null, WeightUnit.LB)

        assertNull(shown.number)
        assertNull(shown.unit)
        assertTrue(shown.isBodyweight)
    }

    @Test
    fun `an actual zero still splits a real number, unlike absent`() {
        val shown = WeightFormatter.format(0.0, WeightUnit.KG)

        assertEquals("0", shown.number)
        assertEquals("kg", shown.unit)
        assertFalse(shown.isBodyweight)
    }

    @Test
    fun `the split number is always the primary unit, matching primary itself`() {
        // number/unit describe the same reading as `primary`, never the secondary conversion —
        // ADR-0008's Turn 4 amendment withdraws the secondary from every surface that would use
        // the split (rest panel, set display, stepper, primary action button); `secondary`
        // stays a full pre-formatted string for the two surfaces (Progress, history) that still
        // show it.
        val shown = WeightFormatter.format(102.06, WeightUnit.LB)

        assertEquals("225", shown.number)
        assertEquals("lb", shown.unit)
        assertEquals("102.1 kg", shown.secondary)
    }
}
