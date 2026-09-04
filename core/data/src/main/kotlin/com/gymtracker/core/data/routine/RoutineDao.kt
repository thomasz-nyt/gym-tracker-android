package com.gymtracker.core.data.routine

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.gymtracker.core.data.session.SYNC_STATE_PENDING
import kotlinx.coroutines.flow.Flow

/** Queries over `routines` (US-29). */
@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines WHERE user_id = :userId ORDER BY position ASC")
    fun observeRoutines(userId: String): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun find(id: String): RoutineEntity?

    @Query("SELECT COALESCE(MAX(position), 0) FROM routines WHERE user_id = :userId")
    suspend fun maxPosition(userId: String): Int

    @Insert
    suspend fun insert(routine: RoutineEntity)

    /**
     * US-57: previously left `sync_state` untouched, so a renamed routine bumped `updated_at`
     * but never re-entered the outbox's `sync_state = 'PENDING'` convention every other write in
     * this codebase follows — found writing the outbox (ADR-0043's amendment). Fixed here.
     */
    @Query(
        "UPDATE routines SET name = :name, updated_at = :updatedAt, sync_state = '$SYNC_STATE_PENDING' WHERE id = :id",
    )
    suspend fun rename(
        id: String,
        name: String,
        updatedAt: Long,
    )

    /**
     * The items go with it via `ON DELETE CASCADE`. No session is touched (ADR-0020). Returns
     * the number of rows actually removed (0 or 1) — US-57's outbox enqueues a delete only when
     * this deleted something.
     */
    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun delete(id: String): Int

    /** Every routine the member has, in order (US-40, ADR-0034) — [observeRoutines] read once. */
    @Query("SELECT * FROM routines WHERE user_id = :userId ORDER BY position ASC")
    suspend fun allForUser(userId: String): List<RoutineEntity>

    /** US-41's replace-all: `routine_items` cascades from this delete, same as `delete(id)` above. */
    @Query("DELETE FROM routines WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Insert
    suspend fun insertAll(routines: List<RoutineEntity>)

    /**
     * US-58's re-key: every routine [oldUserId] owns becomes [newUserId]'s, in one statement —
     * `routine_items` needs no equivalent, since it carries no `user_id` of its own and is
     * reached through the routine that just moved. See `SessionDao.reassignOwner`'s KDoc for why
     * `AccountAdoption` reads [allForUser] before calling this rather than after.
     */
    @Query(
        "UPDATE routines SET user_id = :newUserId, updated_at = :updatedAt, " +
            "sync_state = '$SYNC_STATE_PENDING' WHERE user_id = :oldUserId",
    )
    suspend fun reassignOwner(
        oldUserId: String,
        newUserId: String,
        updatedAt: Long,
    ): Int
}

/**
 * Queries over `routine_items` — a separate table, so a separate DAO.
 *
 * US-57's [find] pushed this one query past detekt's interface threshold. Suppressed for the
 * same reason `SetDao` and `SessionDao` already are: a DAO is a query surface, not a class with
 * behaviour, and this one has exactly one responsibility — reading and writing `routine_items`.
 */
@Dao
@Suppress("TooManyFunctions")
interface RoutineItemDao {
    @Query("SELECT * FROM routine_items WHERE routine_id = :routineId ORDER BY position ASC")
    fun observeItems(routineId: String): Flow<List<RoutineItemEntity>>

    @Query("SELECT * FROM routine_items WHERE routine_id = :routineId ORDER BY position ASC")
    suspend fun itemsOf(routineId: String): List<RoutineItemEntity>

    /** One row by id — US-57's outbox re-reads a row after [setPosition] to build its payload. */
    @Query("SELECT * FROM routine_items WHERE id = :id")
    suspend fun find(id: String): RoutineItemEntity?

    @Query("SELECT COALESCE(MAX(position), 0) FROM routine_items WHERE routine_id = :routineId")
    suspend fun maxPosition(routineId: String): Int

    @Insert
    suspend fun insert(item: RoutineItemEntity)

    /** US-30: matched by primary key, so this only ever touches the row [item] names. */
    @Update
    suspend fun update(item: RoutineItemEntity)

    /**
     * Returns the number of rows actually removed (0 or 1) — US-57's outbox enqueues a delete
     * only when this deleted something.
     */
    @Query("DELETE FROM routine_items WHERE id = :id")
    suspend fun delete(id: String): Int

    /**
     * US-57: previously left `sync_state` untouched, the same gap [RoutineDao.rename] had —
     * found and fixed the same way (ADR-0043's amendment).
     */
    @Query(
        "UPDATE routine_items SET position = :position, updated_at = :updatedAt, " +
            "sync_state = '$SYNC_STATE_PENDING' WHERE id = :id",
    )
    suspend fun setPosition(
        id: String,
        position: Int,
        updatedAt: Long,
    )

    /**
     * Every movement across any of the member's routines (US-40, ADR-0034), reached through
     * `routines` since `routine_items` carries no `user_id` of its own.
     */
    @Query(
        """
        SELECT ri.* FROM routine_items ri
        JOIN routines r ON r.id = ri.routine_id
        WHERE r.user_id = :userId
        """,
    )
    suspend fun allForUser(userId: String): List<RoutineItemEntity>

    @Insert
    suspend fun insertAll(items: List<RoutineItemEntity>)

    /**
     * Applies a whole reordering in one transaction.
     *
     * A drag is a statement about every row's position at once, so writing them one at a time
     * would leave the table briefly holding two rows at the same position — which is exactly
     * what an observer of [observeItems] would render.
     */
    @Transaction
    suspend fun setPositions(
        positions: Map<String, Int>,
        updatedAt: Long,
    ) {
        positions.forEach { (id, position) -> setPosition(id, position, updatedAt) }
    }
}
