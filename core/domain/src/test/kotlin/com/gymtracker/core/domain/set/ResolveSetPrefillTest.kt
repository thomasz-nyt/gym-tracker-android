package com.gymtracker.core.domain.set

import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.units.WeightUnit
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * US-37 (ADR-0031, superseding ADR-0027's target-first order): last performed, then the
 * routine's target, then a floor of 12 reps — weight never floors. Sets floors at 3 only once
 * a target exists; with no target at all it stays ADR-0009's original 1 — confirmed against
 * `TwoTapSetLoggingTest` on-device, which confirms without checking the sheet and would log
 * extra sets if this ever changed silently.
 */
class ResolveSetPrefillTest {
    @Test
    fun `with neither history nor a target, reps float to 12, sets stay at ADR-0009's 1, weight blank`() {
        val resolved = ResolveSetPrefill(history = null, target = null, unit = WeightUnit.LB)

        assertNull(resolved.weight)
        assertEquals(12, resolved.reps)
        assertEquals(1, resolved.sets)
        assertFalse(resolved.fromHistory)
    }

    @Test
    fun `history alone fills weight and reps, sets stays at 1 with no target`() {
        val history = SetPrefill(weight = 135.0, reps = 8)

        val resolved = ResolveSetPrefill(history = history, target = null, unit = WeightUnit.LB)

        assertEquals(135.0, resolved.weight)
        assertEquals(8, resolved.reps)
        assertEquals(1, resolved.sets)
        assertTrue(resolved.fromHistory)
    }

    @Test
    fun `a target alone fills every field it carries`() {
        // KG display rounds to one decimal (UnitConverter), so 61.2 round-trips exactly.
        val target = MovementTarget(sets = 4, reps = 10, weightKg = 61.2)

        val resolved = ResolveSetPrefill(history = null, target = target, unit = WeightUnit.KG)

        assertEquals(61.2, resolved.weight)
        assertEquals(10, resolved.reps)
        assertEquals(4, resolved.sets)
        assertFalse(resolved.fromHistory)
    }

    @Test
    fun `a target with no explicit set count floors sets at 3, not 1`() {
        val target = MovementTarget(sets = null, reps = 8, weightKg = null)

        val resolved = ResolveSetPrefill(history = null, target = target, unit = WeightUnit.LB)

        assertEquals(3, resolved.sets)
    }

    @Test
    fun `history wins over a target for weight and reps, per ADR-0031`() {
        val history = SetPrefill(weight = 100.0, reps = 8)
        val target = MovementTarget(sets = 4, reps = 12, weightKg = 47.63)

        val resolved = ResolveSetPrefill(history = history, target = target, unit = WeightUnit.LB)

        assertEquals(
            message = "history's weight must win, not the target's",
            expected = 100.0,
            actual = resolved.weight,
        )
        assertEquals(message = "history's reps must win, not the target's", expected = 8, actual = resolved.reps)
        assertTrue(resolved.fromHistory)
    }

    @Test
    fun `sets always comes from the target, never from history, per ADR-0009`() {
        val history = SetPrefill(weight = 100.0, reps = 8)
        val target = MovementTarget(sets = 5, reps = null, weightKg = null)

        val resolved = ResolveSetPrefill(history = history, target = target, unit = WeightUnit.LB)

        assertEquals(5, resolved.sets)
    }

    @Test
    fun `a target's weight is converted into the member's unit`() {
        // 100 kg is 220.5 lb to one decimal place — UnitConverter's own rounding, not reinvented.
        val target = MovementTarget(sets = 3, reps = 8, weightKg = 100.0)

        val resolved = ResolveSetPrefill(history = null, target = target, unit = WeightUnit.LB)

        assertEquals(220.5, resolved.weight)
    }

    @Test
    fun `a bodyweight history entry leaves weight blank without losing reps`() {
        val history = SetPrefill(weight = null, reps = 15)

        val resolved = ResolveSetPrefill(history = history, target = null, unit = WeightUnit.LB)

        assertNull(resolved.weight)
        assertEquals(15, resolved.reps)
        assertTrue(resolved.fromHistory)
    }

    @Test
    fun `a target's reps fill in when history is bodyweight-only and carries no reps opinion`() {
        // history always has real reps per SetPrefill's own contract, so this exercises the
        // reps-absent-from-history path a different way: history null, target reps present.
        val target = MovementTarget(sets = 3, reps = 12, weightKg = null)

        val resolved = ResolveSetPrefill(history = null, target = target, unit = WeightUnit.LB)

        assertEquals(12, resolved.reps)
        assertNull(resolved.weight)
    }
}
