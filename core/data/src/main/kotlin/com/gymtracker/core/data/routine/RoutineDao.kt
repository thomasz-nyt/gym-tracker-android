package com.gymtracker.core.data.routine

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

    @Query("UPDATE routines SET name = :name, updated_at = :updatedAt WHERE id = :id")
    suspend fun rename(
        id: String,
        name: String,
        updatedAt: Long,
    )

    /** The items go with it via `ON DELETE CASCADE`. No session is touched (ADR-0020). */
    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun delete(id: String)
}

/** Queries over `routine_items` — a separate table, so a separate DAO. */
@Dao
interface RoutineItemDao {
    @Query("SELECT * FROM routine_items WHERE routine_id = :routineId ORDER BY position ASC")
    fun observeItems(routineId: String): Flow<List<RoutineItemEntity>>

    @Query("SELECT * FROM routine_items WHERE routine_id = :routineId ORDER BY position ASC")
    suspend fun itemsOf(routineId: String): List<RoutineItemEntity>

    @Query("SELECT COALESCE(MAX(position), 0) FROM routine_items WHERE routine_id = :routineId")
    suspend fun maxPosition(routineId: String): Int

    @Insert
    suspend fun insert(item: RoutineItemEntity)

    /** US-30: matched by primary key, so this only ever touches the row [item] names. */
    @Update
    suspend fun update(item: RoutineItemEntity)

    @Query("DELETE FROM routine_items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE routine_items SET position = :position, updated_at = :updatedAt WHERE id = :id")
    suspend fun setPosition(
        id: String,
        position: Int,
        updatedAt: Long,
    )

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
