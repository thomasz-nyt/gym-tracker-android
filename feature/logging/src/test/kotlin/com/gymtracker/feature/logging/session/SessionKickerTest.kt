package com.gymtracker.feature.logging.session

import com.gymtracker.core.domain.model.MovementTarget
import org.junit.Test
import kotlin.test.assertEquals

/**
 * US-54 / ADR-0046: the open exercise's kicker and the other-exercises section label, both pure
 * functions of state already on [com.gymtracker.core.domain.session.SessionProgress] and
 * [com.gymtracker.core.domain.model.SessionExercise.target] — plain JVM tests, no Compose
 * runtime needed, the same reasoning [com.gymtracker.core.designsystem.theme.GymTypographyTest]
 * gives for why this module needs no Robolectric.
 */
class SessionKickerTest {
    @Test
    fun `an exercise with a sets target shows exercise and set position`() {
        val target = MovementTarget(sets = 3, reps = 8, weightKg = 47.6)
        assertEquals(
            "EXERCISE 2 OF 4 · SET 1 OF 3",
            sessionKicker(exerciseNumber = 2, movementsTotal = 4, target = target, setsLogged = 0),
        )
        assertEquals(
            "EXERCISE 2 OF 4 · SET 2 OF 3",
            sessionKicker(exerciseNumber = 2, movementsTotal = 4, target = target, setsLogged = 1),
        )
    }

    @Test
    fun `an exercise with no target shows CURRENT regardless of position`() {
        assertEquals(
            "CURRENT",
            sessionKicker(exerciseNumber = 1, movementsTotal = 1, target = null, setsLogged = 0),
        )
        assertEquals(
            "CURRENT",
            sessionKicker(exerciseNumber = 3, movementsTotal = 5, target = null, setsLogged = 2),
        )
    }

    @Test
    fun `a target with no sets count shows CURRENT, not a fabricated set count`() {
        // MovementTarget's fields are independently nullable (reps or weight can be set without
        // sets) — only a real sets count backs "SET x OF y".
        val target = MovementTarget(sets = null, reps = 8, weightKg = 47.6)
        assertEquals(
            "CURRENT",
            sessionKicker(exerciseNumber = 1, movementsTotal = 1, target = target, setsLogged = 0),
        )
    }

    @Test
    fun `the other-exercises section label reads THEN for a plan-backed session, ALSO TODAY otherwise`() {
        assertEquals("THEN", otherExercisesSectionLabel(orderIsAPlan = true))
        assertEquals("ALSO TODAY", otherExercisesSectionLabel(orderIsAPlan = false))
    }
}
