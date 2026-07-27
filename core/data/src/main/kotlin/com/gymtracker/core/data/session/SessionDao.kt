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
