package com.gymtracker.core.data.session

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Queries over the `sessions` table. */
@Dao
interface SessionDao {
    @Query(ACTIVE_SESSION)
    fun observeActive(userId: String): Flow<SessionEntity?>

    @Query(ACTIVE_SESSION)
    suspend fun findActive(userId: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun find(id: String): SessionEntity?

    @Insert
    suspend fun insert(session: SessionEntity)

    @Query(
        "UPDATE sessions SET ended_at = :endedAt, updated_at = :updatedAt, " +
            "sync_state = '$SYNC_STATE_PENDING' WHERE id = :id",
    )
    suspend fun end(
        id: String,
        endedAt: Long,
        updatedAt: Long,
    )

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Finished sessions, newest first, with their counts and volume (US-06).
     *
     * The aggregates are computed here rather than by loading every set into memory. Volume
     * filters on `weight_kg IS NOT NULL`, so bodyweight sets contribute nothing and a session
     * with none weighted gets a null total — which is what the UI shows as a dash.
     *
     * Backed by the `sessions(user_id, started_at)` index.
     */
    @Query(
        """
        SELECT
            s.id AS id,
            s.started_at AS started_at,
            s.ended_at AS ended_at,
            (SELECT COUNT(*) FROM session_exercises se WHERE se.session_id = s.id) AS exercise_count,
            (SELECT COUNT(*) FROM sets st
                JOIN session_exercises se ON se.id = st.session_exercise_id
                WHERE se.session_id = s.id) AS set_count,
            (SELECT SUM(st.weight_kg * st.reps) FROM sets st
                JOIN session_exercises se ON se.id = st.session_exercise_id
                WHERE se.session_id = s.id AND st.weight_kg IS NOT NULL) AS volume_kg
        FROM sessions s
        WHERE s.user_id = :userId AND s.ended_at IS NOT NULL
        ORDER BY s.started_at DESC
        """,
    )
    fun observeHistory(userId: String): Flow<List<SessionSummaryRow>>

    private companion object {
        /**
         * The member's active session. "Only one active session per member" is enforced by the
         * StartSession use case rather than a unique index, because Room cannot express a
         * partial index (`WHERE ended_at IS NULL`). Ordering makes the query total anyway: if
         * the invariant were ever broken, the newest session is the one the member is in.
         */
        const val ACTIVE_SESSION =
            "SELECT * FROM sessions WHERE user_id = :userId AND ended_at IS NULL " +
                "ORDER BY started_at DESC LIMIT 1"
    }
}
