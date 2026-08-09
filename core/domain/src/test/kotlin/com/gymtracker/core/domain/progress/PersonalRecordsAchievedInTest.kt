package com.gymtracker.core.domain.progress

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.session.FakeSessionRepository
import com.gymtracker.core.domain.session.PerformedExercise
import com.gymtracker.core.domain.session.SessionDetail
import com.gymtracker.core.domain.session.SessionSummary
import com.gymtracker.core.domain.sessionexercise.FakeSessionExerciseRepository
import com.gymtracker.core.domain.set.FakeSetRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * US-31: which personal records a just-finished session actually set.
 *
 * [PersonalRecordsAchievedIn] loops [DetectPersonalRecord] over every set in a [SessionDetail],
 * which is correct but has one sharp edge worth pinning here rather than discovering on a finish
 * screen: two sets at the same rep count in one session can *both* look like records — the
 * second beats the first, which is now part of the history it is judged against. A summary
 * listing both would be redundant, so only the best per (exercise, reps) survives.
 *
 * History is seeded through the same fakes [DetectPersonalRecord] reads from
 * (`PersonalRecordTest`'s pattern); the session under test is a hand-built [SessionDetail], the
 * same shape [ActiveSessionViewModel][com.gymtracker.feature.logging] will pass in from
 * `WorkoutDetail`.
 */
class PersonalRecordsAchievedInTest {
    private val alice = UserId("alice")
    private val bench = ExerciseId("bench")
    private val squat = ExerciseId("squat")

    private val sessions = FakeSessionRepository()
    private val sessionExercises = FakeSessionExerciseRepository()
    private val sets = FakeSetRepository()

    private val recordsOf = PersonalRecordsOf(sessions, sessionExercises, sets, ZoneOffset.UTC)
    private val detect = DetectPersonalRecord(recordsOf, ZoneOffset.UTC)
    private val achievedIn = PersonalRecordsAchievedIn(detect)

    private var nextAppearance = 1
    private var nextSet = 1

    /** Seeds prior history the new session's sets will be judged against. */
    private suspend fun history(
        date: String,
        exerciseId: ExerciseId,
        lifts: List<Pair<Double?, Int>>,
    ) {
        val sessionId = SessionId("s-$date-$nextAppearance")
        val startedAt = Instant.parse("${date}T18:00:00Z")
        sessions.startSession(
            WorkoutSession(sessionId, alice, null, startedAt, startedAt.plusSeconds(3600), null),
        )
        val appearance = SessionExerciseId("se-${nextAppearance++}")
        sessionExercises.add(SessionExercise(appearance, sessionId, exerciseId, 1))
        sets.belongsTo(SessionExercise(appearance, sessionId, exerciseId, 1))
        lifts.forEachIndexed { index, (weight, reps) ->
            sets.add(ExerciseSet("hist-${nextSet++}", appearance, index + 1, weight, reps, null, startedAt))
        }
    }

    /**
     * The session under test: seeded into the same fakes (so [DetectPersonalRecord] can compare
     * against it), and returned as the [SessionDetail] this session's own finish flow would see.
     */
    private suspend fun thisSession(vararg exercises: Pair<ExerciseId, List<Pair<Double?, Int>>>): SessionDetail {
        val sessionId = SessionId("today")
        val startedAt = Instant.parse("2026-08-10T18:00:00Z")
        val session = WorkoutSession(sessionId, alice, null, startedAt, startedAt.plusSeconds(3600), null)
        sessions.startSession(session)

        val performed =
            exercises.map { (exerciseId, lifts) ->
                val appearance = SessionExerciseId("se-${nextAppearance++}")
                val row = SessionExercise(appearance, sessionId, exerciseId, 1)
                sessionExercises.add(row)
                sets.belongsTo(row)
                val rows =
                    lifts.mapIndexed { index, (weight, reps) ->
                        ExerciseSet("today-${nextSet++}", appearance, index + 1, weight, reps, null, startedAt)
                    }
                rows.forEach { sets.add(it) }
                PerformedExercise(row, exercise = null, sets = rows, volumeKg = null, bodyweightSetCount = 0)
            }

        return SessionDetail(
            summary = SessionSummary(session, performed.size, performed.sumOf { it.sets.size }, null, 0),
            exercises = performed,
        )
    }

    @Test
    fun `a session with nothing to beat sets no records`() =
        runTest {
            val detail = thisSession(bench to listOf(100.0 to 5))

            assertTrue(achievedIn(detail, alice).isEmpty())
        }

    @Test
    fun `a set that beats prior history is a record`() =
        runTest {
            history("2026-07-12", bench, listOf(100.0 to 5))
            val detail = thisSession(bench to listOf(102.5 to 5))

            val records = achievedIn(detail, alice)

            assertEquals(1, records.size)
            assertEquals(102.5, records.single().weightKg)
            assertEquals(5, records.single().reps)
        }

    @Test
    fun `beating the same rep count twice in one session reports only the best`() =
        runTest {
            // The sharp edge this class exists for: 100 then 105 at 5 reps in one workout is
            // one record to celebrate, not two.
            history("2026-07-12", bench, listOf(95.0 to 5))
            val detail = thisSession(bench to listOf(100.0 to 5, 105.0 to 5))

            val records = achievedIn(detail, alice)

            assertEquals(1, records.size, "got $records")
            assertEquals(105.0, records.single().weightKg)
        }

    @Test
    fun `records are reported per exercise`() =
        runTest {
            history("2026-07-12", bench, listOf(100.0 to 5))
            history("2026-07-12", squat, listOf(140.0 to 5))
            val detail = thisSession(bench to listOf(102.5 to 5), squat to listOf(145.0 to 5))

            val records = achievedIn(detail, alice)

            assertEquals(setOf(bench, squat), records.map { it.exerciseId }.toSet())
        }

    @Test
    fun `a set that does not beat history is not a record`() =
        runTest {
            history("2026-07-12", bench, listOf(100.0 to 5))
            val detail = thisSession(bench to listOf(97.5 to 5))

            assertTrue(achievedIn(detail, alice).isEmpty())
        }

    @Test
    fun `a bodyweight-only session sets no records`() =
        runTest {
            // No load to compare — DetectPersonalRecord already returns null for a null weight.
            val detail = thisSession(bench to listOf(null to 12))

            assertTrue(achievedIn(detail, alice).isEmpty())
        }

    @Test
    fun `an empty session sets no records`() =
        runTest {
            val detail = thisSession()

            assertTrue(achievedIn(detail, alice).isEmpty())
        }
}
