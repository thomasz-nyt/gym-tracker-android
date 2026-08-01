package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.set.NoSets
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** US-06: ending a session, and the rule that an empty one is discarded rather than saved. */
class EndSessionTest {
    private val now: Instant = Instant.parse("2026-08-01T18:12:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val member = UserId("alice")

    private fun session(startedAt: Instant = now.minus(Duration.ofMinutes(70))) =
        WorkoutSession(
            id = SessionId("s1"),
            userId = member,
            gymName = null,
            startedAt = startedAt,
            endedAt = null,
            metrics = null,
        )

    private class SetsIn(
        private val lastSetAt: Instant?,
    ) : NoSets() {
        override suspend fun lastSetAtInSession(sessionId: SessionId): Instant? = lastSetAt
    }

    @Test
    fun `ending a session with sets closes it at the moment it was ended`() =
        runTest {
            // Not at the last set's time: that is the stale-session path (US-01), where nobody
            // was there to press the button. Here the member is standing in the gym pressing it.
            val sessions = FakeSessionRepository(listOf(session()))
            val endSession = EndSession(sessions, SetsIn(now.minus(Duration.ofMinutes(3))), clock)

            val result = endSession(SessionId("s1"))

            assertEquals(EndSessionResult.Ended(now), result)
            assertEquals(now, sessions.sessions.single().endedAt)
        }

    @Test
    fun `a session with no sets is discarded rather than saved`() =
        runTest {
            val sessions = FakeSessionRepository(listOf(session()))
            val endSession = EndSession(sessions, SetsIn(lastSetAt = null), clock)

            val result = endSession(SessionId("s1"))

            assertEquals(EndSessionResult.Discarded, result)
            assertEquals(emptyList(), sessions.sessions, "an empty session leaves no trace")
        }

    @Test
    fun `ending a session that is not there does nothing`() =
        runTest {
            val sessions = FakeSessionRepository()
            val endSession = EndSession(sessions, SetsIn(lastSetAt = null), clock)

            endSession(SessionId("gone"))

            assertEquals(emptyList(), sessions.sessions)
        }

    @Test
    fun `an ended session is no longer the active one`() =
        runTest {
            val sessions = FakeSessionRepository(listOf(session()))
            val endSession = EndSession(sessions, SetsIn(now), clock)

            endSession(SessionId("s1"))

            assertNull(sessions.findActiveSession(member), "US-06 returns the member to home")
        }
}
