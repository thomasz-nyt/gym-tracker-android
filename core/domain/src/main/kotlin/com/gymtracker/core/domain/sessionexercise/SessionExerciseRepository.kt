package com.gymtracker.core.domain.sessionexercise

import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** The exercises a session contains, in the order they were added (ADR-0004). */
interface SessionExerciseRepository {
    /** The session's exercises, ordered by position. */
    fun observeForSession(sessionId: SessionId): Flow<List<SessionExercise>>

    /**
     * The exercises of several sessions at once, ordered by position.
     *
     * History summarises a whole list of sessions (US-06); reading them one session at a time
     * would be a query per row.
     */
    fun observeForSessions(sessionIds: List<SessionId>): Flow<List<SessionExercise>>

    /** One appearance, or null if it is not there. */
    suspend fun find(id: SessionExerciseId): SessionExercise?

    /** Appends [sessionExercise]. Callers get its position from [nextPosition]. */
    suspend fun add(sessionExercise: SessionExercise)

    /**
     * Removes one appearance (US-02c). Its sets go with it via `ON DELETE CASCADE`.
     *
     * Positions are left as they are: the surviving rows keep the numbers they were performed
     * under, and the gap is closed when the list is displayed (US-02b).
     */
    suspend fun remove(id: SessionExerciseId)

    /**
     * Writes or clears the appearance's done mark (US-02d, ADR-0019).
     *
     * Null means in progress. Doing nothing when [id] is not there is correct: the mark of a
     * removed exercise is carried by US-02c's in-memory snapshot, not by this row.
     */
    suspend fun setFinishedAt(
        id: SessionExerciseId,
        finishedAt: Instant?,
    )

    /**
     * The position an exercise appended to [sessionId] should take. 1-based.
     *
     * `MAX(position) + 1` rather than a count, so removing from the middle of a session cannot
     * mint a position that a surviving row already holds.
     */
    suspend fun nextPosition(sessionId: SessionId): Int
}
