package com.gymtracker.core.data.routine

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.exercise.CatalogAssetReader
import com.gymtracker.core.data.exercise.CatalogSeeder
import com.gymtracker.core.data.sync.SyncPayloadCodec
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.RoutineItemId
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** US-29 against the real schema: order, cascade, and the foreign key to the catalog. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoutineTest {
    private lateinit var database: GymTrackerDatabase
    private lateinit var routines: RoomRoutineRepository
    private lateinit var items: RoomRoutineItemRepository

    private val json = Json { ignoreUnknownKeys = true }
    private val alice = UserId("alice")
    private val bench = ExerciseId("bench")
    private val squat = ExerciseId("squat")

    private val bundled =
        """
        [
          {"id":"bench","name":"Bench Press","primaryMuscles":["CHEST"],"secondaryMuscles":[],
           "equipment":"BARBELL","instructions":[],"source":"free-exercise-db"},
          {"id":"squat","name":"Squat","primaryMuscles":["QUADS"],"secondaryMuscles":[],
           "equipment":"BARBELL","instructions":[],"source":"free-exercise-db"}
        ]
        """.trimIndent()

    @Before
    fun setUp() =
        runTest {
            database =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext(),
                        GymTrackerDatabase::class.java,
                    ).build()
            val codec = SyncPayloadCodec(json)
            routines = RoomRoutineRepository(database.routineDao(), database, codec)
            items = RoomRoutineItemRepository(database.routineItemDao(), database, codec)
            CatalogSeeder(
                dao = database.exerciseDao(),
                assets = CatalogAssetReader { bundled.byteInputStream() },
                json = json,
                io = UnconfinedTestDispatcher(),
            ).seedIfEmpty(now = 1L)
        }

    @After
    fun tearDown() = database.close()

    private suspend fun upperA(): RoutineId {
        val routine = Routine(RoutineId("r1"), alice, "Upper A", 1)
        routines.add(routine)
        return routine.id
    }

    private suspend fun item(
        id: String,
        routineId: RoutineId,
        exerciseId: ExerciseId,
        position: Int,
    ) = items.addItem(RoutineItem(RoutineItemId(id), routineId, exerciseId, position))

    @Test
    fun `a routine round-trips`() =
        runTest {
            val id = upperA()

            val found = routines.find(id)

            assertEquals("Upper A", found?.name)
            assertEquals(alice, found?.userId)
            assertEquals(1, found?.position)
        }

    @Test
    fun `items come back in position order, not insertion order`() =
        runTest {
            val id = upperA()
            item("i2", id, squat, 2)
            item("i1", id, bench, 1)

            assertEquals(listOf(bench, squat), items.itemsOf(id).map { it.exerciseId })
        }

    @Test
    fun `deleting a routine cascades to its items`() =
        runTest {
            val id = upperA()
            item("i1", id, bench, 1)
            item("i2", id, squat, 2)

            routines.delete(id)

            assertNull(routines.find(id))
            assertTrue(items.itemsOf(id).isEmpty(), "ON DELETE CASCADE, not orphans")
        }

    @Test
    fun `next positions follow MAX rather than a count`() =
        runTest {
            val id = upperA()
            item("i1", id, bench, 1)
            item("i2", id, squat, 2)
            items.removeItem(RoutineItemId("i1"))

            assertEquals(3, items.nextItemPosition(id), "the gap at 1 is not reused")
        }

    @Test
    fun `positions start at one on an empty routine`() =
        runTest {
            assertEquals(1, items.nextItemPosition(upperA()))
            assertEquals(1, routines.nextRoutinePosition(UserId("nobody")))
        }

    @Test
    fun `a whole new ordering is applied at once`() =
        runTest {
            val id = upperA()
            item("i1", id, bench, 1)
            item("i2", id, squat, 2)

            items.setItemPositions(mapOf(RoutineItemId("i1") to 2, RoutineItemId("i2") to 1))

            assertEquals(listOf(squat, bench), items.itemsOf(id).map { it.exerciseId })
        }

    @Test
    fun `renaming leaves the items alone`() =
        runTest {
            val id = upperA()
            item("i1", id, bench, 1)

            routines.rename(id, "Push A")

            assertEquals("Push A", routines.find(id)?.name)
            assertEquals(listOf(bench), items.itemsOf(id).map { it.exerciseId })
        }

    @Test
    fun `one member's routines are not another's`() =
        runTest {
            upperA()
            routines.add(Routine(RoutineId("r2"), UserId("bob"), "Bob's day", 1))

            assertEquals(listOf("Upper A"), routines.observeRoutines(alice).first().map { it.name })
        }

    @Test
    fun `a target round-trips through Room, and each field is independently nullable`() =
        runTest {
            val id = upperA()
            item("i1", id, bench, 1)
            val target = MovementTarget(sets = 3, reps = 8, weightKg = 47.6)

            items.updateItem(items.itemsOf(id).single().copy(target = target))

            assertEquals(target, items.itemsOf(id).single().target)
        }

    @Test
    fun `a target that names only a rest round-trips as a target, not as absence`() =
        runTest {
            // ADR-0050: "bench, take two minutes" is a plan; the fourth column counts.
            val id = upperA()
            item("i1", id, bench, 1)
            val restOnly = MovementTarget(sets = null, reps = null, weightKg = null, restSeconds = 120)

            items.updateItem(items.itemsOf(id).single().copy(target = restOnly))

            assertEquals(restOnly, items.itemsOf(id).single().target)
        }

    @Test
    fun `a movement with no target round-trips as null, not as a row of zeroes`() =
        runTest {
            val id = upperA()
            item("i1", id, bench, 1)

            assertNull(items.itemsOf(id).single().target, "US-13's absence pattern, not a zero")
        }

    @Test
    fun `clearing a target updates only the movement it was cleared on`() =
        runTest {
            val id = upperA()
            item("i1", id, bench, 1)
            item("i2", id, squat, 2)
            val target = MovementTarget(sets = 3, reps = 8, weightKg = 47.6)
            items.updateItem(items.itemsOf(id).first { it.exerciseId == bench }.copy(target = target))
            items.updateItem(items.itemsOf(id).first { it.exerciseId == squat }.copy(target = target))

            items.updateItem(items.itemsOf(id).first { it.exerciseId == bench }.copy(target = null))

            val byExercise = items.itemsOf(id).associateBy { it.exerciseId }
            assertNull(byExercise.getValue(bench).target)
            assertEquals(target, byExercise.getValue(squat).target)
        }
}

/** The v7 upgrade is additive: nothing already on the device is disturbed by it. */
@RunWith(RobolectricTestRunner::class)
class RoutineMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            GymTrackerDatabase::class.java,
        )

    @Test
    fun `migrating from 6 to 7 adds routines and keeps every existing row`() {
        val name = "migration-6-7.db"

        helper.createDatabase(name, 6).use { v6 ->
            v6.execSQL(
                "INSERT INTO sessions (id, user_id, gym_name, started_at, ended_at, avg_hr, max_hr, " +
                    "active_kcal, metrics_source, updated_at, sync_state) " +
                    "VALUES ('s1', 'u1', NULL, 1000, NULL, NULL, NULL, NULL, NULL, 1000, 'PENDING')",
            )
            // `is_starter` and `image_asset` arrived at v5 (ADR-0007), so a v6 row has them.
            v6.execSQL(
                "INSERT INTO exercises (id, name, aliases_json, primary_json, secondary_json, " +
                    "equipment, instructions_json, media_url, media_type, youtube_url, source, " +
                    "is_starter, image_asset, updated_at) " +
                    "VALUES ('e1', 'Bench Press', '[]', '[]', '[]', 'BARBELL', '[]', NULL, NULL, NULL, " +
                    "'free-exercise-db', 0, NULL, 1000)",
            )
            v6.execSQL(
                "INSERT INTO session_exercises (id, session_id, exercise_id, position, updated_at, sync_state) " +
                    "VALUES ('se1', 's1', 'e1', 1, 1000, 'PENDING')",
            )
            v6.execSQL(
                "INSERT INTO sets (id, session_exercise_id, set_index, weight_kg, reps, rpe, performed_at, " +
                    "updated_at, sync_state) VALUES ('set1', 'se1', 1, 60.0, 8, NULL, 1000, 1000, 'PENDING')",
            )
        }

        val v7 = helper.runMigrationsAndValidate(name, 7, true, GymTrackerDatabase.MIGRATION_6_7)

        listOf("sessions", "exercises", "session_exercises", "sets").forEach { table ->
            v7.query("SELECT COUNT(*) FROM $table").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0), "$table lost a row to an additive migration")
            }
        }
        v7.query("SELECT COUNT(*) FROM routines").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0), "a device upgrading has no routines yet")
        }
        v7.query("SELECT COUNT(*) FROM routine_items").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
    }

    /**
     * US-30 (ADR-0027): the v8 upgrade adds three nullable target columns to `routine_items`
     * and `session_exercises`, and touches nothing else — `sessions` and `sets` are explicitly
     * out of scope for this migration.
     */
    @Test
    fun `migrating from 7 to 8 adds target columns and keeps every existing row`() {
        val name = "migration-7-8.db"

        helper.createDatabase(name, 7).use { v7 ->
            v7.execSQL(
                "INSERT INTO sessions (id, user_id, gym_name, started_at, ended_at, avg_hr, max_hr, " +
                    "active_kcal, metrics_source, updated_at, sync_state) " +
                    "VALUES ('s1', 'u1', NULL, 1000, NULL, NULL, NULL, NULL, NULL, 1000, 'PENDING')",
            )
            v7.execSQL(
                "INSERT INTO exercises (id, name, aliases_json, primary_json, secondary_json, " +
                    "equipment, instructions_json, media_url, media_type, youtube_url, source, " +
                    "is_starter, image_asset, updated_at) " +
                    "VALUES ('e1', 'Bench Press', '[]', '[]', '[]', 'BARBELL', '[]', NULL, NULL, NULL, " +
                    "'free-exercise-db', 0, NULL, 1000)",
            )
            v7.execSQL(
                "INSERT INTO session_exercises (id, session_id, exercise_id, position, updated_at, sync_state) " +
                    "VALUES ('se1', 's1', 'e1', 1, 1000, 'PENDING')",
            )
            v7.execSQL(
                "INSERT INTO routines (id, user_id, name, position, created_at, updated_at, sync_state) " +
                    "VALUES ('r1', 'u1', 'Upper A', 1, 1000, 1000, 'PENDING')",
            )
            v7.execSQL(
                "INSERT INTO routine_items (id, routine_id, exercise_id, position, updated_at, sync_state) " +
                    "VALUES ('ri1', 'r1', 'e1', 1, 1000, 'PENDING')",
            )
        }

        val v8 = helper.runMigrationsAndValidate(name, 8, true, GymTrackerDatabase.MIGRATION_7_8)

        listOf("sessions", "exercises", "session_exercises", "routines", "routine_items").forEach { table ->
            v8.query("SELECT COUNT(*) FROM $table").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0), "$table lost a row to an additive migration")
            }
        }
        v8.query("SELECT target_sets, target_reps, target_weight_kg FROM routine_items WHERE id = 'ri1'").use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(0), "an upgraded row has no target until one is set")
            assertTrue(it.isNull(1))
            assertTrue(it.isNull(2))
        }
        v8
            .query(
                "SELECT target_sets, target_reps, target_weight_kg FROM session_exercises WHERE id = 'se1'",
            ).use {
                assertTrue(it.moveToFirst())
                assertTrue(it.isNull(0), "a session predating this migration has no target either")
                assertTrue(it.isNull(1))
                assertTrue(it.isNull(2))
            }
    }
}
