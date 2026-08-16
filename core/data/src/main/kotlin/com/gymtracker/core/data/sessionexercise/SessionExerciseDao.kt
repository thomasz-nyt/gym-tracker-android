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

    @Query("SELECT * FROM session_exercises WHERE id = :id")
    suspend fun find(id: String): SessionExerciseEntity?

    @Query("SELECT COALESCE(MAX(position), 0) FROM session_exercises WHERE session_id = :sessionId")
    suspend fun maxPosition(sessionId: String): Int

    @Insert
    suspend fun insert(sessionExercise: SessionExerciseEntity)

    /** US-02c. The row's sets go with it via `ON DELETE CASCADE` on `sets`. */
    @Query("DELETE FROM session_exercises WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Every appearance across any of the member's sessions (US-40, ADR-0034).
     *
     * `session_exercises` carries no `user_id` of its own, so this reaches it through
     * `sessions` — the same join [com.gymtracker.core.data.set.SetDao.lastSetOf] uses to reach
     * an exercise, per ADR-0004.
     */
    @Query(
        """
        SELECT se.* FROM session_exercises se
        JOIN sessions s ON s.id = se.session_id
        WHERE s.user_id = :userId
        """,
    )
    suspend fun allForUser(userId: String): List<SessionExerciseEntity>

    @Insert
    suspend fun insertAll(sessionExercises: List<SessionExerciseEntity>)
}
