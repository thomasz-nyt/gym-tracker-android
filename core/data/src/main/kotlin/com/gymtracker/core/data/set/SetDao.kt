package com.gymtracker.core.data.set

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Queries over `sets`. */
@Dao
interface SetDao {
    @Query("SELECT * FROM sets WHERE session_exercise_id = :sessionExerciseId ORDER BY set_index ASC")
    fun observeForSessionExercise(sessionExerciseId: String): Flow<List<SetEntity>>

    /**
     * Every set in any of these sessions, for the volume on each history row (US-06).
     *
     * Reaches the session through `session_exercises`, the same join ADR-0004 chose over
     * denormalising a `session_id` onto `sets`.
     */
    @Query(
        """
        SELECT s.* FROM sets s
        JOIN session_exercises se ON se.id = s.session_exercise_id
        WHERE se.session_id IN (:sessionIds)
        ORDER BY s.set_index ASC
        """,
    )
    fun observeForSessions(sessionIds: List<String>): Flow<List<SetEntity>>

    /**
     * The member's most recent set of an exercise, in any session — the US-03 prefill.
     *
     * This is the join ADR-0004 chose over a denormalised `exercise_id` on `sets`: it reaches
     * the exercise through `session_exercises`, backed by the index on that column.
     */
    @Query(
        """
        SELECT s.* FROM sets s
        JOIN session_exercises se ON se.id = s.session_exercise_id
        JOIN sessions ss ON ss.id = se.session_id
        WHERE se.exercise_id = :exerciseId AND ss.user_id = :userId
        ORDER BY s.performed_at DESC
        LIMIT 1
        """,
    )
    suspend fun lastSetOf(
        exerciseId: String,
        userId: String,
    ): SetEntity?

    /** The most recent set time in a session, which is what US-01 calls "last activity". */
    @Query(
        """
        SELECT MAX(s.performed_at) FROM sets s
        JOIN session_exercises se ON se.id = s.session_exercise_id
        WHERE se.session_id = :sessionId
        """,
    )
    suspend fun lastPerformedAtInSession(sessionId: String): Long?

    @Query("SELECT COALESCE(MAX(set_index), 0) FROM sets WHERE session_exercise_id = :sessionExerciseId")
    suspend fun maxSetIndex(sessionExerciseId: String): Int

    @Insert
    suspend fun insert(set: SetEntity)
}
