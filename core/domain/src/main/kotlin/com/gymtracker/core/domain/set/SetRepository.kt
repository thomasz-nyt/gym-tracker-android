package com.gymtracker.core.domain.set

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Sets, the thing the whole app exists to record quickly. */
interface SetRepository {
    /** The sets of one session-exercise, in index order. */
    fun observeForSessionExercise(sessionExerciseId: SessionExerciseId): Flow<List<ExerciseSet>>

    /**
     * Every set belonging to any of [sessionIds], reached through `session_exercises`
     * (ADR-0004). Backs the volume on each history row (US-06) in one query rather than one
     * per session.
     */
    fun observeForSessions(sessionIds: List<SessionId>): Flow<List<ExerciseSet>>

    /**
     * The member's most recent set of [exerciseId] in any session, or null if they have never
     * done it. Backs the US-03 prefill.
     */
    suspend fun lastSetOf(
        exerciseId: ExerciseId,
        member: UserId,
    ): ExerciseSet?

    /**
     * The member's most recent set of [exerciseId] from a session other than
     * [excludingSessionId], or null when there is none — either the exercise has never been
     * performed, or every time it was is the session being excluded.
     *
     * Backs the ADR-0023 rest-panel comparison, which must show what was lifted last *time*,
     * never the set that was just logged in this same session compared against itself.
     */
    suspend fun lastSetOfBefore(
        exerciseId: ExerciseId,
        member: UserId,
        excludingSessionId: SessionId,
    ): ExerciseSet?

    /** The `performed_at` of the most recent set in a session, or null. Backs US-01's staleness. */
    suspend fun lastSetAtInSession(sessionId: SessionId): Instant?

    /** The index a set appended to [sessionExerciseId] should take. 1-based. */
    suspend fun nextSetIndex(sessionExerciseId: SessionExerciseId): Int

    /** Persists a set. Must complete before any UI transition (US-03). */
    suspend fun add(set: ExerciseSet)

    /**
     * Writes back a corrected weight, reps or RPE for a set that already exists (US-04). [set]'s
     * `id` addresses the row; every other field on it is the new value to store.
     */
    suspend fun update(set: ExerciseSet)

    /**
     * Deletes one set by id (US-04).
     *
     * @return the set as it was just before the delete, so a caller can undo it, or null if
     *   there was no set with that id — meaning there is nothing to undo either.
     */
    suspend fun delete(id: String): ExerciseSet?
}
