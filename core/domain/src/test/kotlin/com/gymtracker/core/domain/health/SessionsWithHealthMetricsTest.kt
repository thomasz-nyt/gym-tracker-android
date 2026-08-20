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

/**
 * US-23: the number the revoke offer names. Counted before anything is deleted, because an
 * offer to delete nothing is the nag US-20 forbids — the dialog only appears when this is
 * greater than zero.
 */
class SessionsWithHealthMetricsTest {
    private val member = UserId("member-1")
    private val other = UserId("member-2")
    private val start = Instant.parse("2026-08-19T18:00:00Z")

    private fun session(
        id: String,
        owner: UserId = member,
        metrics: SessionMetrics? = SessionMetrics(120, 160, 300, "health_connect"),
        ended: Boolean = true,
    ) = WorkoutSession(
        id = SessionId(id),
        userId = owner,
        gymName = null,
        startedAt = start,
        endedAt = if (ended) start.plusSeconds(3600) else null,
        metrics = metrics,
    )

    @Test
    fun `counts a workout carrying any metrics at all`() =
        runTest {
            val sessions = FakeSessionRepository(listOf(session("s1")))

            assertEquals(1, SessionsWithHealthMetrics(sessions)(member))
        }

    @Test
    fun `counts a workout whose read found nothing but recorded that it looked`() =
        runTest {
            val looked = session("s1", metrics = SessionMetrics(null, null, null, "health_connect"))
            val sessions = FakeSessionRepository(listOf(looked))

            assertEquals(1, SessionsWithHealthMetrics(sessions)(member))
        }

    @Test
    fun `zero for a member who never imported anything`() =
        runTest {
            val sessions = FakeSessionRepository(listOf(session("s1", metrics = null)))

            assertEquals(0, SessionsWithHealthMetrics(sessions)(member))
        }

    @Test
    fun `another member's workouts are not counted`() =
        runTest {
            val sessions = FakeSessionRepository(listOf(session("s1", owner = other)))

            assertEquals(0, SessionsWithHealthMetrics(sessions)(member))
        }

    @Test
    fun `the active session counts too — it is the member's data like any other`() =
        runTest {
            val sessions = FakeSessionRepository(listOf(session("s1", ended = false)))

            assertEquals(1, SessionsWithHealthMetrics(sessions)(member))
        }
}
