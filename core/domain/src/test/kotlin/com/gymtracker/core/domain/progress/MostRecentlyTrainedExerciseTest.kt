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
import kotlin.test.assertNull

/**
 * US-33: the Progress tab's top section leads with a lift, chosen without asking — the one the
 * member most recently actually trained.
 */
class MostRecentlyTrainedExerciseTest {
    private val alice = UserId("alice")
    private val bench = ExerciseId("bench")
    private val squat = ExerciseId("squat")

    private val sessions = FakeSessionRepository()
    private val sessionExercises = FakeSessionExerciseRepository()
    private val sets = FakeSetRepository()
    private val mostRecent = MostRecentlyTrainedExercise(sessions, sessionExercises, sets)

    private var nextAppearance = 1

    private suspend fun finishedSession(
        id: String,
        startedAt: Instant,
    ): SessionId {
        val sessionId = SessionId(id)
        sessions.startSession(
            WorkoutSession(sessionId, alice, null, startedAt, startedAt.plusSeconds(3600), null),
        )
        return sessionId
    }

    private suspend fun appearance(
        sessionId: SessionId,
        exerciseId: ExerciseId,
        position: Int,
        performedAt: Instant? = null,
    ) {
        val id = SessionExerciseId("se-${nextAppearance++}")
        val row = SessionExercise(id, sessionId, exerciseId, position)
        sessionExercises.add(row)
        sets.belongsTo(row)
        performedAt?.let { sets.add(ExerciseSet("set-${id.value}", id, 1, 60.0, 8, null, it)) }
    }

    @Test
    fun `with no finished session there is nothing to lead with`() =
        runTest {
            assertNull(mostRecent(alice))
        }

    @Test
    fun `the most recent session's only performed exercise leads`() =
        runTest {
            val now = Instant.parse("2026-08-10T18:00:00Z")
            val session = finishedSession("s1", now)
            appearance(session, bench, position = 1, performedAt = now)

            assertEquals(bench, mostRecent(alice))
        }

    @Test
    fun `picks the newest session, not just any finished one`() =
        runTest {
            val older = finishedSession("older", Instant.parse("2026-08-01T18:00:00Z"))
            appearance(older, squat, position = 1, performedAt = Instant.parse("2026-08-01T18:00:00Z"))
            val newer = finishedSession("newer", Instant.parse("2026-08-08T18:00:00Z"))
            appearance(newer, bench, position = 1, performedAt = Instant.parse("2026-08-08T18:00:00Z"))

            assertEquals(bench, mostRecent(alice))
        }

    @Test
    fun `an appearance added but never performed is skipped in favour of one that was`() =
        runTest {
            // US-29: a routine copies in every movement, whether or not the member reaches it.
            val now = Instant.parse("2026-08-10T18:00:00Z")
            val session = finishedSession("s1", now)
            appearance(session, bench, position = 1, performedAt = null)
            appearance(session, squat, position = 2, performedAt = now)

            assertEquals(squat, mostRecent(alice))
        }

    @Test
    fun `a session where nothing was actually performed leads nowhere, even if an older one has sets`() =
        runTest {
            // The deliberate simplification: only the newest finished session is considered, so
            // an all-planned-nothing-lifted session (US-13's honest-empty case) reports absence
            // rather than reaching back through history for something to show.
            val older = finishedSession("older", Instant.parse("2026-08-01T18:00:00Z"))
            appearance(older, squat, position = 1, performedAt = Instant.parse("2026-08-01T18:00:00Z"))
            val newer = finishedSession("newer", Instant.parse("2026-08-08T18:00:00Z"))
            appearance(newer, bench, position = 1, performedAt = null)

            assertNull(mostRecent(alice))
        }
}
