package com.gymtracker.core.data.member

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.routine.RoomRoutineRepository
import com.gymtracker.core.data.session.RoomSessionRepository
import com.gymtracker.core.data.sessionexercise.RoomSessionExerciseRepository
import com.gymtracker.core.data.sync.SyncEntityNames
import com.gymtracker.core.data.sync.SyncPayloadCodec
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * US-58 against real Room and a real DataStore, per `specs/testing-strategy.md`
 * ("Repository + sync | Fake remote, real Room, assert queue behaviour").
 */
@RunWith(RobolectricTestRunner::class)
class RoomAccountAdoptionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var database: GymTrackerDatabase
    private lateinit var currentMember: DataStoreCurrentMember
    private lateinit var adoption: RoomAccountAdoption
    private lateinit var sessions: RoomSessionRepository
    private lateinit var sessionExercises: RoomSessionExerciseRepository
    private lateinit var routines: RoomRoutineRepository

    private val now: Instant = Instant.parse("2026-09-02T18:00:00Z")

    private fun preferences(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { folder.newFile(name) }

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GymTrackerDatabase::class.java)
                .build()
        val codec = SyncPayloadCodec(Json { ignoreUnknownKeys = true })
        currentMember = DataStoreCurrentMember(preferences("member.preferences_pb"))
        sessions = RoomSessionRepository(database.sessionDao(), database, codec)
        sessionExercises = RoomSessionExerciseRepository(database.sessionExerciseDao(), database, codec)
        routines = RoomRoutineRepository(database.routineDao(), database, codec)
        adoption =
            RoomAccountAdoption(
                database = database,
                // A real sign-in flow injects the same singleton DataStore this shares with
                // DataStoreCurrentMember's own file; a second file here only isolates the test.
                preferences = preferences("adoption.preferences_pb"),
                currentMember = currentMember,
                codec = codec,
            )
    }

    @After
    fun tearDown() = database.close()

    private suspend fun queueRows() = database.syncQueueDao().oldestFirst()

    private fun session(
        id: String,
        owner: UserId,
    ) = WorkoutSession(
        id = SessionId(id),
        userId = owner,
        gymName = null,
        startedAt = now,
        endedAt = null,
        metrics = null,
    )

    /** One session and one routine, both owned by whatever id this install currently has. */
    private suspend fun seedLocalData() {
        val localId = currentMember.id()
        sessions.startSession(session("s1", localId))
        routines.add(Routine(RoutineId("r1"), localId, "Upper A", position = 1))
    }

    @Test
    fun `first adoption re-assigns every local session and routine to the signed-in id`() =
        runTest {
            seedLocalData()
            val signedInAs = UserId("auth-uid-1")

            adoption.adopt(signedInAs)

            assertEquals(signedInAs, sessions.findSession(SessionId("s1"))?.userId)
            assertEquals(signedInAs, routines.find(RoutineId("r1"))?.userId)
        }

    @Test
    fun `first adoption moves the device's current member id to the signed-in id`() =
        runTest {
            seedLocalData()
            val signedInAs = UserId("auth-uid-1")

            adoption.adopt(signedInAs)

            assertEquals(signedInAs, currentMember.id())
        }

    @Test
    fun `first adoption enqueues a fresh write row for each re-assigned session and routine`() =
        runTest {
            // seedLocalData() already enqueues its own two rows (US-57) — this test is about
            // the *re-key's* rows, so it diffs against what was already queued before adopt().
            seedLocalData()
            val idsBefore = queueRows().map { it.id }.toSet()
            val signedInAs = UserId("auth-uid-1")

            adoption.adopt(signedInAs)

            val newRows = queueRows().filterNot { it.id in idsBefore }
            assertEquals(2, newRows.size, "one new row for the session, one for the routine")
            assertTrue(
                newRows.any {
                    it.entity == SyncEntityNames.SESSIONS &&
                        it.payloadJson?.contains(signedInAs.value) == true
                },
            )
            assertTrue(
                newRows.any {
                    it.entity == SyncEntityNames.ROUTINES &&
                        it.payloadJson?.contains(signedInAs.value) == true
                },
            )
        }

    @Test
    fun `an install with no local rows still adopts cleanly, enqueuing nothing`() =
        runTest {
            currentMember.id() // establishes a local id, but nothing is ever written under it
            val signedInAs = UserId("auth-uid-1")

            adoption.adopt(signedInAs)

            assertEquals(signedInAs, currentMember.id())
            assertEquals(0, queueRows().size)
        }

    @Test
    fun `a second sign-in on the same install does not re-assign what the first one already adopted`() =
        runTest {
            seedLocalData()
            val firstAccount = UserId("auth-uid-1")
            adoption.adopt(firstAccount)

            adoption.adopt(UserId("auth-uid-2"))

            assertEquals(
                firstAccount,
                sessions.findSession(SessionId("s1"))?.userId,
                "the session stays with whoever already adopted it",
            )
            assertEquals(firstAccount, routines.find(RoutineId("r1"))?.userId)
        }

    @Test
    fun `a second sign-in still moves the device's current member id, so the new member sees an empty view`() =
        runTest {
            seedLocalData()
            adoption.adopt(UserId("auth-uid-1"))

            val secondAccount = UserId("auth-uid-2")
            adoption.adopt(secondAccount)

            assertEquals(secondAccount, currentMember.id())
        }

    @Test
    fun `a second sign-in enqueues nothing new — nothing was re-assigned`() =
        runTest {
            seedLocalData()
            adoption.adopt(UserId("auth-uid-1"))
            val afterFirst = queueRows().size

            adoption.adopt(UserId("auth-uid-2"))

            assertEquals(afterFirst, queueRows().size)
        }

    @Test
    fun `signing back in as the very first adopted account is still a no-op re-assignment`() =
        runTest {
            // "the same member signing in again after signing out" — ADR-0042's own example.
            seedLocalData()
            val account = UserId("auth-uid-1")
            adoption.adopt(account)
            val afterFirst = queueRows().size

            adoption.adopt(account)

            assertEquals(afterFirst, queueRows().size)
            assertEquals(account, currentMember.id())
        }

    @Test
    fun `a session_exercise reached through a re-assigned session is still reachable afterward`() =
        runTest {
            // session_exercises carries no user_id of its own (ADR-0043's amendment) — it is
            // reached through session_id, which does not change, so nothing here should break.
            seedLocalData()
            val seId = SessionExerciseId("se1")
            sessionExercises.add(SessionExercise(seId, SessionId("s1"), ExerciseId("bench"), position = 1))

            adoption.adopt(UserId("auth-uid-1"))

            assertEquals(seId, sessionExercises.find(seId)?.id)
        }
}
