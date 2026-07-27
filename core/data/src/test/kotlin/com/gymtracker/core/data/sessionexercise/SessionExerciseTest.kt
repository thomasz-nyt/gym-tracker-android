package com.gymtracker.core.data.sessionexercise

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.exercise.CatalogAssetReader
import com.gymtracker.core.data.exercise.CatalogSeeder
import com.gymtracker.core.data.exercise.RoomExerciseCatalog
import com.gymtracker.core.data.session.RoomSessionRepository
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.sessionexercise.AddExerciseToSession
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
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** US-02: appending exercises to a session, and ranking the catalog by recent use. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SessionExerciseTest {
    private lateinit var database: GymTrackerDatabase
    private lateinit var sessionExercises: RoomSessionExerciseRepository
    private lateinit var sessions: RoomSessionRepository
    private lateinit var catalog: RoomExerciseCatalog

    private val json = Json { ignoreUnknownKeys = true }
    private val now: Instant = Instant.parse("2026-07-26T18:00:00Z")
    private val alice = UserId("alice")

    /** Alphabetically bench < curl < squat, so any recency effect is unambiguous. */
    private val bundled =
        """
        [
          {"id":"bench","name":"Bench Press","primaryMuscles":["CHEST"],"secondaryMuscles":[],
           "equipment":"BARBELL","instructions":[],"source":"free-exercise-db"},
          {"id":"curl","name":"Curl","primaryMuscles":["BICEPS"],"secondaryMuscles":[],
           "equipment":"DUMBBELL","instructions":[],"source":"free-exercise-db"},
          {"id":"squat","name":"Squat","primaryMuscles":["QUADS"],"secondaryMuscles":[],
           "equipment":"BARBELL","instructions":[],"source":"free-exercise-db"},
          {"id":"zzz","name":"Zzz Obscure Machine","primaryMuscles":["CHEST"],"secondaryMuscles":[],
           "equipment":"MACHINE","instructions":[],"source":"free-exercise-db",
           "is_starter":true,"image_asset":"Zzz.jpg"}
        ]
        """.trimIndent()

    private fun addExercise() = AddExerciseToSession(sessionExercises) { SessionExerciseId("se-${nextId++}") }

    private var nextId = 1

    @Before
    fun setUp() =
        runTest {
            database =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext(),
                        GymTrackerDatabase::class.java,
                    ).build()
            sessionExercises = RoomSessionExerciseRepository(database.sessionExerciseDao())
            sessions = RoomSessionRepository(database.sessionDao())
            catalog = RoomExerciseCatalog(database.exerciseDao(), json)
            CatalogSeeder(
                dao = database.exerciseDao(),
                assets = CatalogAssetReader { bundled.byteInputStream() },
                json = json,
                io = UnconfinedTestDispatcher(),
            ).seedIfEmpty(now = 1L)
        }

    @After
    fun tearDown() = database.close()

    private suspend fun startSession(
        id: String,
        startedAt: Instant = now,
    ): SessionId {
        val session =
            WorkoutSession(
                id = SessionId(id),
                userId = alice,
                gymName = null,
                startedAt = startedAt,
                endedAt = null,
                metrics = null,
            )
        sessions.startSession(session)
        return session.id
    }

    @Test
    fun `an added exercise is appended to the session`() =
        runTest {
            val session = startSession("s1")

            addExercise()(session, ExerciseId("bench"))

            val added = sessionExercises.observeForSession(session).first().single()
            assertEquals(ExerciseId("bench"), added.exerciseId)
            assertEquals(1, added.position)
        }

    @Test
    fun `exercises keep the order they were added in`() =
        runTest {
            val session = startSession("s1")
            val add = addExercise()

            add(session, ExerciseId("squat"))
            add(session, ExerciseId("bench"))
            add(session, ExerciseId("curl"))

            val order = sessionExercises.observeForSession(session).first()
            assertEquals(listOf("squat", "bench", "curl"), order.map { it.exerciseId.value })
            assertEquals(listOf(1, 2, 3), order.map { it.position })
        }

    @Test
    fun `the same exercise may appear twice in one session`() =
        runTest {
            // US-02, third criterion: someone comes back to a machine later in the workout.
            val session = startSession("s1")
            val add = addExercise()

            val first = add(session, ExerciseId("bench"))
            add(session, ExerciseId("curl"))
            val second = add(session, ExerciseId("bench"))

            val all = sessionExercises.observeForSession(session).first()
            assertEquals(3, all.size)
            assertTrue(first.id != second.id, "each appearance is its own row, so sets stay separate")
            assertEquals(listOf(1, 2, 3), all.map { it.position })
        }

    @Test
    fun `positions do not restart in a second session`() =
        runTest {
            val first = startSession("s1")
            val second = startSession("s2", startedAt = now.plus(Duration.ofDays(1)))

            addExercise()(first, ExerciseId("bench"))
            val inSecond = addExercise()(second, ExerciseId("squat"))

            assertEquals(1, inSecond.position, "position is per session, not global")
        }

    @Test
    fun `discarding a session takes its exercises with it`() =
        runTest {
            val session = startSession("s1")
            addExercise()(session, ExerciseId("bench"))

            sessions.discardSession(session)

            assertEquals(emptyList(), sessionExercises.observeForSession(session).first())
        }

    @Test
    fun `with no history starters come first, then everything else alphabetically`() =
        runTest {
            // ADR-0007: a new member should not meet the catalog in alphabetical order.
            // "Zzz Obscure Machine" is last alphabetically and still leads, because it is a starter.
            assertEquals(
                listOf("Zzz Obscure Machine", "Bench Press", "Curl", "Squat"),
                catalog.search("", alice).first().map { it.name },
            )
        }

    @Test
    fun `a starter carries its bundled image and a non-starter carries none`() =
        runTest {
            val all = catalog.search("", alice).first().associateBy { it.name }

            assertEquals("Zzz.jpg", all.getValue("Zzz Obscure Machine").imageAsset)
            assertEquals(null, all.getValue("Curl").imageAsset)
        }

    @Test
    fun `history still outranks the starter set`() =
        runTest {
            val session = startSession("s1")
            addExercise()(session, ExerciseId("curl"))

            assertEquals(
                listOf("Curl", "Zzz Obscure Machine", "Bench Press", "Squat"),
                catalog.search("", alice).first().map { it.name },
                "what you actually do beats what we guessed you might",
            )
        }

    @Test
    fun `a recently used exercise outranks alphabetical order`() =
        runTest {
            val session = startSession("s1")
            addExercise()(session, ExerciseId("squat"))

            assertEquals(
                listOf("Squat", "Zzz Obscure Machine", "Bench Press", "Curl"),
                catalog.search("", alice).first().map { it.name },
                "Squat is last alphabetically but was just used",
            )
        }

    @Test
    fun `more recent use outranks older use`() =
        runTest {
            val older = startSession("older", startedAt = now.minus(Duration.ofDays(7)))
            val newer = startSession("newer", startedAt = now)
            addExercise()(older, ExerciseId("squat"))
            addExercise()(newer, ExerciseId("curl"))

            assertEquals(
                listOf("Curl", "Squat", "Zzz Obscure Machine", "Bench Press"),
                catalog.search("", alice).first().map { it.name },
            )
        }

    @Test
    fun `recency ranks filtered results too, not just the full list`() =
        runTest {
            // The chosen reading of US-02: recency applies to every result set, including one
            // the member has narrowed with a query.
            val session = startSession("s1")
            addExercise()(session, ExerciseId("squat"))

            assertEquals(listOf("Squat"), catalog.search("qua", alice).first().map { it.name })
            assertEquals(
                listOf("Squat", "Zzz Obscure Machine", "Bench Press", "Curl"),
                catalog.search("", alice).first().map { it.name },
            )
        }

    @Test
    fun `another member's history does not reorder mine`() =
        runTest {
            val bobs =
                WorkoutSession(
                    id = SessionId("bobs"),
                    userId = UserId("bob"),
                    gymName = null,
                    startedAt = now,
                    endedAt = null,
                    metrics = null,
                )
            sessions.startSession(bobs)
            addExercise()(bobs.id, ExerciseId("squat"))

            assertEquals(
                listOf("Zzz Obscure Machine", "Bench Press", "Curl", "Squat"),
                catalog.search("", alice).first().map { it.name },
            )
        }
}

/** The v2 to v3 migration adds `session_exercises` without disturbing what is already there. */
@RunWith(RobolectricTestRunner::class)
class SessionExerciseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            GymTrackerDatabase::class.java,
        )

    @Test
    fun `migrating from 2 to 3 keeps sessions and the seeded catalog`() {
        val name = "migration-2-3.db"

        helper.createDatabase(name, 2).use { v2 ->
            v2.execSQL(
                "INSERT INTO sessions (id, user_id, gym_name, started_at, ended_at, avg_hr, max_hr, " +
                    "active_kcal, metrics_source, updated_at, sync_state) " +
                    "VALUES ('s1', 'u1', NULL, 1000, NULL, NULL, NULL, NULL, NULL, 1000, 'PENDING')",
            )
            v2.execSQL(
                "INSERT INTO exercises (id, name, aliases_json, primary_json, secondary_json, " +
                    "equipment, instructions_json, media_url, media_type, youtube_url, source, updated_at) " +
                    "VALUES ('e1', 'Bench Press', '[]', '[]', '[]', 'BARBELL', '[]', NULL, NULL, NULL, " +
                    "'free-exercise-db', 1000)",
            )
        }

        val v3 = helper.runMigrationsAndValidate(name, 3, true, GymTrackerDatabase.MIGRATION_2_3)

        v3.query("SELECT COUNT(*) FROM sessions").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        v3.query("SELECT COUNT(*) FROM exercises").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0), "the seeded catalog is not wiped by the upgrade")
        }
        v3.query("SELECT COUNT(*) FROM session_exercises").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
    }
}
