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

/** US-06's history list, aggregated in SQL. */
@RunWith(RobolectricTestRunner::class)
class SessionHistoryTest {
    private lateinit var database: GymTrackerDatabase
    private lateinit var sessions: RoomSessionRepository
    private lateinit var sessionExercises: RoomSessionExerciseRepository
    private lateinit var sets: RoomSetRepository
    private lateinit var history: RoomSessionHistory

    private val now: Instant = Instant.parse("2026-07-28T18:00:00Z")
    private val alice = UserId("alice")
    private val bob = UserId("bob")
    private var nextSet = 1

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
        history = RoomSessionHistory(database.sessionDao())
    }

    @After
    fun tearDown() = database.close()

    private suspend fun workout(
        id: String,
        member: UserId = alice,
        startedAt: Instant = now.minus(Duration.ofHours(1)),
        endedAt: Instant? = now,
        exercises: Int = 1,
    ): List<SessionExerciseId> {
        sessions.startSession(WorkoutSession(SessionId(id), member, null, startedAt, null, null))
        endedAt?.let { sessions.endSession(SessionId(id), it) }
        return (1..exercises).map { position ->
            val se = SessionExerciseId("se-$id-$position")
            sessionExercises.add(SessionExercise(se, SessionId(id), ExerciseId("e$position"), position))
            se
        }
    }

    private suspend fun logSet(
        into: SessionExerciseId,
        weightKg: Double?,
        reps: Int,
    ) {
        sets.add(ExerciseSet("set-${nextSet++}", into, nextSet, weightKg, reps, null, now))
    }

    @Test
    fun `an unfinished session is not in history`() =
        runTest {
            workout("today", endedAt = null)

            assertEquals(emptyList(), history.observeHistory(alice).first())
        }

    @Test
    fun `a finished session appears with its counts`() =
        runTest {
            val (first, second) = workout("done", exercises = 2)
            logSet(first, 60.0, 5)
            logSet(first, 60.0, 5)
            logSet(second, 40.0, 10)

            val row = history.observeHistory(alice).first().single()

            assertEquals(2, row.exerciseCount)
            assertEquals(3, row.setCount)
        }

    @Test
    fun `volume is weight times reps across the whole session`() =
        runTest {
            val (only) = workout("done")
            logSet(only, 60.0, 5)
            logSet(only, 50.0, 10)

            assertEquals(
                800.0,
                history
                    .observeHistory(alice)
                    .first()
                    .single()
                    .volumeKg,
            )
        }

    @Test
    fun `a bodyweight-only session has no volume rather than zero`() =
        runTest {
            // The decision behind Volume: "0 kg" would claim they lifted nothing.
            val (only) = workout("done")
            logSet(only, null, 20)

            assertNull(
                history
                    .observeHistory(alice)
                    .first()
                    .single()
                    .volumeKg,
            )
        }

    @Test
    fun `bodyweight sets do not drag the volume down`() =
        runTest {
            val (only) = workout("done")
            logSet(only, 60.0, 5)
            logSet(only, null, 20)

            assertEquals(
                300.0,
                history
                    .observeHistory(alice)
                    .first()
                    .single()
                    .volumeKg,
            )
        }

    @Test
    fun `sessions are newest first`() =
        runTest {
            workout("old", startedAt = now.minus(Duration.ofDays(7)), endedAt = now.minus(Duration.ofDays(7)))
            workout("recent", startedAt = now.minus(Duration.ofDays(1)), endedAt = now.minus(Duration.ofDays(1)))

            assertEquals(
                listOf("recent", "old"),
                history.observeHistory(alice).first().map { it.id.value },
            )
        }

    @Test
    fun `duration is the span the workout covered`() =
        runTest {
            workout("done", startedAt = now.minus(Duration.ofMinutes(75)), endedAt = now)

            assertEquals(
                Duration.ofMinutes(75),
                history
                    .observeHistory(alice)
                    .first()
                    .single()
                    .duration,
            )
        }

    @Test
    fun `another member's sessions are not mine`() =
        runTest {
            workout("bobs", member = bob)

            assertEquals(emptyList(), history.observeHistory(alice).first())
        }

    @Test
    fun `a finished session with no sets still reports honest zeros`() =
        runTest {
            // EndSession discards empty sessions, so this should not occur through the app;
            // if one ever does, it must not read as a workout that had volume.
            workout("empty", exercises = 0)

            val row = history.observeHistory(alice).first().single()
            assertEquals(0, row.setCount)
            assertEquals(0, row.exerciseCount)
            assertNull(row.volumeKg)
        }
}
