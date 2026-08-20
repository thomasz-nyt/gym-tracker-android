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
import kotlin.test.assertNull

/**
 * US-23: revoking deletes what was imported, and nothing else (ADR-0040).
 *
 * The case that matters most is [a revoked workout is one that never had metrics] — clearing
 * the three numbers but leaving `source` set would render "not recorded" forever, which under
 * US-22 means a read happened and found nothing. That is a different, false statement.
 */
class ForgetHealthMetricsTest {
    private val member = UserId("member-1")
    private val other = UserId("member-2")
    private val start = Instant.parse("2026-08-19T18:00:00Z")

    private fun session(
        id: String,
        owner: UserId = member,
        metrics: SessionMetrics? = SessionMetrics(120, 160, 300, "health_connect"),
    ) = WorkoutSession(
        id = SessionId(id),
        userId = owner,
        gymName = null,
        startedAt = start,
        endedAt = start.plusSeconds(3600),
        metrics = metrics,
    )

    @Test
    fun `a revoked workout is one that never had metrics, source marker included`() =
        runTest {
            val sessions = FakeSessionRepository(listOf(session("s1")))

            ForgetHealthMetrics(sessions)(member)

            assertNull(sessions.sessions.single().metrics)
        }

    @Test
    fun `the workout itself survives untouched — only the metrics go`() =
        runTest {
            val original = session("s1")
            val sessions = FakeSessionRepository(listOf(original))

            ForgetHealthMetrics(sessions)(member)

            assertEquals(1, sessions.sessions.size, "revoking must never delete a workout")
            assertEquals(original.copy(metrics = null), sessions.sessions.single())
        }

    @Test
    fun `a read that found nothing is still imported data, and is cleared too`() =
        runTest {
            // US-22 stores this when a real read produced no samples: every value null, but
            // the source marker set. It is the app asserting "we looked" — which is exactly
            // what the member is asking to forget.
            val looked = session("s1", metrics = SessionMetrics(null, null, null, "health_connect"))
            val sessions = FakeSessionRepository(listOf(looked))

            ForgetHealthMetrics(sessions)(member)

            assertNull(sessions.sessions.single().metrics)
        }

    @Test
    fun `another member's workouts are never touched`() =
        runTest {
            val theirs = session("s2", owner = other)
            val sessions = FakeSessionRepository(listOf(session("s1"), theirs))

            ForgetHealthMetrics(sessions)(member)

            assertEquals(theirs, sessions.sessions.single { it.userId == other })
        }

    @Test
    fun `it reports how many workouts it cleared, so the UI can name a real number`() =
        runTest {
            val sessions =
                FakeSessionRepository(
                    listOf(
                        session("s1"),
                        session("s2"),
                        session("s3", metrics = null),
                        session("s4", owner = other),
                    ),
                )

            assertEquals(2, ForgetHealthMetrics(sessions)(member))
        }

    @Test
    fun `running it twice is not an error and clears nothing the second time`() =
        runTest {
            val sessions = FakeSessionRepository(listOf(session("s1")))
            val forget = ForgetHealthMetrics(sessions)

            forget(member)
            val second = forget(member)

            assertEquals(0, second)
            assertNull(sessions.sessions.single().metrics)
        }
}
