package com.gymtracker.core.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.core.data.backup.RoomBackupStore
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.exercise.ExerciseEntity
import com.gymtracker.core.data.member.DataStoreCurrentMember
import com.gymtracker.core.data.member.DataStoreUnitPreference
import com.gymtracker.core.data.rest.DataStoreRestTimerStore
import com.gymtracker.core.data.routine.RoomRoutineItemRepository
import com.gymtracker.core.data.routine.RoomRoutineRepository
import com.gymtracker.core.data.session.RoomSessionRepository
import com.gymtracker.core.data.sessionexercise.RoomSessionExerciseRepository
import com.gymtracker.core.data.set.RoomSetRepository
import com.gymtracker.core.domain.TestData
import com.gymtracker.core.domain.backup.BackupContents
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.SessionMetrics
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
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
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * US-57 against real Room, per `specs/testing-strategy.md` ("Repository + sync | Fake remote,
 * real Room, assert queue behaviour"). No `SyncWorker` and no network exist yet — this proves
 * only that the outbox itself is correct: every syncable write leaves exactly the right row.
 */
@RunWith(RobolectricTestRunner::class)
class OutboxEnqueueTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var database: GymTrackerDatabase
    private lateinit var sessions: RoomSessionRepository
    private lateinit var sessionExercises: RoomSessionExerciseRepository
    private lateinit var sets: RoomSetRepository
    private lateinit var routines: RoomRoutineRepository
    private lateinit var routineItems: RoomRoutineItemRepository
    private lateinit var backupStore: RoomBackupStore

    private val json = Json { ignoreUnknownKeys = true }
    private val now: Instant = Instant.parse("2026-09-01T18:00:00Z")
    private val alice = UserId("alice")
    private val bench = ExerciseId(TestData.BENCH.value)

    @Before
    fun setUp() =
        runTest {
            database =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext(),
                        GymTrackerDatabase::class.java,
                    ).build()
            // routine_items has a real FK to exercises (RoutineEntity.kt); session_exercises has
            // none. Seeded once here rather than per-test, matching RoomBackupStoreTest's own
            // comment on the same wrinkle.
            database.exerciseDao().insertAll(listOf(seedExercise()))
            val codec = SyncPayloadCodec(json)
            sessions = RoomSessionRepository(database.sessionDao(), database, codec)
            sessionExercises = RoomSessionExerciseRepository(database.sessionExerciseDao(), database, codec)
            sets = RoomSetRepository(database.setDao(), database, codec)
            routines = RoomRoutineRepository(database.routineDao(), database, codec)
            routineItems = RoomRoutineItemRepository(database.routineItemDao(), database, codec)
            backupStore =
                RoomBackupStore(
                    database = database,
                    unitPreference = DataStoreUnitPreference(preferences("unit.preferences_pb")),
                    restTimerStore = DataStoreRestTimerStore(preferences("rest.preferences_pb")),
                    currentMember = DataStoreCurrentMember(preferences("member.preferences_pb")),
                    codec = codec,
                )
        }

    @After
    fun tearDown() = database.close()

    private fun preferences(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { folder.newFile(name) }

    private suspend fun queueRows() = database.syncQueueDao().oldestFirst()

    private fun session(id: String) =
        WorkoutSession(SessionId(id), alice, gymName = null, startedAt = now, endedAt = null, metrics = null)

    // --- sessions ---

    @Test
    fun `starting a session enqueues one write row naming it`() =
        runTest {
            sessions.startSession(session("s1"))

            val row = queueRows().single()
            assertEquals(SyncEntityNames.SESSIONS, row.entity)
            assertEquals("s1", row.entityId)
            assertEquals(SYNC_OP_WRITE, row.op)
            assertTrue(row.payloadJson!!.contains("\"id\":\"s1\""))
        }

    @Test
    fun `ending a session enqueues a write row carrying the full row, not just the two changed columns`() =
        runTest {
            sessions.startSession(session("s1"))

            sessions.endSession(SessionId("s1"), now.plusSeconds(3600))

            val row = queueRows().last()
            assertEquals("s1", row.entityId)
            assertTrue(row.payloadJson!!.contains("\"endedAt\":${now.plusSeconds(3600).toEpochMilli()}"))
            assertTrue(row.payloadJson.contains("\"userId\":\"alice\""), "the full row, not a two-column patch")
        }

    @Test
    fun `saving metrics enqueues a fresh write row for that session`() =
        runTest {
            sessions.startSession(session("s1"))

            sessions.saveMetrics(SessionId("s1"), SessionMetrics(120, 160, 300, "health_connect"))

            val row = queueRows().last()
            assertEquals("s1", row.entityId)
            assertTrue(row.payloadJson!!.contains("\"avgHr\":120"))
        }

    @Test
    fun `clearing metrics enqueues one row per session that actually had metrics, none for one that didn't`() =
        runTest {
            sessions.startSession(session("carrying"))
            sessions.saveMetrics(SessionId("carrying"), SessionMetrics(120, 160, 300, "health_connect"))
            sessions.startSession(session("untouched"))
            val before = queueRows().size

            sessions.clearMetrics(alice)

            val after = queueRows()
            assertEquals(before + 1, after.size, "exactly one new row, for the session that had metrics")
            assertEquals("carrying", after.last().entityId)
        }

    @Test
    fun `deleting a session enqueues a delete row with no payload`() =
        runTest {
            sessions.startSession(session("s1"))

            sessions.deleteSession(SessionId("s1"))

            val row = queueRows().last()
            assertEquals(SyncEntityNames.SESSIONS, row.entity)
            assertEquals("s1", row.entityId)
            assertEquals(SYNC_OP_DELETE, row.op)
            assertNull(row.payloadJson)
        }

    @Test
    fun `deleting a session that does not exist enqueues nothing`() =
        runTest {
            sessions.deleteSession(SessionId("never-existed"))

            assertEquals(0, queueRows().size)
        }

    @Test
    fun `deleting a session cascades its exercises and sets, but enqueues only the session's own delete`() =
        runTest {
            sessions.startSession(session("s1"))
            val se = SessionExerciseId("se1")
            sessionExercises.add(SessionExercise(se, SessionId("s1"), bench, position = 1))
            sets.add(ExerciseSet("set1", se, setIndex = 1, weightKg = 60.0, reps = 5, rpe = null, performedAt = now))
            val beforeDelete = queueRows().size // 1 session write + 1 se write + 1 set write = 3

            sessions.deleteSession(SessionId("s1"))

            assertEquals(beforeDelete + 1, queueRows().size, "cascade is silent — no queue row for either child")
            assertTrue(
                sessionExercises.find(se) == null,
                "the cascade itself must have actually happened",
            )
        }

    // --- session_exercises ---

    @Test
    fun `adding a session exercise enqueues a write row`() =
        runTest {
            sessions.startSession(session("s1"))

            sessionExercises.add(SessionExercise(SessionExerciseId("se1"), SessionId("s1"), bench, position = 1))

            val row = queueRows().last()
            assertEquals(SyncEntityNames.SESSION_EXERCISES, row.entity)
            assertEquals("se1", row.entityId)
            assertEquals(SYNC_OP_WRITE, row.op)
        }

    @Test
    fun `removing a session exercise enqueues a delete row`() =
        runTest {
            sessions.startSession(session("s1"))
            sessionExercises.add(SessionExercise(SessionExerciseId("se1"), SessionId("s1"), bench, position = 1))

            sessionExercises.remove(SessionExerciseId("se1"))

            val row = queueRows().last()
            assertEquals(SYNC_OP_DELETE, row.op)
            assertEquals("se1", row.entityId)
        }

    // --- sets ---

    @Test
    fun `logging a set enqueues a write row`() =
        runTest {
            sessions.startSession(session("s1"))
            val se = SessionExerciseId("se1")
            sessionExercises.add(SessionExercise(se, SessionId("s1"), bench, position = 1))

            sets.add(ExerciseSet("set1", se, setIndex = 1, weightKg = 60.0, reps = 5, rpe = null, performedAt = now))

            val row = queueRows().last()
            assertEquals(SyncEntityNames.SETS, row.entity)
            assertEquals("set1", row.entityId)
            assertTrue(row.payloadJson!!.contains("\"reps\":5"))
        }

    @Test
    fun `correcting a set enqueues a fresh write row`() =
        runTest {
            sessions.startSession(session("s1"))
            val se = SessionExerciseId("se1")
            sessionExercises.add(SessionExercise(se, SessionId("s1"), bench, position = 1))
            val logged = ExerciseSet("set1", se, setIndex = 1, weightKg = 60.0, reps = 5, rpe = null, performedAt = now)
            sets.add(logged)

            sets.update(logged.copy(weightKg = 65.0, reps = 6))

            val row = queueRows().last()
            assertEquals(SYNC_OP_WRITE, row.op)
            assertTrue(row.payloadJson!!.contains("\"weightKg\":65.0"))
        }

    @Test
    fun `deleting a set enqueues a delete row`() =
        runTest {
            sessions.startSession(session("s1"))
            val se = SessionExerciseId("se1")
            sessionExercises.add(SessionExercise(se, SessionId("s1"), bench, position = 1))
            sets.add(ExerciseSet("set1", se, setIndex = 1, weightKg = 60.0, reps = 5, rpe = null, performedAt = now))

            sets.delete("set1")

            val row = queueRows().last()
            assertEquals(SYNC_OP_DELETE, row.op)
            assertEquals("set1", row.entityId)
        }

    @Test
    fun `deleting a set that does not exist enqueues nothing`() =
        runTest {
            assertNull(sets.delete("never-existed"))

            assertEquals(0, queueRows().size)
        }

    // --- routines and routine items ---

    @Test
    fun `adding a routine enqueues a write row`() =
        runTest {
            routines.add(Routine(RoutineId("r1"), alice, "Upper A", position = 1))

            val row = queueRows().single()
            assertEquals(SyncEntityNames.ROUTINES, row.entity)
            assertEquals("r1", row.entityId)
        }

    @Test
    fun `renaming a routine enqueues a write row and, separately from US-57, now marks the row pending`() =
        runTest {
            // ADR-0043's amendment: rename previously bumped updated_at without ever setting
            // sync_state — a pre-existing bug this story's own outbox surfaced and fixed.
            routines.add(Routine(RoutineId("r1"), alice, "Upper A", position = 1))

            routines.rename(RoutineId("r1"), "Push A")

            val row = queueRows().last()
            assertEquals(SYNC_OP_WRITE, row.op)
            assertTrue(row.payloadJson!!.contains("\"name\":\"Push A\""))
            assertEquals("PENDING", database.routineDao().find("r1")?.syncState)
        }

    @Test
    fun `deleting a routine enqueues a delete row, and cascades its items without enqueuing them`() =
        runTest {
            routines.add(Routine(RoutineId("r1"), alice, "Upper A", position = 1))
            routineItems.addItem(RoutineItem(RoutineItemId("ri1"), RoutineId("r1"), bench, position = 1, target = null))
            val beforeDelete = queueRows().size

            routines.delete(RoutineId("r1"))

            assertEquals(
                beforeDelete + 1,
                queueRows().size,
                "the item's own delete is cascade-silent, like a session's",
            )
        }

    @Test
    fun `adding a routine item enqueues a write row`() =
        runTest {
            routines.add(Routine(RoutineId("r1"), alice, "Upper A", position = 1))

            routineItems.addItem(
                RoutineItem(RoutineItemId("ri1"), RoutineId("r1"), bench, position = 1, target = null),
            )

            val row = queueRows().last()
            assertEquals(SyncEntityNames.ROUTINE_ITEMS, row.entity)
            assertEquals("ri1", row.entityId)
        }

    @Test
    fun `updating a routine item's target enqueues a fresh write row`() =
        runTest {
            routines.add(Routine(RoutineId("r1"), alice, "Upper A", position = 1))
            val item = RoutineItem(RoutineItemId("ri1"), RoutineId("r1"), bench, position = 1, target = null)
            routineItems.addItem(item)

            routineItems.updateItem(item.copy(target = MovementTarget(sets = 3, reps = 8, weightKg = 61.25)))

            val row = queueRows().last()
            assertEquals(SYNC_OP_WRITE, row.op)
            assertTrue(row.payloadJson!!.contains("\"targetReps\":8"))
        }

    @Test
    fun `removing a routine item enqueues a delete row`() =
        runTest {
            routines.add(Routine(RoutineId("r1"), alice, "Upper A", position = 1))
            routineItems.addItem(RoutineItem(RoutineItemId("ri1"), RoutineId("r1"), bench, position = 1, target = null))

            routineItems.removeItem(RoutineItemId("ri1"))

            val row = queueRows().last()
            assertEquals(SYNC_OP_DELETE, row.op)
            assertEquals("ri1", row.entityId)
        }

    @Test
    fun `reordering routine items enqueues one write row per item moved, and marks each pending`() =
        runTest {
            routines.add(Routine(RoutineId("r1"), alice, "Upper A", position = 1))
            routineItems.addItem(RoutineItem(RoutineItemId("ri1"), RoutineId("r1"), bench, position = 1, target = null))
            routineItems.addItem(RoutineItem(RoutineItemId("ri2"), RoutineId("r1"), bench, position = 2, target = null))
            val before = queueRows().size

            routineItems.setItemPositions(mapOf(RoutineItemId("ri1") to 2, RoutineItemId("ri2") to 1))

            assertEquals(before + 2, queueRows().size, "one row per item the drag actually touched")
            assertEquals("PENDING", database.routineItemDao().find("ri1")?.syncState)
        }

    // --- atomicity ---

    @Test
    fun `a write that fails its own transaction leaves neither the row nor a queue entry`() =
        runTest {
            // No session "does-not-exist" — the foreign key on session_id fails the insert.
            assertFails {
                sessionExercises.add(
                    SessionExercise(SessionExerciseId("orphan"), SessionId("does-not-exist"), bench, position = 1),
                )
            }

            assertNull(sessionExercises.find(SessionExerciseId("orphan")), "the write must not have committed")
            assertEquals(0, queueRows().size, "nor must the queue row, in the same transaction")
        }

    // --- restore (US-41) ---

    @Test
    fun `a restore enqueues every row it writes, exactly like an ordinary write`() =
        runTest {
            // Already seeded in setUp() — the fixture's routine item points at the same bench id.
            val fixture = TestData.memberWithARoutineAndASession(alice)

            backupStore.replaceAll(
                BackupContents(
                    memberId = alice,
                    unit = WeightUnit.KG,
                    restDefault = Duration.ofSeconds(90),
                    sessions = fixture.sessions,
                    sessionExercises = fixture.sessionExercises,
                    sets = fixture.sets,
                    routines = fixture.routines,
                    routineItems = fixture.routineItems,
                ),
            )

            val rows = queueRows()
            assertEquals(5, rows.size, "one write row for each of the fixture's five restored rows")
            assertTrue(rows.all { it.op == SYNC_OP_WRITE })
            val entities = rows.map { it.entity }.toSet()
            assertEquals(
                setOf(
                    SyncEntityNames.SESSIONS,
                    SyncEntityNames.SESSION_EXERCISES,
                    SyncEntityNames.SETS,
                    SyncEntityNames.ROUTINES,
                    SyncEntityNames.ROUTINE_ITEMS,
                ),
                entities,
            )
        }

    @Test
    fun `replaceAll wiping a member's prior rows does not itself enqueue any delete`() =
        runTest {
            // The deliberately-unresolved question this story leaves open (see the M2 roadmap
            // entry and ADR-0043's amendment): deleteAllForUser stays exactly what it was.
            sessions.startSession(session("old"))
            val beforeWipe = queueRows().size

            backupStore.replaceAll(
                BackupContents(
                    memberId = alice,
                    unit = WeightUnit.LB,
                    restDefault = Duration.ofSeconds(60),
                    sessions = emptyList(),
                    sessionExercises = emptyList(),
                    sets = emptyList(),
                    routines = emptyList(),
                    routineItems = emptyList(),
                ),
            )

            assertEquals(beforeWipe, queueRows().size, "wiping stale local rows enqueues nothing on its own")
        }

    private fun seedExercise() =
        ExerciseEntity(
            id = TestData.BENCH.value,
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
}
