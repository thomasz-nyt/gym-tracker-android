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
 * US-34: what actually happened, one row per session, for one exercise across every
 * session it appears in.
 *
 * Deliberately mirrors [ExerciseTrendTest]'s fixtures rather than sharing them — the two
 * reads must apply the same "which sessions counted" rule independently, so a fixture bug
 * shared between them would not be caught by either suite.
 */
class ExerciseLogOfTest {
    private val alice = UserId("alice")
    private val bench = ExerciseId("bench")
    private val squat = ExerciseId("squat")

    private val sessions = FakeSessionRepository()
    private val sessionExercises = FakeSessionExerciseRepository()
    private val sets = FakeSetRepository()
    private val logOf = ExerciseLogOf(sessions, sessionExercises, sets, ZoneOffset.UTC)

    private var nextAppearance = 1
    private var nextSet = 1

    private suspend fun session(
        date: String,
        exercise: ExerciseId = bench,
        vararg lifts: Pair<Double?, Int>,
    ): SessionId {
        val sessionId = SessionId("s-$date-${exercise.value}")
        val startedAt = Instant.parse("${date}T18:00:00Z")
        sessions.startSession(WorkoutSession(sessionId, alice, null, startedAt, startedAt.plusSeconds(3600), null))
        val appearance = SessionExerciseId("se-${nextAppearance++}")
        val row = SessionExercise(appearance, sessionId, exercise, 1)
        sessionExercises.add(row)
        sets.belongsTo(row)
        lifts.forEachIndexed { index, (weight, reps) ->
            sets.add(ExerciseSet("set-${nextSet++}", appearance, index + 1, weight, reps, null, startedAt))
        }
        return sessionId
    }

    @Test
    fun `an exercise never performed has an empty log`() =
        runTest {
            assertEquals(emptyList(), logOf(bench, alice))
        }

    @Test
    fun `several sessions come back newest first, a log reads like history`() =
        runTest {
            session("2026-08-05", bench, 105.0 to 5)
            session("2026-07-22", bench, 95.0 to 5)
            session("2026-07-29", bench, 100.0 to 5)

            val log = logOf(bench, alice)

            assertEquals(
                listOf("2026-08-05", "2026-07-29", "2026-07-22").map(LocalDate::parse),
                log.map { it.performedOn },
                "opposite direction from the chart, which reads oldest first",
            )
        }

    @Test
    fun `a row carries the session's sets, best set and estimate`() =
        runTest {
            // 100x5 and 110x3: top set 110, estimate max(116.67, 121.0) = 121.0
            session("2026-08-01", bench, 100.0 to 5, 110.0 to 3)

            val row = logOf(bench, alice).single()

            assertEquals(2, row.sets.size)
            assertEquals(110.0, row.topSetKg)
            assertEquals(121.0, row.estimatedOneRepMaxKg!!, 1e-9)
        }

    @Test
    fun `only the chosen exercise is counted`() =
        runTest {
            session("2026-08-01", bench, 100.0 to 5)
            session("2026-08-01", squat, 140.0 to 5)

            val log = logOf(bench, alice)

            assertEquals(1, log.size)
            assertEquals(100.0, log.single().topSetKg)
        }

    @Test
    fun `a session with no sets logged is not a row`() =
        runTest {
            // An exercise added and never performed contributes no row — the same rule
            // ExerciseTrendOf applies, so the chart and the log never disagree.
            session("2026-08-01", bench)
            session("2026-08-05", bench, 100.0 to 5)

            val log = logOf(bench, alice)

            assertEquals(1, log.size)
            assertEquals(LocalDate.parse("2026-08-05"), log.single().performedOn)
        }

    @Test
    fun `two appearances of the same exercise in one session are one row`() =
        runTest {
            val date = "2026-08-01"
            val sessionId = session(date, bench, 100.0 to 5)
            val second = SessionExerciseId("se-${nextAppearance++}")
            sessionExercises.add(SessionExercise(second, sessionId, bench, 2))
            sets.belongsTo(SessionExercise(second, sessionId, bench, 2))
            sets.add(ExerciseSet("set-${nextSet++}", second, 1, 110.0, 3, null, Instant.parse("${date}T19:00:00Z")))

            val log = logOf(bench, alice)

            assertEquals(1, log.size)
            assertEquals(2, log.single().sets.size, "both appearances' sets, in one row")
            assertEquals(110.0, log.single().topSetKg, "the heaviest across both appearances")
        }

    @Test
    fun `a bodyweight session has no top set or estimate, but its sets are still shown`() =
        runTest {
            session("2026-08-01", bench, null to 12, null to 10)

            val row = logOf(bench, alice).single()

            assertNull(row.topSetKg)
            assertNull(row.estimatedOneRepMaxKg)
            assertEquals(2, row.sets.size, "the sets still happened")
        }

    @Test
    fun `two hundred sessions come back in one pass`() =
        runTest {
            val start = LocalDate.parse("2026-01-01")
            repeat(200) { day -> session(start.plusDays(day.toLong()).toString(), bench, 100.0 to 5) }

            val log = logOf(bench, alice)

            assertEquals(200, log.size)
            assertEquals(start.plusDays(199), log.first().performedOn, "newest first")
            assertTrue(log.all { it.sets.size == 1 })
        }
}
