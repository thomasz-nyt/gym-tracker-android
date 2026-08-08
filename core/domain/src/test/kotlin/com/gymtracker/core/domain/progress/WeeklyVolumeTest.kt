package com.gymtracker.core.domain.progress

import com.gymtracker.core.domain.TestData
import com.gymtracker.core.domain.exercise.FakeExerciseCatalog
import com.gymtracker.core.domain.model.BodyPart
import com.gymtracker.core.domain.model.Equipment
import com.gymtracker.core.domain.model.Exercise
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
import kotlin.test.assertTrue

/**
 * US-17: weekly training volume grouped by primary muscle, over a chosen range.
 *
 * Weeks start Monday. The figures below are hand-computed from `weight × reps`, per
 * `specs/testing-strategy.md`.
 */
class WeeklyVolumeTest {
    private val alice = UserId("alice")
    private val bench = ExerciseId("bench")
    private val squat = ExerciseId("squat")
    private val row = ExerciseId("row")

    private val sessions = FakeSessionRepository()
    private val sessionExercises = FakeSessionExerciseRepository()
    private val sets = FakeSetRepository()

    private val catalog =
        FakeExerciseCatalog(
            listOf(
                exercise(bench, "Bench Press", BodyPart.CHEST),
                exercise(squat, "Squat", BodyPart.QUADS),
                exercise(row, "Row", BodyPart.BACK),
            ) + TestData.exercises,
        )

    private val weeklyVolume = WeeklyVolumeByBodyPart(sessions, sessionExercises, sets, catalog, ZoneOffset.UTC)

    private var nextAppearance = 1
    private var nextSet = 1

    private fun exercise(
        id: ExerciseId,
        name: String,
        vararg primary: BodyPart,
    ) = Exercise(
        id = id,
        name = name,
        aliases = emptyList(),
        primaryMuscles = primary.toList(),
        secondaryMuscles = emptyList(),
        equipment = Equipment.BARBELL,
        instructions = emptyList(),
        mediaUrl = null,
        mediaType = null,
        youtubeUrl = null,
        source = "test",
    )

    private suspend fun session(
        date: String,
        vararg work: Pair<ExerciseId, List<Pair<Double?, Int>>>,
    ) {
        val sessionId = SessionId("s-$date-$nextAppearance")
        val startedAt = Instant.parse("${date}T18:00:00Z")
        sessions.startSession(
            WorkoutSession(sessionId, alice, null, startedAt, startedAt.plusSeconds(3600), null),
        )
        work.forEachIndexed { position, (exerciseId, lifts) ->
            val appearance = SessionExerciseId("se-${nextAppearance++}")
            val row = SessionExercise(appearance, sessionId, exerciseId, position + 1)
            sessionExercises.add(row)
            sets.belongsTo(row)
            lifts.forEachIndexed { index, (weight, reps) ->
                sets.add(ExerciseSet("set-${nextSet++}", appearance, index + 1, weight, reps, null, startedAt))
            }
        }
    }

    private val monday = LocalDate.parse("2026-08-03")

    @Test
    fun `a member who has never trained has no weeks at all`() =
        runTest {
            val weeks = weeklyVolume(alice, from = monday, to = monday.plusDays(6))

            assertTrue(weeks.all { it.byBodyPart.isEmpty() }, "no volume, and nothing invented")
        }

    @Test
    fun `volume is weight times reps, grouped by the exercise's primary muscle`() =
        runTest {
            // bench 100x5 x2 sets = 1000 to CHEST; squat 140x5 = 700 to QUADS.
            session("2026-08-04", bench to listOf(100.0 to 5, 100.0 to 5), squat to listOf(140.0 to 5))

            val week = weeklyVolume(alice, from = monday, to = monday.plusDays(6)).single()

            assertEquals(monday, week.weekStarting)
            assertEquals(
                mapOf(BodyPart.CHEST to 1000.0, BodyPart.QUADS to 700.0),
                week.byBodyPart.associate { it.bodyPart to it.volumeKg },
            )
        }

    @Test
    fun `body parts come back heaviest first`() =
        runTest {
            session("2026-08-04", bench to listOf(50.0 to 5), squat to listOf(140.0 to 5))

            val week = weeklyVolume(alice, from = monday, to = monday.plusDays(6)).single()

            assertEquals(listOf(BodyPart.QUADS, BodyPart.CHEST), week.byBodyPart.map { it.bodyPart })
        }

    @Test
    fun `sessions in the same week are added together`() =
        runTest {
            // Tuesday and Thursday of one week: 500 + 500 = 1000 to CHEST.
            session("2026-08-04", bench to listOf(100.0 to 5))
            session("2026-08-06", bench to listOf(100.0 to 5))

            val weeks = weeklyVolume(alice, from = monday, to = monday.plusDays(6))

            assertEquals(1, weeks.size, "one week, however many sessions are in it")
            assertEquals(
                1000.0,
                weeks
                    .single()
                    .byBodyPart
                    .single()
                    .volumeKg,
            )
        }

    @Test
    fun `weeks are separate, and a week trained not at all is a real zero`() =
        runTest {
            // A week you did not train is not missing data — you trained nothing, and the chart
            // should say so rather than closing the gap and implying you trained every week.
            session("2026-08-04", bench to listOf(100.0 to 5))
            session("2026-08-18", bench to listOf(110.0 to 5))

            val weeks = weeklyVolume(alice, from = monday, to = monday.plusDays(20))

            assertEquals(3, weeks.size)
            assertEquals(500.0, weeks[0].byBodyPart.single().volumeKg)
            assertTrue(weeks[1].byBodyPart.isEmpty(), "the week between is empty, and present")
            assertEquals(550.0, weeks[2].byBodyPart.single().volumeKg)
        }

    @Test
    fun `sessions outside the range are not counted`() =
        runTest {
            session("2026-07-28", bench to listOf(100.0 to 5))
            session("2026-08-04", bench to listOf(100.0 to 5))

            val weeks = weeklyVolume(alice, from = monday, to = monday.plusDays(6))

            assertEquals(1, weeks.size)
            assertEquals(
                500.0,
                weeks
                    .single()
                    .byBodyPart
                    .single()
                    .volumeKg,
            )
        }

    @Test
    fun `a bodyweight set adds no volume rather than adding zero`() =
        runTest {
            // Nothing was loaded, so there is no kilogram figure to add. CHEST does not appear
            // at all, which is honest; a 0.0 bar would say the chest was trained weightlessly.
            session("2026-08-04", bench to listOf(null to 12))

            val week = weeklyVolume(alice, from = monday, to = monday.plusDays(6)).single()

            assertTrue(week.byBodyPart.isEmpty())
        }

    @Test
    fun `an exercise the catalog does not know is left out rather than guessed at`() =
        runTest {
            // No primary muscle means no honest bucket for it (constitution §2.4). It is not
            // filed under "other", which would be the app claiming knowledge it lacks — the
            // same rule ADR-0015 applied to Equipment.UNSPECIFIED.
            session("2026-08-04", ExerciseId("unknown") to listOf(100.0 to 5))

            val week = weeklyVolume(alice, from = monday, to = monday.plusDays(6)).single()

            assertTrue(week.byBodyPart.isEmpty())
        }

    @Test
    fun `the twelve-week fixture spreads across twelve weeks`() =
        runTest {
            TestData.twelveWeeksOfProgress(TestData.PROGRESSING).let { fixture ->
                fixture.sessions.forEach { sessions.startSession(it) }
                fixture.sessionExercises.forEach {
                    sessionExercises.add(it)
                    sets.belongsTo(it)
                }
                fixture.sets.forEach { sets.add(it) }
            }
            val first = LocalDate.parse("2026-05-04")

            val weeks =
                weeklyVolume(TestData.PROGRESSING, from = first, to = first.plusWeeks(11).plusDays(6))

            assertEquals(12, weeks.size)
            assertTrue(weeks.all { it.byBodyPart.isNotEmpty() }, "every week of the fixture was trained")
        }
}
