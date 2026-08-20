package com.gymtracker.core.data.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.SessionMetrics
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * US-23 against real SQL (ADR-0040).
 *
 * The domain suite proves the rule with a fake; this proves the four columns actually go null
 * together in SQLite, and that the `WHERE`-guard keeps a metrics-free row's `updated_at` and
 * `sync_state` untouched — neither of which a fake can tell you anything about.
 */
@RunWith(RobolectricTestRunner::class)
class SessionMetricsRevocationTest {
    private lateinit var database: GymTrackerDatabase
    private lateinit var dao: SessionDao
    private lateinit var repository: RoomSessionRepository

    private val now: Instant = Instant.parse("2026-08-19T18:00:00Z")
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
        dao = database.sessionDao()
        repository = RoomSessionRepository(dao)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun start(
        id: String,
        owner: UserId = alice,
        metrics: SessionMetrics? = null,
    ): SessionId {
        val sessionId = SessionId(id)
        repository.startSession(
            WorkoutSession(
                id = sessionId,
                userId = owner,
                gymName = "Gym",
                startedAt = now,
                endedAt = null,
                metrics = null,
            ),
        )
        repository.endSession(sessionId, now.plusSeconds(3600))
        if (metrics != null) repository.saveMetrics(sessionId, metrics)
        return sessionId
    }

    @Test
    fun `a cleared session reads back as one that never had metrics, not as one read for`() =
        runTest {
            val id = start("s1", metrics = SessionMetrics(120, 160, 300, "health_connect"))

            repository.clearMetrics(alice)

            // Not SessionMetrics(null, null, null, null) — null outright. Anything else means
            // metrics_source survived, and every cleared workout would render "not recorded".
            assertNull(repository.findSession(id)!!.metrics)
        }

    @Test
    fun `a read that found no samples is cleared too`() =
        runTest {
            val id = start("s1", metrics = SessionMetrics(null, null, null, "health_connect"))

            assertEquals(1, repository.clearMetrics(alice))

            assertNull(repository.findSession(id)!!.metrics)
        }

    @Test
    fun `everything else about the row is untouched`() =
        runTest {
            val id = start("s1", metrics = SessionMetrics(120, 160, 300, "health_connect"))
            val before = dao.find(id.value)!!

            repository.clearMetrics(alice)

            val after = dao.find(id.value)!!
            assertEquals(before.userId, after.userId)
            assertEquals(before.gymName, after.gymName)
            assertEquals(before.startedAt, after.startedAt)
            assertEquals(before.endedAt, after.endedAt)
            assertEquals(before.routineName, after.routineName)
            assertEquals(before.routineId, after.routineId)
            assertNull(after.avgHr)
            assertNull(after.maxHr)
            assertNull(after.activeKcal)
            assertNull(after.metricsSource)
        }

    @Test
    fun `a session with no metrics keeps its own updated_at — revoking does not dirty history`() =
        runTest {
            val untouched = start("s1")
            val carrying = start("s2", metrics = SessionMetrics(120, 160, 300, "health_connect"))
            val before = dao.find(untouched.value)!!
            val carryingBefore = dao.find(carrying.value)!!

            repository.clearMetrics(alice)

            assertEquals(before.updatedAt, dao.find(untouched.value)!!.updatedAt)
            assertNotEquals(carryingBefore.updatedAt, dao.find(carrying.value)!!.updatedAt)
        }

    @Test
    fun `another member's metrics survive`() =
        runTest {
            val theirs = start("s2", owner = bob, metrics = SessionMetrics(99, 101, 50, "health_connect"))

            assertEquals(0, repository.clearMetrics(alice))

            assertEquals(SessionMetrics(99, 101, 50, "health_connect"), repository.findSession(theirs)!!.metrics)
        }

    @Test
    fun `the count agrees with what clearing actually clears`() =
        runTest {
            start("s1", metrics = SessionMetrics(120, 160, 300, "health_connect"))
            start("s2", metrics = SessionMetrics(null, null, null, "health_connect"))
            start("s3")
            start("s4", owner = bob, metrics = SessionMetrics(99, 101, 50, "health_connect"))

            assertEquals(2, repository.countSessionsWithMetrics(alice))
            assertEquals(2, repository.clearMetrics(alice))
            assertEquals(0, repository.countSessionsWithMetrics(alice))
        }

    @Test
    fun `sessions are never deleted, only cleared`() =
        runTest {
            start("s1", metrics = SessionMetrics(120, 160, 300, "health_connect"))
            start("s2")

            repository.clearMetrics(alice)

            assertEquals(2, dao.allForUser(alice.value).size)
        }
}
