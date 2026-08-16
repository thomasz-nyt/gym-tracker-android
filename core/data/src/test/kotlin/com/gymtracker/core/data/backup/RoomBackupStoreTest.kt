package com.gymtracker.core.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.exercise.ExerciseEntity
import com.gymtracker.core.data.member.DataStoreUnitPreference
import com.gymtracker.core.data.rest.DataStoreRestTimerStore
import com.gymtracker.core.data.routine.RoutineEntity
import com.gymtracker.core.data.routine.RoutineItemEntity
import com.gymtracker.core.data.session.SYNC_STATE_PENDING
import com.gymtracker.core.data.session.SessionEntity
import com.gymtracker.core.data.sessionexercise.SessionExerciseEntity
import com.gymtracker.core.data.set.SetEntity
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.test.runTest
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
import kotlin.test.assertTrue

/**
 * US-40 against a real Room database and a real DataStore, per `specs/testing-strategy.md`
 * ("Repository + sync | Fake remote, real Room").
 *
 * `replaceAll` has no test here yet — it does not exist until US-41 (PR2).
 */
@RunWith(RobolectricTestRunner::class)
class RoomBackupStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var database: GymTrackerDatabase
    private lateinit var store: RoomBackupStore

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
        store =
            RoomBackupStore(
                database = database,
                unitPreference = DataStoreUnitPreference(preferences("unit.preferences_pb")),
                restTimerStore = DataStoreRestTimerStore(preferences("rest.preferences_pb")),
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
                RoomBackupStore(database = database, unitPreference = unit, restTimerStore = rest)

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
}
