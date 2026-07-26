package com.gymtracker.core.domain.set

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.UserId
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Sets, the thing the whole app exists to record quickly. */
interface SetRepository {
    /** The sets of one session-exercise, in index order. */
    fun observeForSessionExercise(sessionExerciseId: SessionExerciseId): Flow<List<ExerciseSet>>

    /**
     * The member's most recent set of [exerciseId] in any session, or null if they have never
     * done it. Backs the US-03 prefill.
     */
    suspend fun lastSetOf(
        exerciseId: ExerciseId,
        member: UserId,
    ): ExerciseSet?

    /** The `performed_at` of the most recent set in a session, or null. Backs US-01's staleness. */
    suspend fun lastSetAtInSession(sessionId: com.gymtracker.core.domain.model.SessionId): Instant?

    /** The index a set appended to [sessionExerciseId] should take. 1-based. */
    suspend fun nextSetIndex(sessionExerciseId: SessionExerciseId): Int

    /** Persists a set. Must complete before any UI transition (US-03). */
    suspend fun add(set: ExerciseSet)
}
