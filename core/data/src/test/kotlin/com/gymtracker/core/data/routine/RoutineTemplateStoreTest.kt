package com.gymtracker.core.data.routine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.exercise.CatalogAssetReader
import com.gymtracker.core.data.exercise.CatalogSeeder
import com.gymtracker.core.data.session.RoomSessionRepository
import com.gymtracker.core.data.sessionexercise.RoomSessionExerciseRepository
import com.gymtracker.core.data.set.RoomSetRepository
import com.gymtracker.core.data.sync.SyncPayloadCodec
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.RoutineOrigin
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.model.WorkoutWorkflow
import com.gymtracker.core.domain.routine.RoutineTemplateWrite
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoutineTemplateStoreTest {
    private lateinit var db: GymTrackerDatabase
    private lateinit var routines: RoomRoutineRepository
    private lateinit var items: RoomRoutineItemRepository
    private lateinit var sessions: RoomSessionRepository
    private lateinit var sets: RoomSetRepository
    private lateinit var store: RoomRoutineTemplateStore
    private val codec = SyncPayloadCodec(Json)
    private val owner = UserId("alice")
    private val source = Routine(RoutineId("upper"), owner, "Upper A", 1)
    private val original =
        listOf(
            RoutineItem(RoutineItemId("first"), source.id, ExerciseId("bench"), 1, MovementTarget(3, 12, 50.0)),
            RoutineItem(RoutineItemId("second"), source.id, ExerciseId("squat"), 2),
        )
    private val session =
        WorkoutSession(
            SessionId("visit"),
            owner,
            null,
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(600),
            null,
            RoutineOrigin(source.id.value, source.name),
            WorkoutWorkflow(originalRoutineItems = original, structuralEdits = true),
        )
    private val appearance = SessionExercise(SessionExerciseId("bench-occurrence"), session.id, ExerciseId("bench"), 1)
    private val actual = ExerciseSet("actual", appearance.id, 1, 50.0, 10, null, Instant.EPOCH.plusSeconds(60))
    private val revised =
        listOf(original.first(), RoutineItem(RoutineItemId("added"), source.id, ExerciseId("bench"), 2))

    private fun write() = RoutineTemplateWrite(source, original, source, revised, session, "reviewed-order")

    @Before
    fun prepare() =
        runTest {
            db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext(),
                        GymTrackerDatabase::class.java,
                    ).build()
            routines = RoomRoutineRepository(db.routineDao(), db, codec)
            items = RoomRoutineItemRepository(db.routineItemDao(), db, codec)
            sessions = RoomSessionRepository(db.sessionDao(), db, codec)
            sets = RoomSetRepository(db.setDao(), db, codec)
            store = RoomRoutineTemplateStore(db, codec)
            val bundled = """[
            {"id":"bench","name":"Bench","primaryMuscles":["CHEST"],"secondaryMuscles":[],"equipment":"BARBELL","instructions":[],"source":"test"},
            {"id":"squat","name":"Squat","primaryMuscles":["QUADS"],"secondaryMuscles":[],"equipment":"BARBELL","instructions":[],"source":"test"}
        ]"""
            CatalogSeeder(
                db.exerciseDao(),
                CatalogAssetReader {
                    bundled.byteInputStream()
                },
                Json,
                UnconfinedTestDispatcher(),
            ).seedIfEmpty(1L)
            routines.add(source)
            original.forEach { items.addItem(it) }
            sessions.startSession(session)
            RoomSessionExerciseRepository(db.sessionExerciseDao(), db, codec).add(appearance)
            sets.add(actual)
        }

    @After fun close() = db.close()

    @Test
    fun `template and handled marker commit together without rewriting workout or actuals`() =
        runTest {
            assertTrue(store.apply(write()))
            assertEquals(revised, items.itemsOf(source.id))
            assertEquals("reviewed-order", sessions.findSession(session.id)?.workflow?.reviewedStructure)
            assertEquals(session.endedAt, sessions.findSession(session.id)?.endedAt)
            assertEquals(listOf(actual), sets.observeForSessionExercise(appearance.id).first())
            assertEquals(source.name, sessions.findSession(session.id)?.routine?.name)
        }

    @Test
    fun `an original edited since review is refused before any write`() =
        runTest {
            routines.rename(source.id, "Changed separately")
            val before = db.syncQueueDao().pendingCount()
            assertFalse(store.apply(write()))
            assertEquals(original, items.itemsOf(source.id))
            assertNull(sessions.findSession(session.id)?.workflow?.reviewedStructure)
            assertEquals(before, db.syncQueueDao().pendingCount())
        }

    @Test
    fun `a failure after removal rolls back template marker and outbox while retaining saved workout`() =
        runTest {
            val before = db.syncQueueDao().pendingCount()
            val invalid =
                write().copy(
                    replacementItems = listOf(revised.first(), revised.last().copy(exerciseId = ExerciseId("unknown"))),
                )
            assertFails { store.apply(invalid) }
            assertEquals(original, items.itemsOf(source.id))
            assertNull(sessions.findSession(session.id)?.workflow?.reviewedStructure)
            assertEquals(before, db.syncQueueDao().pendingCount())
            assertEquals(listOf(actual), sets.observeForSessionExercise(appearance.id).first())
        }

    @Test
    fun `today-only marker refuses a workout that reopened during the review`() =
        runTest {
            sessions.updateSession(session.copy(endedAt = null))
            assertFalse(store.reviewed(session, "reviewed-order"))
            assertNull(sessions.findSession(session.id)?.endedAt)
            assertNull(sessions.findSession(session.id)?.workflow?.reviewedStructure)
        }
}
