package com.gymtracker.core.domain.sessionexercise

import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionId
import kotlinx.coroutines.flow.Flow

/** The exercises a session contains, in the order they were added (ADR-0004). */
interface SessionExerciseRepository {
    /** The session's exercises, ordered by position. */
    fun observeForSession(sessionId: SessionId): Flow<List<SessionExercise>>

    /** Appends [sessionExercise]. Callers get its position from [nextPosition]. */
    suspend fun add(sessionExercise: SessionExercise)

    /** The position an exercise appended to [sessionId] should take. 1-based. */
    suspend fun nextPosition(sessionId: SessionId): Int
}
