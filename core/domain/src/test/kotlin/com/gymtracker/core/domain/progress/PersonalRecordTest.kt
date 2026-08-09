package com.gymtracker.core.domain.progress

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.session.FakeSessionRepository
import com.gymtracker.core.domain.sessionexercise.FakeSessionExerciseRepository
import com.gymtracker.core.domain.set.FakeSetRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * US-18 and ADR-0025: a personal record is the heaviest load ever lifted for a given exercise
 * **at a given rep count**.
 *
 * Every figure below is hand-computed, per `specs/testing-strategy.md`. The rules that are easy
 * to get wrong, and so are each pinned by their own test: the first time at a rep count is not
 * a record, equalling is not beating, and a bodyweight set sets nothing.
 */
class PersonalRecordTest {
    private val alice = UserId("alice")
    private val bob = UserId("bob")
    private val bench = ExerciseId("bench")
    private val squat = ExerciseId("squat")

    private val sessions = FakeSessionRepository()
    private val sessionExercises = FakeSessionExerciseRepository()
    private val sets = FakeSetRepository()

    private val recordsOf = PersonalRecordsOf(sessions, sessionExercises, sets, ZoneOffset.UTC)
    private val detect = DetectPersonalRecord(recordsOf, ZoneOffset.UTC)

    private var nextAppearance = 1
    private var nextSet = 1

    /** A finished session on [date], with the given lifts as (weight, reps). */
    private suspend fun session(
        date: String,
        exerciseId: ExerciseId,
        lifts: List<Pair<Double?, Int>>,
        member: UserId = alice,
        finished: Boolean = true,
    ): SessionExerciseId {
        val sessionId = SessionId("s-$date-$nextAppearance")
        val startedAt = Instant.parse("${date}T18:00:00Z")
        sessions.startSession(
            WorkoutSession(
                sessionId,
                member,
                null,
                startedAt,
                if (finished) startedAt.plusSeconds(3600) else null,
                null,
            ),
        )
        val appearance = SessionExerciseId("se-${nextAppearance++}")
        val row = SessionExercise(appearance, sessionId, exerciseId, 1)
        sessionExercises.add(row)
        sets.belongsTo(row)
        lifts.forEachIndexed { index, (weight, reps) ->
            sets.add(ExerciseSet("set-${nextSet++}", appearance, index + 1, weight, reps, null, startedAt))
        }
        return appearance
    }

    /** A set that has not been saved, for asking "would this be a record". */
    private fun candidate(
        appearance: SessionExerciseId,
        weightKg: Double?,
        reps: Int,
        date: String = "2026-08-10",
    ) = ExerciseSet(
        id = "candidate",
        sessionExerciseId = appearance,
        setIndex = 99,
        weightKg = weightKg,
        reps = reps,
        rpe = null,
        performedAt = Instant.parse("${date}T18:00:00Z"),
    )

    // ---- the list (US-18's second criterion) ----

    @Test
    fun `a member who has never done the exercise has no records`() =
        runTest {
            assertTrue(recordsOf(bench, alice).isEmpty())
        }

    @Test
    fun `each rep count keeps its own record`() =
        runTest {
            // ADR-0025's whole point: 100x8 does not have to beat 105x1 to be a record.
            session("2026-07-12", bench, listOf(105.0 to 1))
            session("2026-08-08", bench, listOf(100.0 to 8))

            val records = recordsOf(bench, alice)

            assertEquals(
                mapOf(1 to 105.0, 8 to 100.0),
                records.associate { it.reps to it.weightKg },
            )
        }

    @Test
    fun `the record at a rep count is the heaviest ever lifted there, not the latest`() =
        runTest {
            session("2026-07-12", bench, listOf(100.0 to 5))
            session("2026-08-08", bench, listOf(95.0 to 5))

            val record = recordsOf(bench, alice).single()

            assertEquals(100.0, record.weightKg)
            assertEquals(LocalDate.parse("2026-07-12"), record.achievedOn, "the day it was lifted")
        }

    @Test
    fun `records come back by rep count`() =
        runTest {
            session("2026-07-12", bench, listOf(105.0 to 1, 100.0 to 8, 102.5 to 5))

            assertEquals(listOf(1, 5, 8), recordsOf(bench, alice).map { it.reps })
        }

    @Test
    fun `records are per exercise`() =
        runTest {
            session("2026-07-12", bench, listOf(100.0 to 5))
            session("2026-07-13", squat, listOf(140.0 to 5))

            assertEquals(100.0, recordsOf(bench, alice).single().weightKg)
            assertEquals(140.0, recordsOf(squat, alice).single().weightKg)
        }

    @Test
    fun `one member's lifting is not another's record`() =
        runTest {
            session("2026-07-12", bench, listOf(140.0 to 5), member = bob)

            assertTrue(recordsOf(bench, alice).isEmpty())
        }

    @Test
    fun `a bodyweight set sets no record`() =
        runTest {
            // No load to compare. Reading the missing weight as zero would tie every bodyweight
            // set for last place forever (constitution §2.4).
            session("2026-07-12", bench, listOf(null to 12))

            assertTrue(recordsOf(bench, alice).isEmpty())
        }

    @Test
    fun `the workout in progress counts, because a record happens when it is lifted`() =
        runTest {
            // Unlike the trend chart, which reads finished sessions only: a chart point is a day
            // that is over, but a record is a lift, and it is set the moment it is performed.
            session("2026-08-08", bench, listOf(120.0 to 5), finished = false)

            assertEquals(120.0, recordsOf(bench, alice).single().weightKg)
        }

    // ---- detection on save (US-18's first criterion) ----

    @Test
    fun `beating the load at that rep count is a record`() =
        runTest {
            val today = session("2026-07-12", bench, listOf(100.0 to 5))

            val record = detect(candidate(today, 102.5, reps = 5), bench, alice)

            assertEquals(102.5, record?.weightKg)
            assertEquals(5, record?.reps)
            assertEquals(LocalDate.parse("2026-08-10"), record?.achievedOn)
        }

    @Test
    fun `the first time at a rep count is not a record`() =
        runTest {
            // ADR-0025: a record needs a previous value to beat. "You have not done this before"
            // is a fact, not an achievement — and celebrating it makes the first workout
            // wall-to-wall banners.
            val today = session("2026-07-12", bench, listOf(100.0 to 5))

            assertNull(detect(candidate(today, 60.0, reps = 12), bench, alice))
        }

    @Test
    fun `a member's very first set of all is not a record`() =
        runTest {
            val today = session("2026-08-10", bench, emptyList(), finished = false)

            assertNull(detect(candidate(today, 100.0, reps = 5), bench, alice))
        }

    @Test
    fun `equalling a record is not beating it`() =
        runTest {
            // Otherwise repeating the same working weight every week fires a banner every week.
            val today = session("2026-07-12", bench, listOf(100.0 to 5))

            assertNull(detect(candidate(today, 100.0, reps = 5), bench, alice))
        }

    @Test
    fun `lifting less than the record is not a record`() =
        runTest {
            val today = session("2026-07-12", bench, listOf(100.0 to 5))

            assertNull(detect(candidate(today, 97.5, reps = 5), bench, alice))
        }

    @Test
    fun `a bodyweight set is never a record`() =
        runTest {
            val today = session("2026-07-12", bench, listOf(100.0 to 5))

            assertNull(detect(candidate(today, weightKg = null, reps = 5), bench, alice))
        }

    @Test
    fun `a heavier set at a rep count you have never done is still not a record`() =
        runTest {
            // 110 beats the 5-rep record, but this is a 3-rep set and there is no 3-rep history
            // to beat. Comparing across rep counts is exactly what ADR-0025 rejected.
            val today = session("2026-07-12", bench, listOf(100.0 to 5))

            assertNull(detect(candidate(today, 110.0, reps = 3), bench, alice))
        }

    @Test
    fun `a set already saved does not beat itself`() =
        runTest {
            // Detection runs on the save path and may see its own set already committed. It has
            // to compare against what came *before* it, or every set is its own record.
            val appearance = session("2026-07-12", bench, listOf(100.0 to 5))
            val saved = ExerciseSet("saved", appearance, 2, 105.0, 5, null, Instant.parse("2026-08-10T18:00:00Z"))
            sets.add(saved)

            val record = detect(saved, bench, alice)

            assertEquals(105.0, record?.weightKg, "beats the 100 that came before it, not itself")
        }

    @Test
    fun `an earlier set in the same session is history to beat`() =
        runTest {
            // Work up 100 then 105 in one workout and the 105 is a record. The session need not
            // be over for the lift to have happened.
            val appearance = session("2026-08-10", bench, listOf(100.0 to 5), finished = false)

            val record = detect(candidate(appearance, 105.0, reps = 5), bench, alice)

            assertEquals(105.0, record?.weightKg)
        }
}
