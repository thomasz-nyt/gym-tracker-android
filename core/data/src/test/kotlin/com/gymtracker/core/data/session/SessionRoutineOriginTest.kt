package com.gymtracker.core.data.session

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.routine.RoomRoutineRepository
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineOrigin
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * US-32 (ADR-0028) against the real schema: a session's routine name and id round-trip, and
 * neither is a live reference — renaming or deleting the routine leaves an already-started
 * session's provenance exactly as it was written.
 */
@RunWith(RobolectricTestRunner::class)
class SessionRoutineOriginTest {
    private lateinit var database: GymTrackerDatabase
    private lateinit var sessions: RoomSessionRepository
    private lateinit var routines: RoomRoutineRepository

    private val now: Instant = Instant.parse("2026-08-09T18:00:00Z")
    private val alice = UserId("alice")

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    GymTrackerDatabase::class.java,
                ).build()
        sessions = RoomSessionRepository(database.sessionDao())
        routines = RoomRoutineRepository(database.routineDao())
    }

    @After
    fun tearDown() = database.close()

    private fun session(
        id: String,
        routine: RoutineOrigin? = null,
    ) = WorkoutSession(
        id = SessionId(id),
        userId = alice,
        gymName = null,
        startedAt = now,
        endedAt = null,
        metrics = null,
        routine = routine,
    )

    @Test
    fun `a routine's name and id round-trip through Room`() =
        runTest {
            val origin = RoutineOrigin(id = "r1", name = "Upper A")

            sessions.startSession(session("s1", routine = origin))

            assertEquals(origin, sessions.findSession(SessionId("s1"))?.routine)
        }

    @Test
    fun `a session started without a routine carries none, not a row of empty strings`() =
        runTest {
            sessions.startSession(session("s1"))

            assertNull(sessions.findSession(SessionId("s1"))?.routine)
        }

    @Test
    fun `renaming the routine afterwards does not change what an already-started session says`() =
        runTest {
            routines.add(Routine(RoutineId("r1"), alice, "Upper A", 1))
            sessions.startSession(session("s1", routine = RoutineOrigin(id = "r1", name = "Upper A")))

            routines.rename(RoutineId("r1"), "Push A")

            assertEquals("Upper A", sessions.findSession(SessionId("s1"))?.routine?.name)
        }

    @Test
    fun `deleting the routine afterwards does not change what an already-started session says`() =
        runTest {
            routines.add(Routine(RoutineId("r1"), alice, "Upper A", 1))
            sessions.startSession(session("s1", routine = RoutineOrigin(id = "r1", name = "Upper A")))

            routines.delete(RoutineId("r1"))

            assertEquals("Upper A", sessions.findSession(SessionId("s1"))?.routine?.name)
        }

    @Test
    fun `sessions has no foreign key pointing at routines`() {
        // ADR-0028's structural guarantee, checked against the schema Room actually built.
        // This can't be a reflective check over @Query annotations the way the "routine"
        // field checks elsewhere in this repo are: androidx.room.Query's retention is
        // RetentionPolicy.CLASS (confirmed against the compiled room-common jar), so it is
        // gone by the time this test runs. The CREATE TABLE SQL is the ground truth instead
        // — if a future migration ever added a foreign key from `sessions` to `routines`,
        // it would show up here.
        val createSql = createTableSql("sessions")

        assertFalse(
            createSql.contains("REFERENCES", ignoreCase = true),
            "sessions gained a foreign key: $createSql",
        )
    }

    private fun createTableSql(table: String): String {
        database.openHelper.readableDatabase
            .query(SimpleSQLiteQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)))
            .use { cursor ->
                assertTrue(cursor.moveToFirst(), "table $table not found in sqlite_master")
                return cursor.getString(0)
            }
    }
}

/** The v8 to v9 upgrade is additive: nothing already on the device is disturbed by it. */
@RunWith(RobolectricTestRunner::class)
class SessionRoutineOriginMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            GymTrackerDatabase::class.java,
        )

    @Test
    fun `migrating from 8 to 9 adds routine provenance and keeps every existing row`() {
        val name = "migration-8-9.db"

        helper.createDatabase(name, 8).use { v8 ->
            v8.execSQL(
                "INSERT INTO sessions (id, user_id, gym_name, started_at, ended_at, avg_hr, max_hr, " +
                    "active_kcal, metrics_source, updated_at, sync_state) " +
                    "VALUES ('s1', 'u1', NULL, 1000, NULL, NULL, NULL, NULL, NULL, 1000, 'PENDING')",
            )
        }

        val v9 = helper.runMigrationsAndValidate(name, 9, true, GymTrackerDatabase.MIGRATION_8_9)

        v9.query("SELECT COUNT(*) FROM sessions").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0), "sessions lost a row to an additive migration")
        }
        v9.query("SELECT routine_name, routine_id FROM sessions WHERE id = 's1'").use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(0), "a session predating this migration has no routine")
            assertTrue(it.isNull(1))
        }
    }
}
