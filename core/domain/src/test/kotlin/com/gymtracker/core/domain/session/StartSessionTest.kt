package com.gymtracker.core.domain.session

import app.cash.turbine.test
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.rest.RestTimerStore
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

    private fun startSession(
        repository: SessionRepository,
        restTimerStore: RestTimerStore = FakeRestTimerStore(),
    ) = StartSession(
        sessions = repository,
        restTimerStore = restTimerStore,
        clock = clock,
        newId = { SessionId("generated") },
    )

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

    @Test
    fun `a new session clears a rest timer left running by whatever ended before it`() =
        runTest {
            val repository = FakeSessionRepository()
            val restTimerStore = FakeRestTimerStore()
            restTimerStore.setRestEndsAt(now.plus(Duration.ofSeconds(45)))

            startSession(repository, restTimerStore)(alice)

            assertEquals(
                null,
                restTimerStore.restEndsAt.first(),
                "a fresh session has no business inheriting a countdown from an unrelated one",
            )
        }

    @Test
    fun `resuming an active session leaves its own rest timer alone`() =
        runTest {
            val existing =
                WorkoutSession(
                    id = SessionId("existing"),
                    userId = alice,
                    gymName = null,
                    startedAt = now.minus(Duration.ofMinutes(5)),
                    endedAt = null,
                    metrics = null,
                )
            val repository = FakeSessionRepository(listOf(existing))
            val restTimerStore = FakeRestTimerStore()
            val restingUntil = now.plus(Duration.ofSeconds(20))
            restTimerStore.setRestEndsAt(restingUntil)

            startSession(repository, restTimerStore)(alice)

            assertEquals(restingUntil, restTimerStore.restEndsAt.first(), "resuming is not a fresh start")
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
