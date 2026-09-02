package com.gymtracker.core.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Queries over `sync_queue` (US-57, ADR-0043).
 *
 * Every other syncable-table repository holds a reference to this DAO too, and calls [insert]
 * inside the same `database.withTransaction` block as its own table's write — that pairing,
 * not anything in this file, is what makes a write and its outbox row atomic.
 */
@Dao
interface SyncQueueDao {
    @Insert
    suspend fun insert(entry: SyncQueueEntity)

    /** Oldest first — the order a future `SyncWorker` drains in. */
    @Query("SELECT * FROM sync_queue ORDER BY created_at ASC")
    suspend fun oldestFirst(): List<SyncQueueEntity>

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun delete(id: String)

    /** The `SyncIndicatorChip`'s eventual pending count (ADR-0043) — unused until it exists. */
    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun pendingCount(): Int
}
