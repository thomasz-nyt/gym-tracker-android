package com.gymtracker.core.domain.progress

import com.gymtracker.core.domain.TestData
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
 * US-16 and US-19: one exercise over time, and what the app says when there is not enough
 * of it to say anything.
 *
 * The shape of [ExerciseTrend] is the US-19 criterion made structural. "With a single data
 * point, no trend line is drawn and no trend is claimed" is not a rule the screen has to
 * remember — [ExerciseTrend.SinglePoint] hands it one point and no list, so there is nothing
 * a line could be drawn through.
 */
class ExerciseTrendTest {
    private val alice = UserId("alice")
    private val bench = ExerciseId("bench")
    private val squat = ExerciseId("squat")

    private val sessions = FakeSessionRepository()
    private val sessionExercises = FakeSessionExerciseRepository()
    private val sets = FakeSetRepository()
    private val trendOf = ExerciseTrendOf(sessions, sessionExercises, sets, ZoneOffset.UTC)

    private var nextAppearance = 1
    private var nextSet = 1

    /** Logs [weights] as one session's worth of [exercise], performed on [date]. */
    private suspend fun session(
        date: String,
        exercise: ExerciseId = bench,
        vararg lifts: Pair<Double?, Int>,
    ): SessionId {
        val sessionId = SessionId("s-$date-${exercise.value}")
        val startedAt = Instant.parse("${date}T18:00:00Z")
        sessions.startSession(
            WorkoutSession(
                sessionId,
                alice,
                null,
                startedAt,
                startedAt.plusSeconds(3600),
                null,
            ),
        )
        val appearance = SessionExerciseId("se-${nextAppearance++}")
        val row = SessionExercise(appearance, sessionId, exercise, 1)
        sessionExercises.add(row)
        sets.belongsTo(row)
        lifts.forEachIndexed { index, (weight, reps) ->
            sets.add(ExerciseSet("set-${nextSet++}", appearance, index + 1, weight, reps, null, startedAt))
        }
        return sessionId
    }

    /** Loads a shared fixture into the fakes, wiring each set to its appearance as SQL would. */
    private suspend fun load(fixture: TestData.Fixture) {
        fixture.sessions.forEach { sessions.startSession(it) }
        fixture.sessionExercises.forEach {
            sessionExercises.add(it)
            sets.belongsTo(it)
        }
        fixture.sets.forEach { sets.add(it) }
    }

    @Test
    fun `an exercise never performed has no trend`() =
        runTest {
            assertEquals(ExerciseTrend.NoData, trendOf(bench, alice))
        }

    @Test
    fun `the shared twelve-week fixture is twelve points, rising`() =
        runTest {
            // `specs/testing-strategy.md` § Fixture data: chart tests run against TestData, not
            // rows invented here. Bench starts at 60 kg and gains 1.25 a week, so week 11 is
            // 60 + 1.25 x 11 = 73.75 — a figure worked out by hand, not read off the output.
            load(TestData.twelveWeeksOfProgress(TestData.PROGRESSING))

            val trend = trendOf(TestData.BENCH, TestData.PROGRESSING) as ExerciseTrend.Series

            assertEquals(12, trend.points.size)
            assertEquals(60.0, trend.points.first().topSetKg)
            assertEquals(73.75, trend.points.last().topSetKg)
            assertTrue(
                trend.points.zipWithNext().all { (earlier, later) -> later.topSetKg!! > earlier.topSetKg!! },
                "twelve weeks of progression should be monotonic",
            )
        }

    @Test
    fun `the sparse fixture claims no trend, because one session is not one`() =
        runTest {
            // The US-19 edge, against the shared fixture rather than a local one.
            load(TestData.oneSessionOnly(TestData.SPARSE))

            val trend = trendOf(TestData.BENCH, TestData.SPARSE)

            assertTrue(trend is ExerciseTrend.SinglePoint, "got $trend")
        }

    @Test
    fun `the empty fixture has nothing to chart`() =
        runTest {
            load(TestData.noData())

            assertEquals(ExerciseTrend.NoData, trendOf(TestData.BENCH, TestData.EMPTY))
        }

    @Test
    fun `two hundred sessions come back in order, and in one pass`() =
        runTest {
            // The milestone's exit criterion names 1, 3 and 200 sessions. 200 is here to catch
            // the shape of the read rather than its speed: the sets are fetched once and
            // grouped, not queried per session, so this stays three reads however long the
            // history gets.
            val start = LocalDate.parse("2026-01-01")
            repeat(200) { day -> session(start.plusDays(day.toLong()).toString(), bench, 100.0 to 5) }

            val trend = trendOf(bench, alice) as ExerciseTrend.Series

            assertEquals(200, trend.points.size)
            assertEquals(start, trend.points.first().performedOn)
            assertEquals(start.plusDays(199), trend.points.last().performedOn)
        }

    @Test
    fun `one session is a single point, and no series to draw a line through`() =
        runTest {
            // US-19: "with a single data point, no trend line is drawn and no trend is claimed."
            session("2026-08-01", bench, 100.0 to 5)

            val trend = trendOf(bench, alice)

            assertTrue(trend is ExerciseTrend.SinglePoint, "got $trend")
            assertEquals(LocalDate.parse("2026-08-01"), trend.point.performedOn)
        }

    @Test
    fun `a point carries the session's top set, volume and best estimate`() =
        runTest {
            // 100x5 and 110x3 in one session:
            //   top set   = 110 (the heaviest actually lifted)
            //   volume    = 100x5 + 110x3 = 500 + 330 = 830
            //   estimate  = max(100x(1+5/30), 110x(1+3/30)) = max(116.67, 121.0) = 121.0
            session("2026-08-01", bench, 100.0 to 5, 110.0 to 3)

            val trend = trendOf(bench, alice) as ExerciseTrend.SinglePoint

            assertEquals(110.0, trend.point.topSetKg)
            assertEquals(830.0, trend.point.volumeKg!!, 1e-9)
            assertEquals(121.0, trend.point.estimatedOneRepMaxKg!!, 1e-9)
            assertEquals(2, trend.point.sets)
        }

    @Test
    fun `several sessions come back oldest first, one point each`() =
        runTest {
            session("2026-08-05", bench, 105.0 to 5)
            session("2026-07-22", bench, 95.0 to 5)
            session("2026-07-29", bench, 100.0 to 5)

            val trend = trendOf(bench, alice) as ExerciseTrend.Series

            assertEquals(
                listOf("2026-07-22", "2026-07-29", "2026-08-05").map(LocalDate::parse),
                trend.points.map { it.performedOn },
                "a chart reads left to right in time",
            )
        }

    @Test
    fun `only the chosen exercise is counted`() =
        runTest {
            session("2026-08-01", bench, 100.0 to 5)
            session("2026-08-01", squat, 140.0 to 5)

            val trend = trendOf(bench, alice) as ExerciseTrend.SinglePoint

            assertEquals(100.0, trend.point.topSetKg)
        }

    @Test
    fun `two appearances of the same exercise in one session are one point`() =
        runTest {
            // US-02 lets an exercise appear twice in a session. That is still one day's work,
            // so it is one point — otherwise the chart would show two dots on the same date.
            // Two sets on the first appearance, one on the second: three sets, one point.
            val date = "2026-08-01"
            session(date, bench, 100.0 to 5, 100.0 to 5)
            val sessionId = SessionId("s-$date-bench")
            val second = SessionExerciseId("se-${nextAppearance++}")
            val secondRow = SessionExercise(second, sessionId, bench, 2)
            sessionExercises.add(secondRow)
            sets.belongsTo(secondRow)
            sets.add(
                ExerciseSet("set-${nextSet++}", second, 1, 110.0, 3, null, Instant.parse("${date}T19:00:00Z")),
            )

            val trend = trendOf(bench, alice) as ExerciseTrend.SinglePoint

            assertEquals(110.0, trend.point.topSetKg, "the heaviest across both appearances")
            assertEquals(3, trend.point.sets)
        }

    @Test
    fun `a bodyweight session has no load to report, and says so rather than reporting zero`() =
        runTest {
            // Constitution §2.4: absence is a state. A session of bodyweight dips has no top
            // set and no volume in kilograms, and zero would be a claim that it was weightless.
            session("2026-08-01", bench, null to 12, null to 10)

            val trend = trendOf(bench, alice) as ExerciseTrend.SinglePoint

            assertNull(trend.point.topSetKg)
            assertNull(trend.point.volumeKg)
            assertNull(trend.point.estimatedOneRepMaxKg)
            assertEquals(2, trend.point.sets, "the sets still happened")
        }

    @Test
    fun `a session mixing loaded and bodyweight sets counts only what was loaded`() =
        runTest {
            // 100x5 loaded, plus a bodyweight set: volume is 500, from the set that had a load.
            session("2026-08-01", bench, 100.0 to 5, null to 12)

            val trend = trendOf(bench, alice) as ExerciseTrend.SinglePoint

            assertEquals(500.0, trend.point.volumeKg!!, 1e-9)
            assertEquals(100.0, trend.point.topSetKg)
            assertEquals(2, trend.point.sets, "both sets were performed, whatever they weighed")
        }

    @Test
    fun `a session with no sets logged is not a point on the chart`() =
        runTest {
            // An exercise added and never performed is not a data point; drawing it would put a
            // zero on the chart for a day nothing was lifted.
            session("2026-08-01", bench)
            session("2026-08-05", bench, 100.0 to 5)

            val trend = trendOf(bench, alice)

            assertTrue(trend is ExerciseTrend.SinglePoint, "got $trend")
            assertEquals(LocalDate.parse("2026-08-05"), trend.point.performedOn)
        }
}
