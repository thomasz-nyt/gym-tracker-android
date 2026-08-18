package com.gymtracker.core.domain.health

import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.SessionMetrics
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.session.FakeSessionRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * US-22: ending a session reads and stores health metrics, but only when the member has
 * actually opted in — [HealthMetricsSource.status] alone is never enough (ADR-0038).
 */
class RecordSessionMetricsTest {
    private val sessionId = SessionId("s1")
    private val window = Instant.parse("2026-08-19T18:00:00Z")..Instant.parse("2026-08-19T19:00:00Z")
    private val member = UserId("member-1")

    private fun session() =
        WorkoutSession(
            id = sessionId,
            userId = member,
            gymName = null,
            startedAt = window.start,
            endedAt = window.endInclusive,
            metrics = null,
        )

    @Test
    fun `the toggle off means no read at all, even if the source would answer Ready`() =
        runTest {
            val sessions = FakeSessionRepository(listOf(session()))
            val source = FakeHealthMetricsSource(SessionMetrics(120, 160, 300, "health_connect"))
            val record = RecordSessionMetrics(FakeHealthIntegration(false), source, sessions)

            record(sessionId, window)

            assertNull(sessions.sessions.single().metrics)
            assertFalse(source.called)
        }

    @Test
    fun `toggle on and a real read stores exactly what the source returned`() =
        runTest {
            val sessions = FakeSessionRepository(listOf(session()))
            val metrics = SessionMetrics(120, 160, 300, "health_connect")
            val record = RecordSessionMetrics(FakeHealthIntegration(true), FakeHealthMetricsSource(metrics), sessions)

            record(sessionId, window)

            assertEquals(metrics, sessions.sessions.single().metrics)
        }

    @Test
    fun `a null read (not Ready) writes nothing rather than clearing existing metrics`() =
        runTest {
            val sessions = FakeSessionRepository(listOf(session()))
            val record = RecordSessionMetrics(FakeHealthIntegration(true), FakeHealthMetricsSource(null), sessions)

            record(sessionId, window)

            assertNull(sessions.sessions.single().metrics)
        }
}

private class FakeHealthIntegration(
    private val enabled: Boolean,
) : HealthIntegration {
    override fun observe() = error("not needed for this test")

    override suspend fun current(): Boolean = enabled

    override suspend fun set(enabled: Boolean) = error("not needed for this test")
}

private class FakeHealthMetricsSource(
    private val result: SessionMetrics?,
) : HealthMetricsSource {
    var called: Boolean = false
        private set

    override suspend fun status(): HealthStatus = error("not needed for this test")

    override suspend fun metricsFor(window: ClosedRange<Instant>): SessionMetrics? {
        called = true
        return result
    }
}
