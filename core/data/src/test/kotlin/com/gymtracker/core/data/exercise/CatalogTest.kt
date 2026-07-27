package com.gymtracker.core.data.exercise

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.domain.model.BodyPart
import com.gymtracker.core.domain.model.Equipment
import com.gymtracker.core.domain.model.UserId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** US-02, catalog half: the bundled catalog seeds once and is searchable offline. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CatalogTest {
    private lateinit var database: GymTrackerDatabase
    private lateinit var dao: ExerciseDao

    private val json = Json { ignoreUnknownKeys = true }
    private val member = UserId("alice")

    private val bundled =
        """
        [
          {"id":"a","name":"Bench Press","primaryMuscles":["CHEST"],"secondaryMuscles":["TRICEPS"],
           "equipment":"BARBELL","instructions":["Lie down.","Press."],"source":"free-exercise-db"},
          {"id":"b","name":"ab roller","primaryMuscles":["CORE"],"secondaryMuscles":[],
           "equipment":"OTHER","instructions":[],"source":"free-exercise-db"},
          {"id":"c","name":"Cable Fly","primaryMuscles":["CHEST"],"secondaryMuscles":[],
           "equipment":"CABLE","instructions":[],"source":"free-exercise-db"}
        ]
        """.trimIndent()

    private fun seeder(payload: String = bundled) =
        CatalogSeeder(
            dao = dao,
            assets = CatalogAssetReader { payload.byteInputStream() },
            json = json,
            io = UnconfinedTestDispatcher(),
        )

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    GymTrackerDatabase::class.java,
                ).build()
        dao = database.exerciseDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `seeding inserts the bundled catalog`() =
        runTest {
            val inserted = seeder().seedIfEmpty(now = 1L)

            assertEquals(3, inserted)
            assertEquals(3, dao.count())
        }

    @Test
    fun `seeding twice does not duplicate the catalog`() =
        runTest {
            seeder().seedIfEmpty(now = 1L)
            val second = seeder().seedIfEmpty(now = 2L)

            assertEquals(0, second, "already seeded")
            assertEquals(3, dao.count())
        }

    @Test
    fun `an exercise round-trips into the domain model`() =
        runTest {
            seeder().seedIfEmpty(now = 1L)

            val bench = RoomExerciseCatalog(dao, json).search("Bench", member).first().single()

            assertEquals("Bench Press", bench.name)
            assertEquals(listOf(BodyPart.CHEST), bench.primaryMuscles)
            assertEquals(listOf(BodyPart.TRICEPS), bench.secondaryMuscles)
            assertEquals(Equipment.BARBELL, bench.equipment)
            assertEquals(listOf("Lie down.", "Press."), bench.instructions)
            assertEquals("free-exercise-db", bench.source)
        }

    @Test
    fun `a blank query returns the whole catalog alphabetically, ignoring case`() =
        runTest {
            seeder().seedIfEmpty(now = 1L)

            val names = RoomExerciseCatalog(dao, json).search("", member).first().map { it.name }

            assertEquals(listOf("ab roller", "Bench Press", "Cable Fly"), names)
        }

    @Test
    fun `search matches anywhere in the name and is case insensitive`() =
        runTest {
            seeder().seedIfEmpty(now = 1L)
            val catalog = RoomExerciseCatalog(dao, json)

            assertEquals(listOf("Bench Press"), catalog.search("press", member).first().map { it.name })
            assertEquals(listOf("ab roller"), catalog.search("ROLL", member).first().map { it.name })
            assertEquals(listOf("Cable Fly"), catalog.search("  Cable  ".trim(), member).first().map { it.name })
        }

    @Test
    fun `search emits again when the catalog changes`() =
        runTest {
            RoomExerciseCatalog(dao, json).search("", member).test {
                assertEquals(emptyList(), awaitItem())

                seeder().seedIfEmpty(now = 1L)

                assertEquals(3, awaitItem().size)
            }
        }

    @Test
    fun `a query matching nothing returns nothing rather than everything`() =
        runTest {
            seeder().seedIfEmpty(now = 1L)

            assertTrue(RoomExerciseCatalog(dao, json).search("zzzz", member).first().isEmpty())
        }
}

/**
 * The v1 to v2 migration adds the catalog table. It must not disturb `sessions`: a member with
 * a workout in progress when they install the update keeps it (US-01).
 */
@RunWith(RobolectricTestRunner::class)
class CatalogMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            GymTrackerDatabase::class.java,
        )

    @Test
    fun `migrating from 1 to 2 adds exercises and keeps the active session`() {
        val name = "migration-test.db"

        helper.createDatabase(name, 1).use { v1 ->
            v1.execSQL(
                "INSERT INTO sessions (id, user_id, gym_name, started_at, ended_at, avg_hr, max_hr, " +
                    "active_kcal, metrics_source, updated_at, sync_state) " +
                    "VALUES ('s1', 'u1', NULL, 1000, NULL, NULL, NULL, NULL, NULL, 1000, 'PENDING')",
            )
        }

        val v2 = helper.runMigrationsAndValidate(name, 2, true, GymTrackerDatabase.MIGRATION_1_2)

        v2.query("SELECT id, ended_at FROM sessions").use { rows ->
            assertTrue(rows.moveToFirst(), "the session survived the migration")
            assertEquals("s1", rows.getString(0))
            assertTrue(rows.isNull(1), "and is still active")
        }
        v2.query("SELECT COUNT(*) FROM exercises").use { rows ->
            assertTrue(rows.moveToFirst())
            assertEquals(0, rows.getInt(0))
        }
    }
}
