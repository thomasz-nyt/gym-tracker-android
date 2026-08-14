package com.gymtracker.core.domain.progress

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.session.FakeSessionRepository
import com.gymtracker.core.domain.sessionexercise.FakeSessionExerciseRepository
import com.gymtracker.core.domain.set.FakeSetRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * US-38: which of the member's finished sessions the Progress list should badge as containing a
 * personal record — an O(sets) read purpose-built for a 200-row list, unlike
 * [PersonalRecordsAchievedIn] which re-reads the member's whole history per set and is meant
 * for a single session (US-31's finish summary).
 *
 * Mirrors [DetectPersonalRecord]'s own rule exactly: the first time at a rep count is not a
 * record, and beating has to be strict.
 */
class SessionsWithRecordsTest {
    private val member = UserId("alice")
    private val bench = ExerciseId("bench")

    @Test
    fun `with no sessions, nothing is badged`() =
        runTest {
            val sessionsWithRecords =
                SessionsWithRecords(FakeSessionRepository(), FakeSessionExerciseRepository(), FakeSetRepository())

            assertEquals(emptySet(), sessionsWithRecords(member))
        }

    @Test
    fun `the first set at a rep count is not a record, even though it is the heaviest so far`() =
        runTest {
            val session = finishedSession("s1", "2026-08-01T00:00:00Z")
            val sessionExercises = FakeSessionExerciseRepository()
            val sets = FakeSetRepository()
            val sessionsRepo = FakeSessionRepository(initial = listOf(session))
            seedSet(sessionExercises, sets, "s1", "se1", "set1", weight = 100.0, reps = 8, at = "2026-08-01T00:00:00Z")

            val sessionsWithRecords = SessionsWithRecords(sessionsRepo, sessionExercises, sets)

            assertEquals(emptySet(), sessionsWithRecords(member))
        }

    @Test
    fun `a set that strictly beats the previous best badges its own session`() =
        runTest {
            val sessionExercises = FakeSessionExerciseRepository()
            val sets = FakeSetRepository()
            val sessionsRepo =
                FakeSessionRepository(
                    initial =
                        listOf(
                            finishedSession("s1", "2026-08-01T00:00:00Z"),
                            finishedSession("s2", "2026-08-08T00:00:00Z"),
                        ),
                )
            seedSet(sessionExercises, sets, "s1", "se1", "set1", weight = 100.0, reps = 8, at = "2026-08-01T00:00:00Z")
            seedSet(sessionExercises, sets, "s2", "se2", "set2", weight = 105.0, reps = 8, at = "2026-08-08T00:00:00Z")

            val sessionsWithRecords = SessionsWithRecords(sessionsRepo, sessionExercises, sets)

            assertEquals(setOf(SessionId("s2")), sessionsWithRecords(member))
        }

    @Test
    fun `equalling the previous best is not a record`() =
        runTest {
            val sessionExercises = FakeSessionExerciseRepository()
            val sets = FakeSetRepository()
            val sessionsRepo =
                FakeSessionRepository(
                    initial =
                        listOf(
                            finishedSession("s1", "2026-08-01T00:00:00Z"),
                            finishedSession("s2", "2026-08-08T00:00:00Z"),
                        ),
                )
            seedSet(sessionExercises, sets, "s1", "se1", "set1", weight = 100.0, reps = 8, at = "2026-08-01T00:00:00Z")
            seedSet(sessionExercises, sets, "s2", "se2", "set2", weight = 100.0, reps = 8, at = "2026-08-08T00:00:00Z")

            val sessionsWithRecords = SessionsWithRecords(sessionsRepo, sessionExercises, sets)

            assertEquals(emptySet(), sessionsWithRecords(member))
        }

    @Test
    fun `a lighter, later set does not badge its session`() =
        runTest {
            val sessionExercises = FakeSessionExerciseRepository()
            val sets = FakeSetRepository()
            val sessionsRepo =
                FakeSessionRepository(
                    initial =
                        listOf(
                            finishedSession("s1", "2026-08-01T00:00:00Z"),
                            finishedSession("s2", "2026-08-08T00:00:00Z"),
                        ),
                )
            seedSet(sessionExercises, sets, "s1", "se1", "set1", weight = 100.0, reps = 8, at = "2026-08-01T00:00:00Z")
            seedSet(sessionExercises, sets, "s2", "se2", "set2", weight = 90.0, reps = 8, at = "2026-08-08T00:00:00Z")

            val sessionsWithRecords = SessionsWithRecords(sessionsRepo, sessionExercises, sets)

            assertEquals(emptySet(), sessionsWithRecords(member))
        }

    @Test
    fun `a different rep count is a separate record track, per ADR-0025`() =
        runTest {
            // Both are each their own exercise's "first time", so neither is a record on its own.
            val sessionExercises = FakeSessionExerciseRepository()
            val sets = FakeSetRepository()
            val sessionsRepo = FakeSessionRepository(initial = listOf(finishedSession("s1", "2026-08-01T00:00:00Z")))
            seedSet(sessionExercises, sets, "s1", "se1", "set1", weight = 100.0, reps = 8, at = "2026-08-01T00:00:00Z")
            seedSet(sessionExercises, sets, "s1", "se2", "set2", weight = 50.0, reps = 12, at = "2026-08-01T00:01:00Z")

            val sessionsWithRecords = SessionsWithRecords(sessionsRepo, sessionExercises, sets)

            assertEquals(emptySet(), sessionsWithRecords(member))
        }

    @Test
    fun `bodyweight sets never count toward the record track`() =
        runTest {
            val sessionExercises = FakeSessionExerciseRepository()
            val sets = FakeSetRepository()
            val sessionsRepo =
                FakeSessionRepository(
                    initial =
                        listOf(
                            finishedSession("s1", "2026-08-01T00:00:00Z"),
                            finishedSession("s2", "2026-08-08T00:00:00Z"),
                        ),
                )
            seedSet(sessionExercises, sets, "s1", "se1", "set1", weight = null, reps = 8, at = "2026-08-01T00:00:00Z")
            seedSet(sessionExercises, sets, "s2", "se2", "set2", weight = 100.0, reps = 8, at = "2026-08-08T00:00:00Z")

            val sessionsWithRecords = SessionsWithRecords(sessionsRepo, sessionExercises, sets)

            // Bodyweight is skipped entirely, so the loaded set is the true "first time" — not a
            // record, even though it is the only loaded set in the whole history.
            assertTrue(sessionsWithRecords(member).isEmpty())
        }

    private fun finishedSession(
        id: String,
        startedAt: String,
    ): WorkoutSession {
        val started = Instant.parse(startedAt)
        return WorkoutSession(
            id = SessionId(id),
            userId = member,
            gymName = null,
            startedAt = started,
            endedAt = started.plusSeconds(SESSION_LENGTH_SECONDS),
            metrics = null,
        )
    }

    private suspend fun seedSet(
        sessionExercises: FakeSessionExerciseRepository,
        sets: FakeSetRepository,
        sessionId: String,
        sessionExerciseId: String,
        setId: String,
        weight: Double?,
        reps: Int,
        at: String,
    ) {
        val appearance = SessionExercise(SessionExerciseId(sessionExerciseId), SessionId(sessionId), bench, 1)
        sessionExercises.add(appearance)
        sets.belongsTo(appearance)
        sets.add(
            ExerciseSet(setId, SessionExerciseId(sessionExerciseId), 1, weight, reps, null, Instant.parse(at)),
        )
    }

    private companion object {
        const val SESSION_LENGTH_SECONDS = 3_000L
    }
}
