package com.gymtracker.core.domain.session

import app.cash.turbine.test
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * US-01, first three criteria: starting a session stamps `started_at = now`, reopening
 * returns the existing session rather than starting a second, and there is never more
 * than one active session per member.
 */
class StartSessionTest {
    private val now: Instant = Instant.parse("2026-07-26T18:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val alice = UserId("alice")
    private val bob = UserId("bob")

    private fun startSession(repository: SessionRepository) =
        StartSession(sessions = repository, clock = clock, newId = { SessionId("generated") })

    @Test
    fun `starting with no active session stamps started_at with now`() =
        runTest {
            val repository = FakeSessionRepository()

            val result = startSession(repository)(alice)

            val started = assertIs<StartSessionResult.Started>(result)
            assertEquals(now, started.session.startedAt)
            assertEquals(alice, started.session.userId)
            assertEquals(null, started.session.endedAt)
            assertEquals(listOf(started.session), repository.sessions, "the session is persisted")
        }

    @Test
    fun `starting again resumes the active session instead of creating a second`() =
        runTest {
            val existing =
                WorkoutSession(
                    id = SessionId("existing"),
                    userId = alice,
                    gymName = null,
                    startedAt = now.minus(Duration.ofMinutes(30)),
                    endedAt = null,
                    metrics = null,
                )
            val repository = FakeSessionRepository(listOf(existing))

            val result = startSession(repository)(alice)

            assertEquals(StartSessionResult.Resumed(existing), result)
            assertEquals(listOf(existing), repository.sessions, "only one active session per member")
        }

    @Test
    fun `an ended session does not block starting a new one`() =
        runTest {
            val finished =
                WorkoutSession(
                    id = SessionId("finished"),
                    userId = alice,
                    gymName = null,
                    startedAt = now.minus(Duration.ofDays(1)),
                    endedAt = now.minus(Duration.ofDays(1)).plus(Duration.ofHours(1)),
                    metrics = null,
                )
            val repository = FakeSessionRepository(listOf(finished))

            val result = startSession(repository)(alice)

            assertTrue(result is StartSessionResult.Started)
            assertEquals(2, repository.sessions.size)
        }

    @Test
    fun `another member's active session does not block mine`() =
        runTest {
            val bobs =
                WorkoutSession(
                    id = SessionId("bobs"),
                    userId = bob,
                    gymName = null,
                    startedAt = now.minus(Duration.ofMinutes(10)),
                    endedAt = null,
                    metrics = null,
                )
            val repository = FakeSessionRepository(listOf(bobs))

            val result = startSession(repository)(alice)

            assertTrue(result is StartSessionResult.Started, "the invariant is per member")
        }

    @Test
    fun `the active session is observable and returns to null when discarded`() =
        runTest {
            val repository = FakeSessionRepository()
            val useCase = startSession(repository)

            repository.observeActiveSession(alice).test {
                assertEquals(null, awaitItem())

                val started = assertIs<StartSessionResult.Started>(useCase(alice))
                assertEquals(started.session, awaitItem())

                repository.deleteSession(started.session.id)
                assertEquals(null, awaitItem())
            }
        }
}
