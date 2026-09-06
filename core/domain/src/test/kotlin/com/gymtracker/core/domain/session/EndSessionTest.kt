package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.rest.RestTimerStore
import com.gymtracker.core.domain.set.NoSets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    private val rest = FakeRestTimerStore()

    private fun endSession(
        sessions: FakeSessionRepository,
        sets: SetsIn,
    ) = EndSession(sessions, sets, rest, clock)

    @Test
    fun `ending a session with sets closes it at the moment it was ended`() =
        runTest {
            // Not at the last set's time: that is the stale-session path (US-01), where nobody
            // was there to press the button. Here the member is standing in the gym pressing it.
            val sessions = FakeSessionRepository(listOf(session()))
            val endSession = endSession(sessions, SetsIn(now.minus(Duration.ofMinutes(3))))

            val result = endSession(SessionId("s1"))

            assertEquals(EndSessionResult.Ended(now), result)
            assertEquals(now, sessions.sessions.single().endedAt)
        }

    @Test
    fun `a session with no sets is discarded rather than saved`() =
        runTest {
            val sessions = FakeSessionRepository(listOf(session()))
            val endSession = endSession(sessions, SetsIn(lastSetAt = null))

            val result = endSession(SessionId("s1"))

            assertEquals(EndSessionResult.Discarded, result)
            assertEquals(emptyList(), sessions.sessions, "an empty session leaves no trace")
        }

    @Test
    fun `ending a session that is not there does nothing`() =
        runTest {
            val sessions = FakeSessionRepository()
            val endSession = endSession(sessions, SetsIn(lastSetAt = null))

            endSession(SessionId("gone"))

            assertEquals(emptyList(), sessions.sessions)
        }

    @Test
    fun `an ended session is no longer the active one`() =
        runTest {
            val sessions = FakeSessionRepository(listOf(session()))
            val endSession = endSession(sessions, SetsIn(now))

            endSession(SessionId("s1"))

            assertNull(sessions.findActiveSession(member), "US-06 returns the member to home")
        }

    // US-56 as amended (2026-09-05): the rest belongs to the session. Until this, only StartSession
    // cleared it — for the next workout — so a rest running at "Finish workout" kept counting in the
    // shade and "Rest over" arrived for a session already in history.

    @Test
    fun `ending a session ends the rest that was running`() =
        runTest {
            val sessions = FakeSessionRepository(listOf(session()))
            rest.setRest(now.plusSeconds(45), Duration.ofSeconds(60))

            endSession(sessions, SetsIn(now.minusSeconds(15)))(SessionId("s1"))

            assertNull(rest.restEndsAt.first(), "a countdown to nothing")
            assertNull(rest.restTotal.first())
        }

    @Test
    fun `discarding an empty session ends its rest too`() =
        runTest {
            // A set logged and then deleted leaves a rest running over a session with no sets.
            val sessions = FakeSessionRepository(listOf(session()))
            rest.setRest(now.plusSeconds(45), Duration.ofSeconds(60))

            endSession(sessions, SetsIn(lastSetAt = null))(SessionId("s1"))

            assertNull(rest.restEndsAt.first())
        }

    private class FakeRestTimerStore : RestTimerStore {
        private val endsAt = MutableStateFlow<Instant?>(null)
        private val total = MutableStateFlow<Duration?>(null)
        private val default = MutableStateFlow(Duration.ofSeconds(60))
        private val asked = MutableStateFlow(false)

        override val restEndsAt = endsAt
        override val restTotal = total
        override val defaultRest = default
        override val shouldAskForNotificationPermission = asked.map { !it }

        override suspend fun setRestEndsAt(instant: Instant?) {
            endsAt.value = instant
            if (instant == null) total.value = null
        }

        override suspend fun setRest(
            endsAt: Instant,
            total: Duration,
        ) {
            this.endsAt.value = endsAt
            this.total.value = total
        }

        override suspend fun setDefaultRest(rest: Duration) {
            default.value = rest
        }

        override suspend fun markNotificationPermissionAsked() {
            asked.value = true
        }
    }
}
