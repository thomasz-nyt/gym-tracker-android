package com.gymtracker.core.data.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.sync.SyncPayloadCodec
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.SessionMetrics
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-01 against a real Room database, per `specs/testing-strategy.md`
 * ("Repository + sync | Fake remote, real Room").
 */
@RunWith(RobolectricTestRunner::class)
class RoomSessionRepositoryTest {
    private lateinit var database: GymTrackerDatabase
    private lateinit var repository: RoomSessionRepository

    private val now: Instant = Instant.parse("2026-07-26T18:00:00Z")
    private val alice = UserId("alice")
    private val bob = UserId("bob")

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    GymTrackerDatabase::class.java,
                ).build()
        repository =
            RoomSessionRepository(database.sessionDao(), database, SyncPayloadCodec(Json { ignoreUnknownKeys = true }))
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun session(
        id: String,
        userId: UserId = alice,
        startedAt: Instant = now,
        endedAt: Instant? = null,
    ) = WorkoutSession(
        id = SessionId(id),
        userId = userId,
        gymName = null,
        startedAt = startedAt,
        endedAt = endedAt,
        metrics = null,
    )

    @Test
    fun `a started session round-trips through the database unchanged`() =
        runTest {
            val started = session("s1", startedAt = now)

            repository.startSession(started)

            assertEquals(started, repository.findActiveSession(alice))
        }

    @Test
    fun `instants survive the round trip to the millisecond`() =
        runTest {
            val odd = Instant.parse("2026-07-26T18:00:00.123Z")

            repository.startSession(session("s1", startedAt = odd))

            assertEquals(odd, repository.findActiveSession(alice)?.startedAt)
        }

    @Test
    fun `there is no active session when none was started`() =
        runTest {
            assertNull(repository.findActiveSession(alice))
        }

    @Test
    fun `an ended session is no longer the active one`() =
        runTest {
            repository.startSession(session("s1"))

            repository.endSession(SessionId("s1"), now.plus(Duration.ofHours(1)))

            assertNull(repository.findActiveSession(alice))
        }

    @Test
    fun `ending a session records the timestamp it was given`() =
        runTest {
            val endedAt = now.plus(Duration.ofMinutes(75))
            repository.startSession(session("s1"))

            repository.endSession(SessionId("s1"), endedAt)

            assertEquals(endedAt, repository.findSession(SessionId("s1"))?.endedAt)
        }

    @Test
    fun `a discarded session is gone entirely`() =
        runTest {
            repository.startSession(session("s1"))

            repository.deleteSession(SessionId("s1"))

            assertNull(repository.findSession(SessionId("s1")))
        }

    @Test
    fun `each member sees only their own active session`() =
        runTest {
            repository.startSession(session("alices", userId = alice))
            repository.startSession(session("bobs", userId = bob))

            assertEquals(SessionId("alices"), repository.findActiveSession(alice)?.id)
            assertEquals(SessionId("bobs"), repository.findActiveSession(bob)?.id)
        }

    @Test
    fun `the active session is observable across start and discard`() =
        runTest {
            repository.observeActiveSession(alice).test {
                assertNull(awaitItem())

                repository.startSession(session("s1"))
                assertEquals(SessionId("s1"), awaitItem()?.id)

                repository.deleteSession(SessionId("s1"))
                assertNull(awaitItem())
            }
        }

    // --- Health metrics (US-22) ---

    @Test
    fun `saved metrics round-trip through the database`() =
        runTest {
            repository.startSession(session("s1"))
            val metrics = SessionMetrics(128, 171, 340, "health_connect")

            repository.saveMetrics(SessionId("s1"), metrics)

            assertEquals(metrics, repository.findSession(SessionId("s1"))?.metrics)
        }

    @Test
    fun `metrics with every field null but a source still round-trip as non-null`() =
        runTest {
            // The "attempted, found nothing" case (health-connect.md): distinguishable from
            // never having read at all, which is why source alone must survive the round trip.
            repository.startSession(session("s1"))
            val metrics = SessionMetrics(null, null, null, "health_connect")

            repository.saveMetrics(SessionId("s1"), metrics)

            assertEquals(metrics, repository.findSession(SessionId("s1"))?.metrics)
        }

    @Test
    fun `saving metrics does not touch the session's other columns`() =
        runTest {
            val endedAt = now.plus(Duration.ofMinutes(45))
            repository.startSession(session("s1"))
            repository.endSession(SessionId("s1"), endedAt)

            repository.saveMetrics(SessionId("s1"), SessionMetrics(128, 171, 340, "health_connect"))

            assertEquals(endedAt, repository.findSession(SessionId("s1"))?.endedAt)
        }

    @Test
    fun `the most recently started session wins when several are somehow open`() =
        runTest {
            // The one-active-session rule is enforced by the StartSession use case, not by a
            // database constraint (Room cannot express a partial unique index). If the rule is
            // ever violated, the newest session is the one the member is in.
            repository.startSession(session("older", startedAt = now.minus(Duration.ofHours(2))))
            repository.startSession(session("newer", startedAt = now))

            assertEquals(SessionId("newer"), repository.findActiveSession(alice)?.id)
        }
}
