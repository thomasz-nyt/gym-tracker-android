package com.gymtracker.core.data.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * The `sync_queue` table from `data-model.md` (US-57, ADR-0043) — a durable, ordered record of
 * every write to a syncable table, populated by an insert alongside that write's own Room
 * transaction. No foreign key to any syncable table: a queue row must be able to outlive the
 * row it describes, which is exactly the case a delete needs (ADR-0043's amendment).
 *
 * [payloadJson] is null for a [SYNC_OP_DELETE] row — there is nothing left to send but the id.
 * For a [SYNC_OP_WRITE] row it holds the whole current state of the row, encoded by
 * [SyncPayloadCodec] — row-shaped, not domain-shaped like a backup file; see ADR-0043's
 * amendment for why `BackupCodec` does not fit here.
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    /** One of [SyncEntityNames] — the table the row describes. */
    @ColumnInfo(name = "entity") val entity: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    /** [SYNC_OP_WRITE] or [SYNC_OP_DELETE]. */
    @ColumnInfo(name = "op") val op: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Read and bumped only once a `SyncWorker` exists to retry a failed drain; always 0 here. */
    @ColumnInfo(name = "attempts") val attempts: Int = 0,
)

/**
 * A row was inserted or updated. [SyncQueueEntity.payloadJson] carries the full row as it
 * stands right now — last-write-wins is a whole-row replace, per ADR-0043, so there is no
 * separate "UPDATE" op distinguishing a change from a first write; both leave the same shape
 * of row here.
 */
const val SYNC_OP_WRITE = "WRITE"

/** A row was deleted. [SyncQueueEntity.payloadJson] is null. */
const val SYNC_OP_DELETE = "DELETE"

/** The `entity` values [SyncQueueEntity] uses — the five syncable tables from `data-model.md`. */
object SyncEntityNames {
    const val SESSIONS = "sessions"
    const val SESSION_EXERCISES = "session_exercises"
    const val SETS = "sets"
    const val ROUTINES = "routines"
    const val ROUTINE_ITEMS = "routine_items"
}

/** Builds a [SYNC_OP_WRITE] row for [entity]/[entityId], carrying [payloadJson] as its snapshot. */
internal fun syncWriteEntry(
    entity: String,
    entityId: String,
    payloadJson: String,
): SyncQueueEntity =
    SyncQueueEntity(
        id = UUID.randomUUID().toString(),
        entity = entity,
        entityId = entityId,
        op = SYNC_OP_WRITE,
        payloadJson = payloadJson,
        createdAt = Instant.now().toEpochMilli(),
    )

/** Builds a [SYNC_OP_DELETE] row for [entity]/[entityId] — no payload, the row is already gone. */
internal fun syncDeleteEntry(
    entity: String,
    entityId: String,
): SyncQueueEntity =
    SyncQueueEntity(
        id = UUID.randomUUID().toString(),
        entity = entity,
        entityId = entityId,
        op = SYNC_OP_DELETE,
        payloadJson = null,
        createdAt = Instant.now().toEpochMilli(),
    )
