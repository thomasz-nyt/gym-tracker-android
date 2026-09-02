package com.gymtracker.core.data.routine

import androidx.room.withTransaction
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.sync.SyncEntityNames
import com.gymtracker.core.data.sync.SyncPayloadCodec
import com.gymtracker.core.data.sync.syncDeleteEntry
import com.gymtracker.core.data.sync.syncWriteEntry
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.routine.RoutineItemRepository
import com.gymtracker.core.domain.routine.RoutineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/**
 * [RoutineRepository] over Room.
 *
 * Every write also leaves a `sync_queue` row in the same transaction (US-57, ADR-0043) — see
 * [com.gymtracker.core.data.session.RoomSessionRepository]'s KDoc for why that needs [database]
 * and [codec] alongside [dao].
 */
class RoomRoutineRepository
    @Inject
    constructor(
        private val dao: RoutineDao,
        private val database: GymTrackerDatabase,
        private val codec: SyncPayloadCodec,
    ) : RoutineRepository {
        private val syncQueue get() = database.syncQueueDao()

        override fun observeRoutines(userId: UserId): Flow<List<Routine>> =
            dao.observeRoutines(userId.value).map { rows -> rows.map { it.toDomain() } }

        override suspend fun find(id: RoutineId): Routine? = dao.find(id.value)?.toDomain()

        override suspend fun add(routine: Routine) {
            val entity = routine.toEntity()
            val payload = codec.encode(entity)
            database.withTransaction {
                dao.insert(entity)
                syncQueue.insert(syncWriteEntry(SyncEntityNames.ROUTINES, entity.id, payload))
            }
        }

        /**
         * [RoutineDao.rename] only touches `name`/`updated_at`/`sync_state` via raw SQL, so the
         * outbox re-reads the row afterward to build a full-row payload — the same pattern
         * [com.gymtracker.core.data.session.RoomSessionRepository.endSession] uses.
         */
        override suspend fun rename(
            id: RoutineId,
            name: String,
        ) {
            val updatedAt = Instant.now().toEpochMilli()
            database.withTransaction {
                dao.rename(id.value, name, updatedAt)
                dao.find(id.value)?.let { after ->
                    syncQueue.insert(syncWriteEntry(SyncEntityNames.ROUTINES, after.id, codec.encode(after)))
                }
            }
        }

        override suspend fun delete(id: RoutineId) {
            database.withTransaction {
                if (dao.delete(id.value) > 0) {
                    syncQueue.insert(syncDeleteEntry(SyncEntityNames.ROUTINES, id.value))
                }
            }
        }

        override suspend fun nextRoutinePosition(userId: UserId): Int = dao.maxPosition(userId.value) + 1
    }

/**
 * [RoutineItemRepository] over Room.
 *
 * Every write also leaves a `sync_queue` row in the same transaction (US-57, ADR-0043) — see
 * [com.gymtracker.core.data.session.RoomSessionRepository]'s KDoc for why that needs [database]
 * and [codec] alongside [dao].
 */
class RoomRoutineItemRepository
    @Inject
    constructor(
        private val dao: RoutineItemDao,
        private val database: GymTrackerDatabase,
        private val codec: SyncPayloadCodec,
    ) : RoutineItemRepository {
        private val syncQueue get() = database.syncQueueDao()

        override fun observeItems(routineId: RoutineId): Flow<List<RoutineItem>> =
            dao.observeItems(routineId.value).map { rows -> rows.map { it.toDomain() } }

        override suspend fun itemsOf(routineId: RoutineId): List<RoutineItem> =
            dao.itemsOf(routineId.value).map { it.toDomain() }

        override suspend fun addItem(item: RoutineItem) {
            val entity = item.toEntity()
            val payload = codec.encode(entity)
            database.withTransaction {
                dao.insert(entity)
                syncQueue.insert(syncWriteEntry(SyncEntityNames.ROUTINE_ITEMS, entity.id, payload))
            }
        }

        override suspend fun updateItem(item: RoutineItem) {
            val entity = item.toEntity()
            val payload = codec.encode(entity)
            database.withTransaction {
                dao.update(entity)
                syncQueue.insert(syncWriteEntry(SyncEntityNames.ROUTINE_ITEMS, entity.id, payload))
            }
        }

        override suspend fun removeItem(id: RoutineItemId) {
            database.withTransaction {
                if (dao.delete(id.value) > 0) {
                    syncQueue.insert(syncDeleteEntry(SyncEntityNames.ROUTINE_ITEMS, id.value))
                }
            }
        }

        override suspend fun nextItemPosition(routineId: RoutineId): Int = dao.maxPosition(routineId.value) + 1

        /**
         * A drag can move several items at once; each one that actually changed re-enters the
         * outbox with its own fresh row, the same re-read pattern raw-SQL writes elsewhere in
         * this file use, since [RoutineItemDao.setPositions] only ever touches `position`,
         * `updated_at` and `sync_state` via SQL, never the payload the outbox needs.
         */
        override suspend fun setItemPositions(positions: Map<RoutineItemId, Int>) {
            val updatedAt = Instant.now().toEpochMilli()
            database.withTransaction {
                dao.setPositions(
                    positions.entries.associate { (id, position) -> id.value to position },
                    updatedAt,
                )
                positions.keys.forEach { id ->
                    dao.find(id.value)?.let { after ->
                        syncQueue.insert(syncWriteEntry(SyncEntityNames.ROUTINE_ITEMS, after.id, codec.encode(after)))
                    }
                }
            }
        }
    }
