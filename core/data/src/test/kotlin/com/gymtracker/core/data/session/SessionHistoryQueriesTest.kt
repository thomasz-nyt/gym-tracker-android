package com.gymtracker.core.data.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.sessionexercise.RoomSessionExerciseRepository
import com.gymtracker.core.data.set.RoomSetRepository
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.session.DeleteSession
import com.gymtracker.core.domain.session.RestoreSession
import com.gymtracker.core.domain.session.SessionHistory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-06 and US-06a against a real Room database: the history queries, and the cascade that
 * ADR-0012 relies on rather than deleting children by hand.
 */
@RunWith(RobolectricTestRunner::class)
class SessionHistoryQueriesTest {
    private lateinit var database: GymTrackerDatabase
    private lateinit var sessions: RoomSessionRepository
    private lateinit var sessionExercises: RoomSessionExerciseRepository
    private lateinit var sets: RoomSetRepository

    private val now: Instant = Instant.parse("2026-08-01T18:00:00Z")
    private val alice = UserId("alice")
    private val bob = UserId("bob")

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    GymTrackerDatabase::class.java,
                ).build()
        sessions = RoomSessionRepository(database.sessionDao())
        sessionExercises = RoomSessionExerciseRepository(database.sessionExerciseDao())
        sets = RoomSetRepository(database.setDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun workout(
        id: String,
        member: UserId = alice,
        startedAt: Instant = now.minus(Duration.ofDays(1)),
        endedAt: Instant? = now.minus(Duration.ofDays(1)).plus(Duration.ofHours(1)),
        weights: List<Double?> = emptyList(),
    ): SessionId {
        val session = SessionId(id)
        sessions.startSession(WorkoutSession(session, member, null, startedAt, endedAt, null))
        if (weights.isEmpty()) return session

        val appearance = SessionExerciseId("$id-se")
        sessionExercises.add(SessionExercise(appearance, session, ExerciseId("bench"), 1))
        weights.forEachIndexed { index, weight ->
            sets.add(ExerciseSet("$id-$index", appearance, index + 1, weight, 10, null, now))
        }
        return session
    }

    @Test
    fun `finished sessions come back newest first, and only for that member`() =
        runTest {
            workout("older", startedAt = now.minus(Duration.ofDays(5)))
            workout("newer", startedAt = now.minus(Duration.ofDays(2)))
            workout("bobs", member = bob, startedAt = now.minus(Duration.ofDays(1)))

            val finished = sessions.observeFinishedSessions(alice).first()

            assertEquals(listOf("newer", "older"), finished.map { it.id.value })
        }

    @Test
    fun `an unfinished session is not history`() =
        runTest {
            workout("running", endedAt = null)

            assertEquals(emptyList(), sessions.observeFinishedSessions(alice).first())
        }

    @Test
    fun `the exercises and sets of several sessions come back in one query each`() =
        runTest {
            val first = workout("first", startedAt = now.minus(Duration.ofDays(2)), weights = listOf(60.0, 60.0))
            val second = workout("second", weights = listOf(40.0))
            workout("untouched", member = bob, weights = listOf(999.0))

            val ids = listOf(first, second)

            assertEquals(2, sessionExercises.observeForSessions(ids).first().size)
            assertEquals(3, sets.observeForSessions(ids).first().size)
        }

    @Test
    fun `history summarises what is in the database`() =
        runTest {
            workout("s1", weights = listOf(100.0, 100.0, null))
            val history = SessionHistory(sessions, sessionExercises, sets)

            val row = history(alice).first().single()

            assertEquals(1, row.exerciseCount)
            assertEquals(3, row.setCount)
            assertEquals(2000.0, row.volumeKg)
            assertEquals(1, row.bodyweightSetCount)
            assertEquals(Duration.ofHours(1), row.duration)
        }

    @Test
    fun `deleting a session cascades to its exercises and sets`() =
        runTest {
            // ADR-0012 leans on the schema for this rather than deleting children by hand, so
            // the schema is what has to be tested.
            val doomed = workout("doomed", weights = listOf(60.0, 60.0))
            val kept = workout("kept", startedAt = now.minus(Duration.ofDays(3)), weights = listOf(50.0))

            sessions.deleteSession(doomed)

            assertNull(sessions.findSession(doomed))
            assertEquals(emptyList(), sessionExercises.observeForSessions(listOf(doomed)).first())
            assertEquals(emptyList(), sets.observeForSessions(listOf(doomed)).first())
            assertEquals(1, sets.observeForSessions(listOf(kept)).first().size, "the others are untouched")
        }

    @Test
    fun `undo puts the whole workout back`() =
        runTest {
            val session = workout("s1", weights = listOf(100.0, null))
            val before = sessions.findSession(session)
            val deleteSession = DeleteSession(sessions, sessionExercises, sets)
            val restoreSession = RestoreSession(sessions, sessionExercises, sets)

            val deleted = checkNotNull(deleteSession(session))
            restoreSession(deleted)

            assertEquals(before, sessions.findSession(session))
            assertEquals(1, sessionExercises.observeForSessions(listOf(session)).first().size)
            assertEquals(
                listOf("s1-0", "s1-1"),
                sets
                    .observeForSessions(listOf(session))
                    .first()
                    .map { it.id }
                    .sorted(),
            )
        }

    @Test
    fun `a restored set keeps its weight, reps and time to the millisecond`() =
        runTest {
            val session = workout("s1")
            val appearance = SessionExerciseId("s1-se")
            sessionExercises.add(SessionExercise(appearance, session, ExerciseId("bench"), 1))
            val original = ExerciseSet("set-1", appearance, 1, 61.23, 8, 9.5, Instant.parse("2026-08-01T17:04:05.123Z"))
            sets.add(original)
            val deleteSession = DeleteSession(sessions, sessionExercises, sets)
            val restoreSession = RestoreSession(sessions, sessionExercises, sets)

            restoreSession(checkNotNull(deleteSession(session)))

            assertEquals(original, sets.observeForSessionExercise(appearance).first().single())
        }
}
