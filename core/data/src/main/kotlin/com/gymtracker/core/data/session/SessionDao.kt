package com.gymtracker.core.data.session

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Queries over the `sessions` table. */
@Dao
// One query past detekt's interface threshold. Suppressed rather than split for the same
// reason `SetDao` is: a DAO is a query surface, not a class with behaviour, and this one has
// exactly one responsibility — reading and writing `sessions`.
@Suppress("TooManyFunctions")
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

    /**
     * US-22, a Room partial-entity update: only the columns [SessionMetricsPatch] names are
     * touched, keyed on its `id` — everything else about the row, `sync_state` included, is
     * untouched. Chosen over a scalar-parameter `@Query` to stay under detekt's
     * `LongParameterList` without inventing an unrelated grouping of the same six values.
     */
    @Update(entity = SessionEntity::class)
    suspend fun saveMetrics(patch: SessionMetricsPatch)

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

/**
 * The six columns [SessionDao.saveMetrics] writes. Not `@Entity`-annotated — Room's partial
 * update matches by `@ColumnInfo` name against [SessionEntity], not by declaring a second table.
 */
data class SessionMetricsPatch(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "avg_hr") val avgHr: Int?,
    @ColumnInfo(name = "max_hr") val maxHr: Int?,
    @ColumnInfo(name = "active_kcal") val activeKcal: Int?,
    @ColumnInfo(name = "metrics_source") val metricsSource: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "sync_state") val syncState: String = SYNC_STATE_PENDING,
)
