package com.gymtracker.core.data.set

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.session.RoomSessionRepository
import com.gymtracker.core.data.session.SYNC_STATE_PENDING
import com.gymtracker.core.data.sessionexercise.RoomSessionExerciseRepository
import com.gymtracker.core.data.sync.SyncPayloadCodec
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.set.LogSet
import com.gymtracker.core.domain.set.PrefillFromLastSet
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** US-03 against a real database: the prefill join, indices, and cascade behaviour. */
@RunWith(RobolectricTestRunner::class)
class SetRepositoryTest {
    private lateinit var database: GymTrackerDatabase
    private lateinit var sets: RoomSetRepository
    private lateinit var sessions: RoomSessionRepository
    private lateinit var sessionExercises: RoomSessionExerciseRepository

    private val now: Instant = Instant.parse("2026-07-26T18:00:00Z")
    private val alice = UserId("alice")
    private val bob = UserId("bob")
    private val bench = ExerciseId("bench")
    private var nextId = 1

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    GymTrackerDatabase::class.java,
                ).build()
        val codec = SyncPayloadCodec(Json { ignoreUnknownKeys = true })
        sets = RoomSetRepository(database.setDao(), database, codec)
        sessions = RoomSessionRepository(database.sessionDao(), database, codec)
        sessionExercises = RoomSessionExerciseRepository(database.sessionExerciseDao(), database, codec)
    }

    @After
    fun tearDown() = database.close()

    private fun logSet(at: Instant) = LogSet(sets, Clock.fixed(at, ZoneOffset.UTC)) { "set-${nextId++}" }

    private suspend fun appearance(
        sessionId: String,
        member: UserId = alice,
        startedAt: Instant = now,
        exercise: ExerciseId = bench,
    ): SessionExerciseId {
        sessions.startSession(
            WorkoutSession(SessionId(sessionId), member, null, startedAt, null, null),
        )
        val id = SessionExerciseId("se-$sessionId-${exercise.value}")
        sessionExercises.add(SessionExercise(id, SessionId(sessionId), exercise, 1))
        return id
    }

    @Test
    fun `the earlier-session lookup skips the session being performed`() =
        runTest {
            // ADR-0023's comparison: "what you lifted last time" must not find the set you just
            // logged a minute ago in the workout you are standing in.
            val lastWeek = appearance("s-last-week", startedAt = now.minus(Duration.ofDays(7)))
            logSet(now.minus(Duration.ofDays(7)))(lastWeek, 100.0, WeightUnit.LB, reps = 8, rpe = null)
            val today = appearance("s-today")
            logSet(now)(today, 135.0, WeightUnit.LB, reps = 5, rpe = null)

            val comparison = sets.lastSetOfBefore(bench, alice, SessionId("s-today"))

            assertEquals(45.36, comparison?.weightKg, "last week's 100 lb, not today's 135")
            assertEquals(8, comparison?.reps)
        }

    @Test
    fun `the earlier-session lookup finds nothing on a first-ever set`() =
        runTest {
            val today = appearance("s-today")
            logSet(now)(today, 135.0, WeightUnit.LB, reps = 5, rpe = null)

            assertNull(
                sets.lastSetOfBefore(bench, alice, SessionId("s-today")),
                "nothing to compare against is rendered as nothing, never as zero",
            )
        }

    @Test
    fun `the earlier-session lookup does not cross members`() =
        runTest {
            val hers = appearance("s-bob", member = bob, startedAt = now.minus(Duration.ofDays(7)))
            logSet(now.minus(Duration.ofDays(7)))(hers, 200.0, WeightUnit.LB, reps = 8, rpe = null)
            val today = appearance("s-today")
            logSet(now)(today, 135.0, WeightUnit.LB, reps = 5, rpe = null)

            assertNull(
                sets.lastSetOfBefore(bench, alice, SessionId("s-today")),
                "another member's set is not this member's history",
            )
        }

    @Test
    fun `a logged set round-trips through the database`() =
        runTest {
            val se = appearance("s1")

            logSet(now)(se, 135.0, WeightUnit.LB, reps = 5, rpe = 8.0)

            val stored = sets.observeForSessionExercise(se).first().single()
            assertEquals(61.23, stored.weightKg, "canonical kilograms, per ADR-0006")
            assertEquals(5, stored.reps)
            assertEquals(8.0, stored.rpe)
            assertEquals(now, stored.performedAt)
        }

    @Test
    fun `a bodyweight set stores null weight, not zero`() =
        runTest {
            val se = appearance("s1")

            logSet(now)(se, null, WeightUnit.KG, reps = 12, rpe = null)

            assertNull(
                sets
                    .observeForSessionExercise(se)
                    .first()
                    .single()
                    .weightKg,
            )
        }

    @Test
    fun `prefill finds the last set of the same exercise in an earlier session`() =
        runTest {
            // The join ADR-0004 chose instead of a denormalised exercise_id on sets.
            val lastWeek = appearance("old", startedAt = now.minus(Duration.ofDays(7)))
            logSet(now.minus(Duration.ofDays(7)))(lastWeek, 60.0, WeightUnit.KG, reps = 5, rpe = null)

            val prefill = PrefillFromLastSet(sets)(bench, alice, WeightUnit.KG)

            assertEquals(60.0, prefill?.weight)
            assertEquals(5, prefill?.reps)
        }

    @Test
    fun `prefill takes the most recent set, not the first`() =
        runTest {
            val old = appearance("old", startedAt = now.minus(Duration.ofDays(7)))
            logSet(now.minus(Duration.ofDays(7)))(old, 60.0, WeightUnit.KG, reps = 5, rpe = null)
            val recent = appearance("recent", startedAt = now)
            logSet(now)(recent, 65.0, WeightUnit.KG, reps = 3, rpe = null)

            val prefill = PrefillFromLastSet(sets)(bench, alice, WeightUnit.KG)

            assertEquals(65.0, prefill?.weight)
            assertEquals(3, prefill?.reps)
        }

    @Test
    fun `prefill is empty for an exercise never performed`() =
        runTest {
            assertNull(PrefillFromLastSet(sets)(ExerciseId("never"), alice, WeightUnit.KG))
        }

    @Test
    fun `prefill does not leak another members history`() =
        runTest {
            val bobs = appearance("bobs", member = bob)
            logSet(now)(bobs, 100.0, WeightUnit.KG, reps = 1, rpe = null)

            assertNull(PrefillFromLastSet(sets)(bench, alice, WeightUnit.KG))
        }

    @Test
    fun `prefill converts into the members unit`() =
        runTest {
            val se = appearance("s1")
            logSet(now)(se, 61.23, WeightUnit.KG, reps = 5, rpe = null)

            assertEquals(135.0, PrefillFromLastSet(sets)(bench, alice, WeightUnit.LB)?.weight)
        }

    @Test
    fun `set indices are per appearance, so a repeated exercise starts again at one`() =
        runTest {
            sessions.startSession(WorkoutSession(SessionId("s1"), alice, null, now, null, null))
            val first = SessionExerciseId("se-1")
            val second = SessionExerciseId("se-2")
            sessionExercises.add(SessionExercise(first, SessionId("s1"), bench, 1))
            sessionExercises.add(SessionExercise(second, SessionId("s1"), bench, 2))
            val log = logSet(now)

            log(first, 60.0, WeightUnit.KG, reps = 5, rpe = null)
            log(first, 60.0, WeightUnit.KG, reps = 5, rpe = null)
            val onSecondVisit = log(second, 50.0, WeightUnit.KG, reps = 8, rpe = null)

            assertEquals(1, onSecondVisit.setIndex)
            assertEquals(2, sets.observeForSessionExercise(first).first().size)
        }

    @Test
    fun `last activity in a session is its most recent set`() =
        runTest {
            // This is what US-01's stale-session policy needs, and could not have until now.
            val se = appearance("s1", startedAt = now.minus(Duration.ofHours(3)))
            logSet(now.minus(Duration.ofHours(2)))(se, 60.0, WeightUnit.KG, reps = 5, rpe = null)
            logSet(now.minus(Duration.ofMinutes(30)))(se, 60.0, WeightUnit.KG, reps = 5, rpe = null)

            assertEquals(
                now.minus(Duration.ofMinutes(30)),
                sets.lastSetAtInSession(SessionId("s1")),
            )
        }

    @Test
    fun `a session with no sets has no last activity`() =
        runTest {
            appearance("s1")

            assertNull(sets.lastSetAtInSession(SessionId("s1")))
        }

    @Test
    fun `discarding a session removes its sets`() =
        runTest {
            val se = appearance("s1")
            logSet(now)(se, 60.0, WeightUnit.KG, reps = 5, rpe = null)

            sessions.deleteSession(SessionId("s1"))

            assertTrue(sets.observeForSessionExercise(se).first().isEmpty(), "cascade through session_exercises")
        }

    @Test
    fun `update persists the new weight, reps and rpe`() =
        runTest {
            val se = appearance("s1")
            val logged = logSet(now)(se, 60.0, WeightUnit.KG, reps = 5, rpe = 7.0)

            sets.update(logged.copy(weightKg = 65.0, reps = 6, rpe = 8.0))

            val stored = sets.observeForSessionExercise(se).first().single()
            assertEquals(65.0, stored.weightKg)
            assertEquals(6, stored.reps)
            assertEquals(8.0, stored.rpe)
        }

    @Test
    fun `update keeps the set's id, index and performed_at`() =
        runTest {
            val se = appearance("s1")
            val logged = logSet(now)(se, 60.0, WeightUnit.KG, reps = 5, rpe = null)

            sets.update(logged.copy(weightKg = 65.0, reps = 6, rpe = null))

            val stored = sets.observeForSessionExercise(se).first().single()
            assertEquals(logged.id, stored.id)
            assertEquals(logged.setIndex, stored.setIndex)
            assertEquals(logged.performedAt, stored.performedAt)
        }

    @Test
    fun `update marks the row pending and bumps updated_at`() =
        runTest {
            val se = appearance("s1")
            val logged = logSet(now)(se, 60.0, WeightUnit.KG, reps = 5, rpe = null)
            val insertedAt = database.setDao().find(logged.id)?.updatedAt

            sets.update(logged.copy(weightKg = 65.0))

            val entity = database.setDao().find(logged.id)
            assertEquals(SYNC_STATE_PENDING, entity?.syncState)
            assertTrue((entity?.updatedAt ?: 0) >= (insertedAt ?: 0), "updated_at is stamped on write, per add()")
        }

    @Test
    fun `delete removes the row and returns what was deleted`() =
        runTest {
            val se = appearance("s1")
            val logged = logSet(now)(se, 60.0, WeightUnit.KG, reps = 5, rpe = 7.5)

            val deleted = sets.delete(logged.id)

            assertEquals(logged, deleted)
            assertTrue(sets.observeForSessionExercise(se).first().isEmpty())
        }

    @Test
    fun `deleting a set does not touch its siblings`() =
        runTest {
            val se = appearance("s1")
            val first = logSet(now)(se, 60.0, WeightUnit.KG, reps = 5, rpe = null)
            val second = logSet(now)(se, 60.0, WeightUnit.KG, reps = 5, rpe = null)

            sets.delete(first.id)

            assertEquals(listOf(second), sets.observeForSessionExercise(se).first())
        }

    @Test
    fun `deleting a set does not touch its parent session_exercise`() =
        runTest {
            val se = appearance("s1")
            val logged = logSet(now)(se, 60.0, WeightUnit.KG, reps = 5, rpe = null)

            sets.delete(logged.id)

            assertEquals(se, sessionExercises.find(se)?.id, "the session_exercise row survives its last set")
        }

    @Test
    fun `deleting a set that does not exist reports nothing to undo`() =
        runTest {
            assertNull(sets.delete("never-existed"))
        }
}

/** The v3 to v4 migration adds `sets` without disturbing anything already recorded. */
@RunWith(RobolectricTestRunner::class)
class SetMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            GymTrackerDatabase::class.java,
        )

    @Test
    fun `migrating from 3 to 4 keeps sessions, catalog and session exercises`() {
        val name = "migration-3-4.db"

        helper.createDatabase(name, 3).use { v3 ->
            v3.execSQL(
                "INSERT INTO sessions (id, user_id, gym_name, started_at, ended_at, avg_hr, max_hr, " +
                    "active_kcal, metrics_source, updated_at, sync_state) " +
                    "VALUES ('s1', 'u1', NULL, 1000, NULL, NULL, NULL, NULL, NULL, 1000, 'PENDING')",
            )
            v3.execSQL(
                "INSERT INTO exercises (id, name, aliases_json, primary_json, secondary_json, equipment, " +
                    "instructions_json, media_url, media_type, youtube_url, source, updated_at) " +
                    "VALUES ('e1', 'Bench Press', '[]', '[]', '[]', 'BARBELL', '[]', NULL, NULL, NULL, 'x', 1000)",
            )
            v3.execSQL(
                "INSERT INTO session_exercises (id, session_id, exercise_id, position, updated_at, sync_state) " +
                    "VALUES ('se1', 's1', 'e1', 1, 1000, 'PENDING')",
            )
        }

        val v4 = helper.runMigrationsAndValidate(name, 4, true, GymTrackerDatabase.MIGRATION_3_4)

        listOf("sessions", "exercises", "session_exercises").forEach { table ->
            v4.query("SELECT COUNT(*) FROM $table").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0), "$table survived the upgrade")
            }
        }
        v4.query("SELECT COUNT(*) FROM sets").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
    }
}
