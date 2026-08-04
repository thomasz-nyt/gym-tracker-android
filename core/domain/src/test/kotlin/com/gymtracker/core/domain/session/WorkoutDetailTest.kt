package com.gymtracker.core.domain.session

import app.cash.turbine.test
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
import com.gymtracker.core.domain.sessionexercise.FakeSessionExerciseRepository
import com.gymtracker.core.domain.set.FakeSetRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** US-06b: what a past workout actually contained, not just what it added up to. */
class WorkoutDetailTest {
    private val now: Instant = Instant.parse("2026-08-01T18:00:00Z")
    private val alice = UserId("alice")
    private val target = SessionId("s1")

    private val bench =
        Exercise(
            id = ExerciseId("bench"),
            name = "Bench Press",
            aliases = emptyList(),
            primaryMuscles = listOf(BodyPart.CHEST),
            secondaryMuscles = listOf(BodyPart.TRICEPS),
            equipment = Equipment.BARBELL,
            instructions = emptyList(),
            mediaUrl = null,
            mediaType = null,
            youtubeUrl = null,
            source = "free-exercise-db",
        )

    private val pullUp =
        bench.copy(
            id = ExerciseId("pullup"),
            name = "Pull Up",
            primaryMuscles = listOf(BodyPart.BACK),
            equipment = Equipment.BODYWEIGHT,
        )

    private val sessionExercises = FakeSessionExerciseRepository()
    private val sets = FakeSetRepository()
    private val sessions = FakeSessionRepository()
    private val catalog = FakeExerciseCatalog(listOf(bench, pullUp))

    private val detail = WorkoutDetail(sessions, sessionExercises, sets, catalog)

    private suspend fun finishedWorkout() {
        sessions.startSession(
            WorkoutSession(target, alice, null, now, now.plus(Duration.ofMinutes(72)), null),
        )
        val first = SessionExercise(SessionExerciseId("se-1"), target, bench.id, 1)
        val second = SessionExercise(SessionExerciseId("se-2"), target, pullUp.id, 2)
        listOf(first, second).forEach {
            sessionExercises.add(it)
            sets.belongsTo(it)
        }
        // Two identical sets then a heavier one, so the grouping has something to do.
        sets.add(ExerciseSet("a", first.id, 1, 60.0, 10, null, now))
        sets.add(ExerciseSet("b", first.id, 2, 60.0, 10, null, now))
        sets.add(ExerciseSet("c", first.id, 3, 70.0, 8, null, now))
        // Bodyweight: no weight recorded, which is not the same as zero.
        sets.add(ExerciseSet("d", second.id, 1, null, 12, null, now))
    }

    @Test
    fun `a workout that is not there reads as absent`() =
        runTest {
            detail(SessionId("never-existed"), alice).test {
                assertNull(awaitItem())
            }
        }

    @Test
    fun `the exercises are listed in the order they were performed`() =
        runTest {
            // Unlike the active session, which shows the newest first (US-02b).
            finishedWorkout()

            detail(target, alice).test {
                val exercises = checkNotNull(awaitItem()).exercises
                assertEquals(listOf("Bench Press", "Pull Up"), exercises.map { it.exercise?.name })
                assertEquals(listOf(1, 2), exercises.map { it.sessionExercise.position })
            }
        }

    @Test
    fun `each exercise carries the catalog entry, so equipment and muscles can be shown`() =
        runTest {
            finishedWorkout()

            detail(target, alice).test {
                val first = checkNotNull(awaitItem()).exercises.first()
                assertEquals(Equipment.BARBELL, first.exercise?.equipment)
                assertEquals(listOf(BodyPart.CHEST), first.exercise?.primaryMuscles)
            }
        }

    @Test
    fun `identical consecutive sets are grouped for reading`() =
        runTest {
            // ADR-0009's display grouping, reused rather than re-derived.
            finishedWorkout()

            detail(target, alice).test {
                val benchPress = checkNotNull(awaitItem()).exercises.first()
                assertEquals(listOf(2, 1), benchPress.groups.map { it.count }, "60x10 twice, then 70x8")
                assertEquals(listOf(60.0, 70.0), benchPress.groups.map { it.weightKg })
                assertEquals(3, benchPress.sets.size, "the grouping is display only")
            }
        }

    @Test
    fun `per-exercise volume counts only the sets that recorded a weight`() =
        runTest {
            finishedWorkout()

            detail(target, alice).test {
                val exercises = checkNotNull(awaitItem()).exercises
                assertEquals(60.0 * 10 + 60.0 * 10 + 70.0 * 8, exercises.first().volumeKg)
                assertEquals(0, exercises.first().bodyweightSetCount)
            }
        }

    @Test
    fun `a bodyweight exercise reports no volume rather than zero`() =
        runTest {
            // Constitution §2.4: a load that was never recorded is absent, not nothing.
            finishedWorkout()

            detail(target, alice).test {
                val pullUps = checkNotNull(awaitItem()).exercises.last()
                assertNull(pullUps.volumeKg)
                assertEquals(1, pullUps.bodyweightSetCount)
            }
        }

    @Test
    fun `the summary matches the one history shows for the same workout`() =
        runTest {
            // Both go through SessionSummary.of, so the detail screen and the list row can
            // never disagree about a count.
            finishedWorkout()
            val fromHistory = SessionHistory(sessions, sessionExercises, sets)

            detail(target, alice).test {
                val summary = checkNotNull(awaitItem()).summary
                fromHistory(alice).test {
                    assertEquals(awaitItem().single(), summary)
                }
            }
        }

    @Test
    fun `an exercise added but never logged against shows no sets`() =
        runTest {
            // ADR-0004 makes this a representable state: the machine was busy, or it was
            // added and not used.
            sessions.startSession(
                WorkoutSession(target, alice, null, now, now.plus(Duration.ofMinutes(20)), null),
            )
            sessionExercises.add(SessionExercise(SessionExerciseId("se-1"), target, bench.id, 1))

            detail(target, alice).test {
                val only = checkNotNull(awaitItem()).exercises.single()
                assertEquals(emptyList(), only.sets)
                assertEquals(emptyList(), only.groups)
                assertNull(only.volumeKg)
            }
        }

    @Test
    fun `metrics that were never recorded stay absent`() =
        runTest {
            // Until M5 there are none at all. The screen must show "not recorded", never zero.
            finishedWorkout()

            detail(target, alice).test {
                assertNull(checkNotNull(awaitItem()).summary.session.metrics)
            }
        }

    @Test
    fun `another members workout is not readable`() =
        runTest {
            finishedWorkout()

            detail(target, UserId("bob")).test {
                assertNull(awaitItem())
            }
        }
}
