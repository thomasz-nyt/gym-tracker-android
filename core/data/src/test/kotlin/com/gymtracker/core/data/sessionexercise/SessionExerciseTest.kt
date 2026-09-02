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
import com.gymtracker.core.data.set.RoomSetRepository
import com.gymtracker.core.data.sync.SyncPayloadCodec
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.SessionExercise
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
    private lateinit var sets: RoomSetRepository
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
            val codec = SyncPayloadCodec(json)
            sessionExercises = RoomSessionExerciseRepository(database.sessionExerciseDao(), database, codec)
            sessions = RoomSessionRepository(database.sessionDao(), database, codec)
            sets = RoomSetRepository(database.setDao(), database, codec)
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

            sessions.deleteSession(session)

            assertEquals(emptyList(), sessionExercises.observeForSession(session).first())
        }

    @Test
    fun `removing an exercise takes the sets logged against it`() =
        runTest {
            // US-02c. The cascade is declared on `sets`, so this asserts the schema does it
            // rather than the domain remembering to.
            val session = startSession("s1")
            val add = addExercise()
            val bench = add(session, ExerciseId("bench"))
            val squat = add(session, ExerciseId("squat"))
            sets.add(ExerciseSet("a", bench.id, 1, 60.0, 10, null, now))
            sets.add(ExerciseSet("b", squat.id, 1, 80.0, 5, null, now))

            sessionExercises.remove(bench.id)

            assertEquals(listOf(squat.id), sessionExercises.observeForSession(session).first().map { it.id })
            assertEquals(emptyList(), sets.observeForSessionExercise(bench.id).first())
            assertEquals(listOf("b"), sets.observeForSessionExercise(squat.id).first().map { it.id })
        }

    @Test
    fun `a removed exercise leaves a gap that the next position does not reuse`() =
        runTest {
            // MAX(position) + 1. A count would mint 3 here, colliding with the surviving row.
            val session = startSession("s1")
            val add = addExercise()
            add(session, ExerciseId("bench"))
            val squat = add(session, ExerciseId("squat"))
            add(session, ExerciseId("curl"))

            sessionExercises.remove(squat.id)

            assertEquals(listOf(1, 3), sessionExercises.observeForSession(session).first().map { it.position })
            assertEquals(4, sessionExercises.nextPosition(session))
        }

    @Test
    fun `finding an exercise that is not there returns null`() =
        runTest {
            assertEquals(null, sessionExercises.find(SessionExerciseId("never-existed")))
        }

    @Test
    fun `a copied target round-trips through Room`() =
        runTest {
            // US-30 (ADR-0027): StartSessionFromRoutine writes the target when it copies the
            // movement in; this asserts Room actually keeps what was written.
            val session = startSession("s1")
            val target = MovementTarget(sets = 3, reps = 8, weightKg = 47.6)

            sessionExercises.add(
                SessionExercise(
                    id = SessionExerciseId("se1"),
                    sessionId = session,
                    exerciseId = ExerciseId("bench"),
                    position = 1,
                    target = target,
                ),
            )

            assertEquals(
                target,
                sessionExercises
                    .observeForSession(session)
                    .first()
                    .single()
                    .target,
            )
        }

    @Test
    fun `an exercise added without a target round-trips as null`() =
        runTest {
            val session = startSession("s1")

            addExercise()(session, ExerciseId("bench"))

            assertEquals(
                null,
                sessionExercises
                    .observeForSession(session)
                    .first()
                    .single()
                    .target,
            )
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
