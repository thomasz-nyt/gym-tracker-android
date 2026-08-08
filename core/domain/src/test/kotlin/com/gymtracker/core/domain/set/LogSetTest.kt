package com.gymtracker.core.domain.set

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** US-03: prefill from the last time this exercise was performed, and validation. */
class LogSetTest {
    private val now: Instant = Instant.parse("2026-07-26T18:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val alice = UserId("alice")
    private val bench = ExerciseId("bench")
    private val appearance = SessionExerciseId("se-1")

    private val sets = FakeSets()
    private var nextId = 1

    private fun logSet() = LogSet(sets, clock) { "set-${nextId++}" }

    private fun prefill() = PrefillFromLastSet(sets)

    @Test
    fun `with no prior set there is nothing to prefill`() =
        runTest {
            assertNull(prefill()(bench, alice, WeightUnit.KG))
        }

    @Test
    fun `prefill offers the weight and reps of the last set, in the members unit`() =
        runTest {
            sets.seed(
                ExerciseSet(
                    id = "old",
                    sessionExerciseId = SessionExerciseId("se-old"),
                    setIndex = 1,
                    weightKg = 61.23,
                    reps = 5,
                    rpe = 8.0,
                    performedAt = now.minus(Duration.ofDays(3)),
                ),
            )
            sets.lastFor[bench to alice] = "old"

            val inPounds = prefill()(bench, alice, WeightUnit.LB)

            assertEquals(135.0, inPounds?.weight, "61.23 kg is 135 lb")
            assertEquals(5, inPounds?.reps)
            assertEquals(61.2, prefill()(bench, alice, WeightUnit.KG)?.weight)
        }

    @Test
    fun `prefill does not carry RPE forward`() =
        runTest {
            // US-03 prefills weight and reps only. RPE is how hard *that* set felt, so
            // repeating it would be inventing data (constitution §2, honest data).
            sets.seed(
                ExerciseSet("old", SessionExerciseId("se-old"), 1, 61.23, 5, 8.0, now),
            )
            sets.lastFor[bench to alice] = "old"

            assertNull(prefill()(bench, alice, WeightUnit.KG)?.rpe)
        }

    @Test
    fun `logging a set stores canonical kilograms and stamps the time`() =
        runTest {
            val logged = logSet()(appearance, 135.0, WeightUnit.LB, reps = 5, rpe = null)

            assertEquals(61.23, logged.weightKg)
            assertEquals(5, logged.reps)
            assertEquals(now, logged.performedAt)
            assertEquals(1, logged.setIndex)
            assertEquals(listOf(logged), sets.all)
        }

    @Test
    fun `set indices increment within one appearance of an exercise`() =
        runTest {
            val log = logSet()

            log(appearance, 60.0, WeightUnit.KG, reps = 5, rpe = null)
            val second = log(appearance, 60.0, WeightUnit.KG, reps = 5, rpe = null)

            assertEquals(2, second.setIndex)
        }

    @Test
    fun `a second appearance of the same exercise starts its own indices`() =
        runTest {
            val log = logSet()
            log(appearance, 60.0, WeightUnit.KG, reps = 5, rpe = null)

            val other = log(SessionExerciseId("se-2"), 60.0, WeightUnit.KG, reps = 5, rpe = null)

            assertEquals(1, other.setIndex, "ADR-0004: indices are per appearance, not per exercise")
        }

    @Test
    fun `a bodyweight set records no weight rather than zero`() =
        runTest {
            val logged = logSet()(appearance, null, WeightUnit.KG, reps = 12, rpe = null)

            assertNull(logged.weightKg, "absent is a first-class state, never zero")
        }

    @Test
    fun `reps must be at least one`() =
        runTest {
            assertThrows<IllegalArgumentException> {
                logSet()(appearance, 60.0, WeightUnit.KG, reps = 0, rpe = null)
            }
        }

    @Test
    fun `rpe must be between 5 and 10 in half steps`() =
        runTest {
            val log = logSet()

            assertEquals(7.5, log(appearance, 60.0, WeightUnit.KG, 5, rpe = 7.5).rpe)
            assertThrows<IllegalArgumentException> { log(appearance, 60.0, WeightUnit.KG, 5, rpe = 4.5) }
            assertThrows<IllegalArgumentException> { log(appearance, 60.0, WeightUnit.KG, 5, rpe = 10.5) }
            assertThrows<IllegalArgumentException> { log(appearance, 60.0, WeightUnit.KG, 5, rpe = 7.3) }
        }

    private class FakeSets : SetRepository {
        private val state = MutableStateFlow(emptyList<ExerciseSet>())
        val lastFor = mutableMapOf<Pair<ExerciseId, UserId>, String>()

        val all: List<ExerciseSet> get() = state.value

        fun seed(set: ExerciseSet) {
            state.value = state.value + set
        }

        override fun observeForSessionExercise(sessionExerciseId: SessionExerciseId): Flow<List<ExerciseSet>> =
            state.map { rows -> rows.filter { it.sessionExerciseId == sessionExerciseId }.sortedBy { it.setIndex } }

        // Nothing in US-03 reaches sets by session; history does, and it has its own fake.
        override fun observeForSessions(sessionIds: List<SessionId>): Flow<List<ExerciseSet>> = state

        override suspend fun lastSetOf(
            exerciseId: ExerciseId,
            member: UserId,
        ): ExerciseSet? = lastFor[exerciseId to member]?.let { id -> state.value.firstOrNull { it.id == id } }

        override suspend fun lastSetOfBefore(
            exerciseId: ExerciseId,
            member: UserId,
            excludingSessionId: SessionId,
        ): ExerciseSet? = null

        override suspend fun lastSetAtInSession(sessionId: SessionId): Instant? =
            state.value.maxOfOrNull { it.performedAt }

        override suspend fun nextSetIndex(sessionExerciseId: SessionExerciseId): Int =
            state.value.count { it.sessionExerciseId == sessionExerciseId } + 1

        override suspend fun add(set: ExerciseSet) {
            state.value = state.value + set
        }

        override suspend fun update(set: ExerciseSet) {
            state.value = state.value.map { if (it.id == set.id) set else it }
        }

        override suspend fun delete(id: String): ExerciseSet? {
            val existing = state.value.firstOrNull { it.id == id } ?: return null
            state.value = state.value.filterNot { it.id == id }
            return existing
        }
    }
}
