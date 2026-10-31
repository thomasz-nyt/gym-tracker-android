package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.rest.FakeRestTimerStore
import com.gymtracker.core.domain.set.FakeSetRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkoutRecoveryTest {
    private val now = Instant.parse("2026-10-29T15:00:00Z")
    private val member = UserId("alice")
    private val session =
        WorkoutSession(SessionId("workout"), member, null, now.minusSeconds(1800), now.minusSeconds(60), null)
    private val movement = SessionExercise(SessionExerciseId("bench"), session.id, ExerciseId("bench"), 1)
    private val sets = FakeSetRepository().also { it.belongsTo(movement) }
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val rest = FakeRestTimerStore()

    @Test
    fun `reopen then finish without a new actual set keeps the original end`() =
        runTest {
            sets.add(ExerciseSet("s1", movement.id, 1, 50.0, 10, null, now.minusSeconds(90)))
            val sessions = FakeSessionRepository(listOf(session))

            assertTrue(ReopenWorkout(sessions, sets, clock)(session.id, member))
            EndSession(sessions, sets, rest, clock)(session.id)

            assertEquals(session.endedAt, sessions.findSession(session.id)?.endedAt)
        }

    @Test
    fun `new work after reopening ends at the new observed finish time`() =
        runTest {
            sets.add(ExerciseSet("s1", movement.id, 1, 50.0, 10, null, now.minusSeconds(90)))
            val sessions = FakeSessionRepository(listOf(session))
            assertTrue(ReopenWorkout(sessions, sets, clock)(session.id, member))
            sets.add(ExerciseSet("s2", movement.id, 2, 50.0, 8, null, now))

            EndSession(sessions, sets, rest, clock)(session.id)

            assertEquals(now, sessions.findSession(session.id)?.endedAt)
        }

    @Test
    fun `reopening is refused after ten minutes or when another workout has started`() =
        runTest {
            val old = session.copy(endedAt = now.minus(Duration.ofMinutes(11)))
            assertFalse(ReopenWorkout(FakeSessionRepository(listOf(old)), sets, clock)(old.id, member))
            val sessions = FakeSessionRepository(listOf(session))
            StartSession(sessions, rest, clock) { SessionId("new") }(member)
            sessions.deleteSession(SessionId("new"))
            assertFalse(ReopenWorkout(sessions, sets, clock)(session.id, member))
        }
}
