package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-01, fourth criterion: on app open, an active session whose last activity is more
 * than four hours old prompts to be finished or discarded. The app never invents an
 * end time silently.
 */
class StaleSessionPolicyTest {
    private val now: Instant = Instant.parse("2026-07-26T18:00:00Z")

    private fun session(
        startedAt: Instant,
        endedAt: Instant? = null,
    ) = WorkoutSession(
        id = SessionId("s1"),
        userId = UserId("u1"),
        gymName = null,
        startedAt = startedAt,
        endedAt = endedAt,
        metrics = null,
    )

    @Test
    fun `a session with recent sets is not stale`() {
        val result =
            StaleSessionPolicy.evaluate(
                session = session(startedAt = now.minus(Duration.ofHours(9))),
                lastSetAt = now.minus(Duration.ofMinutes(20)),
                now = now,
            )

        assertNull(result, "last activity 20 minutes ago is not stale, however old the session is")
    }

    @Test
    fun `a session with old sets is finished at its last set`() {
        val lastSetAt = now.minus(Duration.ofHours(5))

        val result =
            StaleSessionPolicy.evaluate(
                session = session(startedAt = now.minus(Duration.ofHours(9))),
                lastSetAt = lastSetAt,
                now = now,
            )

        assertEquals(
            StaleSessionPrompt.Finish(session(startedAt = now.minus(Duration.ofHours(9))), lastSetAt),
            result,
            "ended_at must be the last set's timestamp, never now",
        )
    }

    @Test
    fun `an old session with no sets is discarded, not finished`() {
        val startedAt = now.minus(Duration.ofHours(5))

        val result =
            StaleSessionPolicy.evaluate(session = session(startedAt), lastSetAt = null, now = now)

        assertEquals(
            StaleSessionPrompt.Discard(session(startedAt)),
            result,
            "with no sets there is no honest end time, so the session is discarded (US-06)",
        )
    }

    @Test
    fun `a young session with no sets is not stale`() {
        val result =
            StaleSessionPolicy.evaluate(
                session = session(startedAt = now.minus(Duration.ofHours(1))),
                lastSetAt = null,
                now = now,
            )

        assertNull(result)
    }

    @Test
    fun `exactly four hours is not yet stale`() {
        val result =
            StaleSessionPolicy.evaluate(
                session = session(startedAt = now.minus(Duration.ofHours(4))),
                lastSetAt = null,
                now = now,
            )

        assertNull(result, "the criterion is 'more than 4 hours', so the boundary is not stale")
    }

    @Test
    fun `a millisecond past four hours is stale`() {
        val startedAt = now.minus(Duration.ofHours(4)).minusMillis(1)

        val result =
            StaleSessionPolicy.evaluate(session = session(startedAt), lastSetAt = null, now = now)

        assertEquals(StaleSessionPrompt.Discard(session(startedAt)), result)
    }

    @Test
    fun `an already ended session is never stale`() {
        val result =
            StaleSessionPolicy.evaluate(
                session =
                    session(
                        startedAt = now.minus(Duration.ofDays(3)),
                        endedAt = now.minus(Duration.ofDays(3)).plus(Duration.ofHours(1)),
                    ),
                lastSetAt = null,
                now = now,
            )

        assertNull(result, "the prompt is only ever about the active session")
    }

    @Test
    fun `last activity is the last set even when it predates nothing else`() {
        // A set logged before the threshold keeps the session alive even though the
        // session itself started long ago — last activity, not session age, is the test.
        val result =
            StaleSessionPolicy.evaluate(
                session = session(startedAt = now.minus(Duration.ofDays(1))),
                lastSetAt = now.minus(Duration.ofHours(3)),
                now = now,
            )

        assertNull(result)
    }
}
