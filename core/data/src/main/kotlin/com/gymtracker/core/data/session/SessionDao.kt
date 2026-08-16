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

    /**
     * History (US-06): finished sessions only, newest first. Backed by the
     * `sessions(user_id, started_at)` index from `data-model.md`.
     */
    @Query(
        "SELECT * FROM sessions WHERE user_id = :userId AND ended_at IS NOT NULL " +
            "ORDER BY started_at DESC",
    )
    fun observeFinished(userId: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun find(id: String): SessionEntity?

    @Insert
    suspend fun insert(session: SessionEntity)

    /**
     * Every session the member has, active or finished (US-40, ADR-0034).
     *
     * Unlike [observeFinished], the session in progress is **included** — a backup is a
     * complete copy of what the member has logged, not history in US-06's sense.
     */
    @Query("SELECT * FROM sessions WHERE user_id = :userId")
    suspend fun allForUser(userId: String): List<SessionEntity>

    /** US-41's replace-all: everything else the member owns cascades from these two deletes. */
    @Query("DELETE FROM sessions WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Insert
    suspend fun insertAll(sessions: List<SessionEntity>)

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
