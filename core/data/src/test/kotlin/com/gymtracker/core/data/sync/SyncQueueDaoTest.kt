package com.gymtracker.core.data.sync

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.gymtracker.core.data.database.GymTrackerDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** US-57 against the real schema: `sync_queue` itself, independent of who writes into it. */
@RunWith(RobolectricTestRunner::class)
class SyncQueueDaoTest {
    private lateinit var database: GymTrackerDatabase
    private lateinit var dao: SyncQueueDao

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GymTrackerDatabase::class.java)
                .build()
        dao = database.syncQueueDao()
    }

    @After
    fun tearDown() = database.close()

    private fun writeRow(
        id: String,
        entityId: String = "e-$id",
        createdAt: Long = 1_000L,
    ) = SyncQueueEntity(
        id = id,
        entity = SyncEntityNames.SETS,
        entityId = entityId,
        op = SYNC_OP_WRITE,
        payloadJson = """{"id":"$entityId"}""",
        createdAt = createdAt,
    )

    @Test
    fun `an inserted row reads back with every field intact`() =
        runTest {
            dao.insert(writeRow("q1", entityId = "set-1", createdAt = 500L))

            val row = dao.oldestFirst().single()
            assertEquals("q1", row.id)
            assertEquals(SyncEntityNames.SETS, row.entity)
            assertEquals("set-1", row.entityId)
            assertEquals(SYNC_OP_WRITE, row.op)
            assertEquals("""{"id":"set-1"}""", row.payloadJson)
            assertEquals(500L, row.createdAt)
            assertEquals(0, row.attempts, "a fresh row has never been retried")
        }

    @Test
    fun `a delete row carries no payload`() =
        runTest {
            dao.insert(syncDeleteEntry(SyncEntityNames.SETS, "set-1"))

            val row = dao.oldestFirst().single()
            assertEquals(SYNC_OP_DELETE, row.op)
            assertNull(row.payloadJson)
        }

    @Test
    fun `oldestFirst orders by created_at, not insertion order`() =
        runTest {
            dao.insert(writeRow("later", createdAt = 2_000L))
            dao.insert(writeRow("earlier", createdAt = 1_000L))

            assertEquals(listOf("earlier", "later"), dao.oldestFirst().map { it.id })
        }

    @Test
    fun `deleting a drained row removes only that row`() =
        runTest {
            dao.insert(writeRow("keep"))
            dao.insert(writeRow("drain"))

            dao.delete("drain")

            assertEquals(listOf("keep"), dao.oldestFirst().map { it.id })
        }

    @Test
    fun `pendingCount is the number of rows still queued`() =
        runTest {
            assertEquals(0, dao.pendingCount())

            dao.insert(writeRow("q1"))
            dao.insert(writeRow("q2"))
            assertEquals(2, dao.pendingCount())

            dao.delete("q1")
            assertEquals(1, dao.pendingCount())
        }
}

/** The v9 to v10 upgrade only adds `sync_queue` — every existing table is untouched. */
@RunWith(RobolectricTestRunner::class)
class SyncQueueMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            GymTrackerDatabase::class.java,
        )

    @Test
    fun `migrating from 9 to 10 adds an empty sync_queue and keeps every existing row`() {
        val name = "migration-9-10.db"

        helper.createDatabase(name, 9).use { v9 ->
            v9.execSQL(
                "INSERT INTO sessions (id, user_id, gym_name, started_at, ended_at, avg_hr, max_hr, " +
                    "active_kcal, metrics_source, updated_at, sync_state, routine_name, routine_id) " +
                    "VALUES ('s1', 'u1', NULL, 1000, NULL, NULL, NULL, NULL, NULL, 1000, 'PENDING', NULL, NULL)",
            )
        }

        val v10 = helper.runMigrationsAndValidate(name, 10, true, GymTrackerDatabase.MIGRATION_9_10)

        v10.query("SELECT COUNT(*) FROM sessions").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0), "an additive migration must not lose a row already on the device")
        }
        v10.query("SELECT COUNT(*) FROM sync_queue").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0), "the queue starts empty — nothing retroactively enqueues")
        }
    }
}
