package com.gymtracker.core.data.set

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Queries over `sets`. */
@Dao
// One query past detekt's interface threshold. Suppressed rather than split because a DAO is a
// query surface, not a class with behaviour: the rule exists to catch objects that have grown too
// many responsibilities, and this has exactly one — reading and writing `sets`. Splitting it would
// put two DAOs on one table and make the next reader hunt for which half a query lives in.
@Suppress("TooManyFunctions")
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

    /**
     * The member's most recent set of an exercise, from a session other than the one being
     * excluded — the ADR-0023 rest-panel comparison, which must never compare a set to the one
     * that was just logged in the same session.
     */
    @Query(
        """
        SELECT s.* FROM sets s
        JOIN session_exercises se ON se.id = s.session_exercise_id
        JOIN sessions ss ON ss.id = se.session_id
        WHERE se.exercise_id = :exerciseId AND ss.user_id = :userId AND se.session_id != :excludingSessionId
        ORDER BY s.performed_at DESC
        LIMIT 1
        """,
    )
    suspend fun lastSetOfBefore(
        exerciseId: String,
        userId: String,
        excludingSessionId: String,
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

    /**
     * Every set belonging to any of the member's sessions (US-40, ADR-0034) — reached through
     * `session_exercises` and `sessions` the same way [lastSetOf] reaches an exercise, since
     * `sets` carries neither a `user_id` nor an `exercise_id` of its own (ADR-0004).
     */
    @Query(
        """
        SELECT s.* FROM sets s
        JOIN session_exercises se ON se.id = s.session_exercise_id
        JOIN sessions ss ON ss.id = se.session_id
        WHERE ss.user_id = :userId
        """,
    )
    suspend fun allForUser(userId: String): List<SetEntity>

    @Insert
    suspend fun insert(set: SetEntity)

    /** One set by id, or null — used to snapshot a row before [deleteById] takes it (US-04). */
    @Query("SELECT * FROM sets WHERE id = :id")
    suspend fun find(id: String): SetEntity?

    /** Overwrites every mutable column of an existing row, matched by primary key (US-04). */
    @Update
    suspend fun update(set: SetEntity)

    @Query("DELETE FROM sets WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Deletes one set, returning the row as it was just before, or null if there was none —
     * the snapshot [com.gymtracker.core.domain.set.DeleteSet] needs for undo (US-04).
     *
     * A default method rather than two calls from the repository, so the read-then-delete is
     * one Room transaction instead of two round trips that could race a concurrent write.
     */
    @Transaction
    suspend fun deleteAndReturn(id: String): SetEntity? {
        val existing = find(id) ?: return null
        deleteById(id)
        return existing
    }
}
