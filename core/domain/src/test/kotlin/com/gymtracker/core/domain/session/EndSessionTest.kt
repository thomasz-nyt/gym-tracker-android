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

/** US-06: ending a session, and discarding one that has nothing in it. */
class EndSessionTest {
    private val now: Instant = Instant.parse("2026-07-28T18:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val alice = UserId("alice")

    private val session =
        WorkoutSession(SessionId("s1"), alice, null, now.minus(Duration.ofHours(1)), null, null)

    private fun endSession(lastSetAt: Instant?) = EndSession(FakeSessions(session), FakeSets(lastSetAt), clock)

    @Test
    fun `a session with sets is ended now`() =
        runTest {
            val sessions = FakeSessions(session)

            EndSession(sessions, FakeSets(now.minus(Duration.ofMinutes(5))), clock)(session.id)

            assertEquals(now, sessions.endedAt)
            assertEquals(false, sessions.discarded)
        }

    @Test
    fun `a session with no sets is discarded rather than saved`() =
        runTest {
            // US-06, third criterion. An empty session is not a workout that happened.
            val sessions = FakeSessions(session)

            EndSession(sessions, FakeSets(lastSetAt = null), clock)(session.id)

            assertEquals(true, sessions.discarded)
            assertNull(sessions.endedAt)
        }

    @Test
    fun `the result says which happened`() =
        runTest {
            assertEquals(EndSessionResult.Ended, endSession(now.minus(Duration.ofMinutes(5)))(session.id))
            assertEquals(EndSessionResult.Discarded, endSession(lastSetAt = null)(session.id))
        }

    @Test
    fun `ending a session that is already gone is not an error`() =
        runTest {
            val sessions = FakeSessions(existing = null)

            val result = EndSession(sessions, FakeSets(null), clock)(SessionId("missing"))

            assertEquals(EndSessionResult.Discarded, result)
        }

    private class FakeSessions(
        private val existing: WorkoutSession?,
    ) : SessionRepository {
        var endedAt: Instant? = null
        var discarded = false

        override fun observeActiveSession(userId: UserId) = throw UnsupportedOperationException()

        override suspend fun findActiveSession(userId: UserId): WorkoutSession? = existing

        override suspend fun startSession(session: WorkoutSession) = Unit

        override suspend fun endSession(
            id: SessionId,
            endedAt: Instant,
        ) {
            this.endedAt = endedAt
        }

        override suspend fun discardSession(id: SessionId) {
            discarded = true
        }
    }

    private class FakeSets(
        private val lastSetAt: Instant?,
    ) : NoSets() {
        override suspend fun lastSetAtInSession(sessionId: SessionId): Instant? = lastSetAt
    }
}
