package com.gymtracker.core.domain.set

import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.sessionexercise.FakeSessionExerciseRepository
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

/** ADR-0009: "3 sets of 12" writes three rows, not one row that says three. */
class LogSetsTest {
    private val now: Instant = Instant.parse("2026-07-26T18:00:00Z")
    private val appearance = SessionExerciseId("se-1")
    private val sets = RecordingSets()
    private var next = 1

    private fun logSets() =
        LogSets(
            LogSet(sets, FakeSessionExerciseRepository(), Clock.fixed(now, ZoneOffset.UTC)) { "set-${next++}" },
        )

    @Test
    fun `one set is the default shape and writes a single row`() =
        runTest {
            val logged = logSets()(appearance, SetInput(60.0, WeightUnit.KG, 12), sets = 1)

            assertEquals(1, logged.size)
            assertEquals(1, logged.single().setIndex)
        }

    @Test
    fun `three sets of twelve writes three rows with their own indices`() =
        runTest {
            val logged = logSets()(appearance, SetInput(60.0, WeightUnit.KG, 12), sets = 3)

            assertEquals(3, logged.size)
            assertEquals(listOf(1, 2, 3), logged.map { it.setIndex })
            assertEquals(listOf(12, 12, 12), logged.map { it.reps })
        }

    @Test
    fun `rows continue from what is already logged`() =
        runTest {
            val log = logSets()
            log(appearance, SetInput(60.0, WeightUnit.KG, 12), sets = 2)

            val more = log(appearance, SetInput(60.0, WeightUnit.KG, 10), sets = 2)

            assertEquals(listOf(3, 4), more.map { it.setIndex })
        }

    @Test
    fun `each row goes through the same conversion and validation`() =
        runTest {
            val logged = logSets()(appearance, SetInput(135.0, WeightUnit.LB, 12), sets = 2)

            assertEquals(listOf(61.23, 61.23), logged.map { it.weightKg })
        }

    @Test
    fun `bulk logged sets share the time they were recorded`() =
        runTest {
            // ADR-0009: this is when they were recorded, not a guess at when each was
            // performed. The app does not know the individual times and does not invent them.
            val logged = logSets()(appearance, SetInput(60.0, WeightUnit.KG, 12), sets = 3)

            assertEquals(listOf(now, now, now), logged.map { it.performedAt })
        }

    @Test
    fun `asking for no sets is a mistake, not a silent no-op`() =
        runTest {
            assertThrows<IllegalArgumentException> {
                logSets()(appearance, SetInput(60.0, WeightUnit.KG, 12), sets = 0)
            }
        }

    private class RecordingSets : SetRepository by NoSets() {
        private val stored = mutableListOf<ExerciseSet>()

        override suspend fun nextSetIndex(sessionExerciseId: SessionExerciseId): Int =
            stored.count { it.sessionExerciseId == sessionExerciseId } + 1

        override suspend fun add(set: ExerciseSet) {
            stored += set
        }
    }
}
