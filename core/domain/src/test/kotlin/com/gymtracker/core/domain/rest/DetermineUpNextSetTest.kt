package com.gymtracker.core.domain.rest

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.sessionexercise.FakeSessionExerciseRepository
import com.gymtracker.core.domain.set.FakeSetRepository
import com.gymtracker.core.domain.set.PrefillFromLastSet
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** ADR-0023: what the rest panel shows next, derived fresh from the database every time. */
class DetermineUpNextSetTest {
    private val now: Instant = Instant.parse("2026-08-08T18:00:00Z")
    private val alice = UserId("alice")
    private val bench = ExerciseId("bench")
    private val squat = ExerciseId("squat")
    private val thisSession = SessionId("today")

    private val sessionExercises = FakeSessionExerciseRepository()
    private val sets = FakeSetRepository()
    private val determineUpNext = DetermineUpNextSet(sessionExercises, sets, PrefillFromLastSet(sets))

    private suspend fun appear(
        id: String,
        exercise: ExerciseId,
        position: Int,
    ): SessionExercise {
        val appearance = SessionExercise(SessionExerciseId(id), thisSession, exercise, position)
        sessionExercises.add(appearance)
        sets.belongsTo(appearance)
        return appearance
    }

    @Test
    fun `there is nothing up next when the session has no exercises`() =
        runTest {
            assertNull(determineUpNext(thisSession, alice, WeightUnit.KG))
        }

    @Test
    fun `there is nothing up next until a set has actually been logged`() =
        runTest {
            appear("se-1", bench, 1)

            assertNull(determineUpNext(thisSession, alice, WeightUnit.KG), "no set, no rest, nothing to show")
        }

    @Test
    fun `up next follows the exercise of the most recently logged set, not the first appearance`() =
        runTest {
            val benchAppearance = appear("se-1", bench, 1)
            val squatAppearance = appear("se-2", squat, 2)
            sets.add(ExerciseSet("s1", benchAppearance.id, 1, 60.0, 5, null, now.minusSeconds(90)))
            val squatSet = ExerciseSet("s2", squatAppearance.id, 1, 100.0, 5, null, now)
            sets.add(squatSet)
            sets.lastFor[squat] = squatSet.id

            val next = determineUpNext(thisSession, alice, WeightUnit.KG)

            assertEquals(squatAppearance.id, next?.sessionExerciseId)
            assertEquals(squat, next?.exerciseId)
        }

    @Test
    fun `the set number is a plain count, never a target`() =
        runTest {
            val appearance = appear("se-1", bench, 1)
            val first = ExerciseSet("s1", appearance.id, 1, 60.0, 5, null, now.minusSeconds(90))
            val second = ExerciseSet("s2", appearance.id, 2, 60.0, 5, null, now)
            sets.add(first)
            sets.add(second)
            sets.lastFor[bench] = second.id

            val next = determineUpNext(thisSession, alice, WeightUnit.KG)

            // Two sets logged, so the next one is 3 — never "3 of anything": UpNextSet has no
            // field a "of N" could even be rendered from (ADR-0023).
            assertEquals(3, next?.setNumber)
        }

    @Test
    fun `the prefill is the member's most recent set of the exercise, in their unit`() =
        runTest {
            val appearance = appear("se-1", bench, 1)
            val logged = ExerciseSet("s1", appearance.id, 1, 61.23, 5, null, now)
            sets.add(logged)
            sets.lastFor[bench] = logged.id

            val next = determineUpNext(thisSession, alice, WeightUnit.LB)

            assertEquals(135.0, next?.prefill?.weight, "61.23 kg is 135 lb")
            assertEquals(5, next?.prefill?.reps)
        }

    @Test
    fun `there is no comparison when there is no earlier session for this exercise`() =
        runTest {
            val appearance = appear("se-1", bench, 1)
            val logged = ExerciseSet("s1", appearance.id, 1, 60.0, 5, null, now)
            sets.add(logged)
            sets.lastFor[bench] = logged.id
            // sets.lastBeforeFor is deliberately left empty: a first-ever set of this movement.

            val next = determineUpNext(thisSession, alice, WeightUnit.KG)

            assertNull(next?.comparison, "US-13's absence pattern: nothing rendered, nothing fabricated")
        }

    @Test
    fun `the comparison is the most recent set of the exercise from an earlier session`() =
        runTest {
            val appearance = appear("se-1", bench, 1)
            val logged = ExerciseSet("s1", appearance.id, 1, 60.0, 5, null, now)
            sets.add(logged)
            sets.lastFor[bench] = logged.id
            val earlier =
                ExerciseSet("old", SessionExerciseId("se-old"), 1, 43.0, 8, null, now.minus(Duration.ofDays(7)))
            sets.add(earlier)
            sets.lastBeforeFor[bench] = earlier.id

            val next = determineUpNext(thisSession, alice, WeightUnit.KG)

            assertEquals(earlier, next?.comparison)
        }
}
