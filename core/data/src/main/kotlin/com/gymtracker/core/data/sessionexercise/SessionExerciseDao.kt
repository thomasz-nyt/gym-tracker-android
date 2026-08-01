package com.gymtracker.core.data.sessionexercise

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Queries over `session_exercises`. */
@Dao
interface SessionExerciseDao {
    @Query("SELECT * FROM session_exercises WHERE session_id = :sessionId ORDER BY position ASC")
    fun observeForSession(sessionId: String): Flow<List<SessionExerciseEntity>>

    /** Several sessions at once, so history is one query rather than one per row (US-06). */
    @Query("SELECT * FROM session_exercises WHERE session_id IN (:sessionIds) ORDER BY position ASC")
    fun observeForSessions(sessionIds: List<String>): Flow<List<SessionExerciseEntity>>

    @Query("SELECT COALESCE(MAX(position), 0) FROM session_exercises WHERE session_id = :sessionId")
    suspend fun maxPosition(sessionId: String): Int

    @Insert
    suspend fun insert(sessionExercise: SessionExerciseEntity)
}
