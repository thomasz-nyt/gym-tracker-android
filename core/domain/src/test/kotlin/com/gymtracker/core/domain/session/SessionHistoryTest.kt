package com.gymtracker.core.domain.session

import app.cash.turbine.test
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

/** US-06: "History lists sessions newest-first with date, duration, exercise count, volume." */
class SessionHistoryTest {
    private val now: Instant = Instant.parse("2026-08-01T18:00:00Z")
    private val alice = UserId("alice")
    private val bob = UserId("bob")

    private val sessionExercises = FakeSessionExerciseRepository()
    private val sets = FakeSetRepository()
    private val sessions = FakeSessionRepository()

    private val history = SessionHistory(sessions, sessionExercises, sets)

    private suspend fun finished(
        id: String,
        member: UserId = alice,
        startedAt: Instant,
        ranFor: Duration = Duration.ofHours(1),
    ): SessionId {
        val sessionId = SessionId(id)
        sessions.startSession(
            WorkoutSession(sessionId, member, null, startedAt, startedAt.plus(ranFor), null),
        )
        return sessionId
    }

    private suspend fun open(
        id: String,
        member: UserId = alice,
    ): SessionId {
        val sessionId = SessionId(id)
        sessions.startSession(WorkoutSession(sessionId, member, null, now, null, null))
        return sessionId
    }

    private suspend fun setsOf(
        session: SessionId,
        vararg weights: Double?,
    ) {
        val appearance = SessionExercise(SessionExerciseId("${session.value}-se"), session, ExerciseId("bench"), 1)
        sessionExercises.add(appearance)
        sets.belongsTo(appearance)
        weights.forEachIndexed { index, weight ->
            sets.add(ExerciseSet("${session.value}-$index", appearance.id, index + 1, weight, 10, null, now))
        }
    }

    @Test
    fun `history is empty for a member who has finished nothing`() =
        runTest {
            history(alice).test {
                assertEquals(emptyList(), awaitItem())
            }
        }

    @Test
    fun `finished sessions are listed newest first`() =
        runTest {
            finished("oldest", startedAt = now.minus(Duration.ofDays(9)))
            finished("newest", startedAt = now.minus(Duration.ofDays(1)))
            finished("middle", startedAt = now.minus(Duration.ofDays(4)))

            history(alice).test {
                assertEquals(
                    listOf("newest", "middle", "oldest"),
                    awaitItem().map { it.session.id.value },
                )
            }
        }

    @Test
    fun `the session still running is not in history`() =
        runTest {
            // You cannot delete the workout you are standing in the middle of (US-06a); the
            // way that is guaranteed is that it never appears in the list at all.
            open("today")
            finished("yesterday", startedAt = now.minus(Duration.ofDays(1)))

            history(alice).test {
                assertEquals(listOf("yesterday"), awaitItem().map { it.session.id.value })
            }
        }

    @Test
    fun `a member sees only their own workouts`() =
        runTest {
            finished("alices", member = alice, startedAt = now.minus(Duration.ofDays(1)))
            finished("bobs", member = bob, startedAt = now.minus(Duration.ofDays(1)))

            history(alice).test {
                assertEquals(listOf("alices"), awaitItem().map { it.session.id.value })
            }
        }

    @Test
    fun `each row carries its own counts and volume`() =
        runTest {
            val heavy = finished("heavy", startedAt = now.minus(Duration.ofDays(1)))
            val light = finished("light", startedAt = now.minus(Duration.ofDays(2)))
            setsOf(heavy, 100.0, 100.0)
            setsOf(light, 20.0)

            history(alice).test {
                val rows = awaitItem()
                assertEquals(listOf(2000.0, 200.0), rows.map { it.volumeKg })
                assertEquals(listOf(2, 1), rows.map { it.setCount })
                assertEquals(listOf(1, 1), rows.map { it.exerciseCount })
            }
        }

    @Test
    fun `duration comes from the recorded start and end`() =
        runTest {
            finished("s1", startedAt = now.minus(Duration.ofDays(1)), ranFor = Duration.ofMinutes(72))

            history(alice).test {
                assertEquals(Duration.ofMinutes(72), awaitItem().single().duration)
            }
        }

    @Test
    fun `history updates when a workout is deleted`() =
        runTest {
            // The list is a live query, so US-06a's delete does not need to tell it anything.
            finished("keep", startedAt = now.minus(Duration.ofDays(1)))
            val doomed = finished("doomed", startedAt = now.minus(Duration.ofDays(2)))

            history(alice).test {
                assertEquals(2, awaitItem().size)

                sessions.deleteSession(doomed)

                assertEquals(listOf("keep"), awaitItem().map { it.session.id.value })
            }
        }
}
