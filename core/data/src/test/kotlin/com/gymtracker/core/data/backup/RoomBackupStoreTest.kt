package com.gymtracker.core.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.exercise.ExerciseEntity
import com.gymtracker.core.data.member.DataStoreCurrentMember
import com.gymtracker.core.data.member.DataStoreUnitPreference
import com.gymtracker.core.data.rest.DataStoreRestTimerStore
import com.gymtracker.core.data.routine.RoutineEntity
import com.gymtracker.core.data.routine.RoutineItemEntity
import com.gymtracker.core.data.session.SYNC_STATE_PENDING
import com.gymtracker.core.data.session.SessionEntity
import com.gymtracker.core.data.sessionexercise.SessionExerciseEntity
import com.gymtracker.core.data.set.SetEntity
import com.gymtracker.core.data.sync.SyncPayloadCodec
import com.gymtracker.core.domain.TestData
import com.gymtracker.core.domain.backup.BackupContents
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * US-40 and US-41 against a real Room database and a real DataStore, per
 * `specs/testing-strategy.md` ("Repository + sync | Fake remote, real Room").
 */
@RunWith(RobolectricTestRunner::class)
class RoomBackupStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var database: GymTrackerDatabase
    private lateinit var store: RoomBackupStore
    private lateinit var currentMember: DataStoreCurrentMember

    private val alice = UserId("alice")
    private val bob = UserId("bob")
    private val now: Instant = Instant.parse("2026-08-15T18:00:00Z")

    private fun preferences(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { folder.newFile(name) }

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GymTrackerDatabase::class.java)
                .build()
        currentMember = DataStoreCurrentMember(preferences("member.preferences_pb"))
        store =
            RoomBackupStore(
                database = database,
                unitPreference = DataStoreUnitPreference(preferences("unit.preferences_pb")),
                restTimerStore = DataStoreRestTimerStore(preferences("rest.preferences_pb")),
                currentMember = currentMember,
                codec = SyncPayloadCodec(Json { ignoreUnknownKeys = true }),
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** `routine_items` has a real FK to `exercises` (`RoutineEntity.kt`); `session_exercises` has none. */
    private fun seedExercise(id: String = "Barbell_Bench_Press_Medium_Grip") =
        ExerciseEntity(
            id = id,
            name = "Barbell Bench Press",
            aliasesJson = "[]",
            primaryJson = "[]",
            secondaryJson = "[]",
            equipment = "BARBELL",
            instructionsJson = "[]",
            mediaUrl = null,
            mediaType = null,
            youtubeUrl = null,
            source = "free-exercise-db",
            isStarter = false,
            imageAsset = null,
            updatedAt = now.toEpochMilli(),
        )

    private fun session(
        id: String,
        userId: UserId = alice,
        endedAt: Instant? = now.plus(Duration.ofMinutes(55)),
    ) = SessionEntity(
        id = id,
        userId = userId.value,
        gymName = null,
        startedAt = now.toEpochMilli(),
        endedAt = endedAt?.toEpochMilli(),
        avgHr = null,
        maxHr = null,
        activeKcal = null,
        metricsSource = null,
        updatedAt = now.toEpochMilli(),
        syncState = SYNC_STATE_PENDING,
    )

    private fun sessionExercise(
        id: String,
        sessionId: String,
        exerciseId: String = "Barbell_Bench_Press_Medium_Grip",
    ) = SessionExerciseEntity(
        id = id,
        sessionId = sessionId,
        exerciseId = exerciseId,
        position = 1,
        updatedAt = now.toEpochMilli(),
        syncState = SYNC_STATE_PENDING,
    )

    private fun set(
        id: String,
        sessionExerciseId: String,
    ) = SetEntity(
        id = id,
        sessionExerciseId = sessionExerciseId,
        setIndex = 1,
        weightKg = 60.0,
        reps = 5,
        rpe = null,
        performedAt = now.toEpochMilli(),
        updatedAt = now.toEpochMilli(),
        syncState = SYNC_STATE_PENDING,
    )

    private fun routine(
        id: String,
        userId: UserId = alice,
    ) = RoutineEntity(
        id = id,
        userId = userId.value,
        name = "Upper A",
        position = 1,
        createdAt = now.toEpochMilli(),
        updatedAt = now.toEpochMilli(),
        syncState = SYNC_STATE_PENDING,
    )

    private fun routineItem(
        id: String,
        routineId: String,
        exerciseId: String = "Barbell_Bench_Press_Medium_Grip",
    ) = RoutineItemEntity(
        id = id,
        routineId = routineId,
        exerciseId = exerciseId,
        position = 1,
        updatedAt = now.toEpochMilli(),
        syncState = SYNC_STATE_PENDING,
        targetSets = 3,
        targetReps = 8,
        targetWeightKg = 61.25,
    )

    @Test
    fun `reads back every row across all five tables, and only this member's`() =
        runTest {
            database.exerciseDao().insertAll(listOf(seedExercise()))
            database.sessionDao().insert(session("s1"))
            database.sessionExerciseDao().insert(sessionExercise("se1", sessionId = "s1"))
            database.setDao().insert(set("set1", sessionExerciseId = "se1"))
            database.routineDao().insert(routine("r1"))
            database.routineItemDao().insert(routineItem("ri1", routineId = "r1"))
            // A second member's rows, to prove the read is scoped.
            database.sessionDao().insert(session("bobs-session", userId = bob, endedAt = null))

            val contents = store.read(alice)

            assertEquals(1, contents.sessions.size, "only alice's session")
            assertEquals(
                "s1",
                contents.sessions
                    .single()
                    .id.value,
            )
            assertEquals(1, contents.sessionExercises.size)
            assertEquals(1, contents.sets.size)
            assertEquals(60.0, contents.sets.single().weightKg)
            assertEquals(1, contents.routines.size)
            assertEquals("Upper A", contents.routines.single().name)
            assertEquals(1, contents.routineItems.size)
            assertEquals(
                8,
                contents.routineItems
                    .single()
                    .target
                    ?.reps,
            )
        }

    @Test
    fun `a session in progress is included, unlike observeFinishedSessions`() =
        runTest {
            database.sessionDao().insert(session("active", endedAt = null))

            val contents = store.read(alice)

            assertTrue(contents.sessions.any { it.id.value == "active" }, "a backup is everything, not just history")
        }

    @Test
    fun `carries the member's unit and rest-default preferences`() =
        runTest {
            // Different filenames from setUp()'s — TemporaryFolder#newFile throws if a file
            // with the same name already exists in the folder.
            val unit = DataStoreUnitPreference(preferences("unit2.preferences_pb"))
            unit.set(WeightUnit.KG)
            val rest = DataStoreRestTimerStore(preferences("rest2.preferences_pb"))
            rest.setDefaultRest(Duration.ofSeconds(90))
            val storeWithPreferences =
                RoomBackupStore(
                    database = database,
                    unitPreference = unit,
                    restTimerStore = rest,
                    currentMember = DataStoreCurrentMember(preferences("member2.preferences_pb")),
                    codec = SyncPayloadCodec(Json { ignoreUnknownKeys = true }),
                )

            val contents = storeWithPreferences.read(alice)

            assertEquals(WeightUnit.KG, contents.unit)
            assertEquals(Duration.ofSeconds(90), contents.restDefault)
        }

    @Test
    fun `an empty member exports empty lists, not an error`() =
        runTest {
            val contents = store.read(alice)

            assertEquals(0, contents.sessions.size)
            assertEquals(0, contents.sessionExercises.size)
            assertEquals(0, contents.sets.size)
            assertEquals(0, contents.routines.size)
            assertEquals(0, contents.routineItems.size)
        }

    /** ADR-0034's definitive test: seed -> export -> wipe -> import -> read back identical. */
    @Test
    fun `a round trip through replaceAll is an identity function, member id included`() =
        runTest {
            database.exerciseDao().insertAll(listOf(seedExercise(TestData.BENCH.value)))
            val fixture = TestData.memberWithARoutineAndASession(alice)
            val contents =
                BackupContents(
                    memberId = alice,
                    unit = WeightUnit.KG,
                    restDefault = Duration.ofSeconds(90),
                    sessions = fixture.sessions,
                    sessionExercises = fixture.sessionExercises,
                    sets = fixture.sets,
                    routines = fixture.routines,
                    routineItems = fixture.routineItems,
                )

            store.replaceAll(contents)
            val readBack = store.read(alice)

            assertEquals(contents, readBack)
            assertEquals(alice, currentMember.id(), "the member id itself is restored, not just the rows")
        }

    @Test
    fun `replaceAll wipes what was there before, not just adds to it`() =
        runTest {
            database.exerciseDao().insertAll(listOf(seedExercise()))
            database.sessionDao().insert(session("old-session"))
            val empty =
                BackupContents(
                    memberId = alice,
                    unit = WeightUnit.LB,
                    restDefault = Duration.ofSeconds(60),
                    sessions = emptyList(),
                    sessionExercises = emptyList(),
                    sets = emptyList(),
                    routines = emptyList(),
                    routineItems = emptyList(),
                )

            store.replaceAll(empty)

            assertEquals(0, store.read(alice).sessions.size, "the old session must not survive an empty import")
        }

    @Test
    fun `deleting a member's sessions and routines cascades their children`() =
        runTest {
            database.exerciseDao().insertAll(listOf(seedExercise()))
            database.sessionDao().insert(session("s1"))
            database.sessionExerciseDao().insert(sessionExercise("se1", sessionId = "s1"))
            database.setDao().insert(set("set1", sessionExerciseId = "se1"))
            database.routineDao().insert(routine("r1"))
            database.routineItemDao().insert(routineItem("ri1", routineId = "r1"))
            val empty =
                BackupContents(
                    memberId = alice,
                    unit = WeightUnit.LB,
                    restDefault = Duration.ofSeconds(60),
                    sessions = emptyList(),
                    sessionExercises = emptyList(),
                    sets = emptyList(),
                    routines = emptyList(),
                    routineItems = emptyList(),
                )

            store.replaceAll(empty)

            val contents = store.read(alice)
            assertEquals(0, contents.sessionExercises.size, "ON DELETE CASCADE from sessions")
            assertEquals(0, contents.sets.size, "ON DELETE CASCADE from session_exercises")
            assertEquals(0, contents.routineItems.size, "ON DELETE CASCADE from routines")
        }

    @Test
    fun `a failure partway through leaves the previous data intact`() =
        runTest {
            database.exerciseDao().insertAll(listOf(seedExercise(TestData.BENCH.value)))
            val original = TestData.memberWithARoutineAndASession(alice)
            val goodContents =
                BackupContents(
                    memberId = alice,
                    unit = WeightUnit.LB,
                    restDefault = Duration.ofSeconds(60),
                    sessions = original.sessions,
                    sessionExercises = original.sessionExercises,
                    sets = original.sets,
                    routines = original.routines,
                    routineItems = original.routineItems,
                )
            store.replaceAll(goodContents)

            // session_exercises referencing a session that isn't in this same payload — the FK
            // on session_id fails partway through the insert, after sessions (and the delete)
            // have already run in the same transaction.
            val brokenContents =
                goodContents.copy(
                    sessions = emptyList(),
                    sessionExercises =
                        listOf(
                            SessionExercise(
                                id = SessionExerciseId("orphan"),
                                sessionId = SessionId("no-such-session"),
                                exerciseId = ExerciseId(TestData.BENCH.value),
                                position = 1,
                                target = null,
                            ),
                        ),
                )

            assertFailsWith<Exception> { store.replaceAll(brokenContents) }
            assertEquals(goodContents, store.read(alice), "the failed transaction must not have touched anything")
        }
}
