package com.gymtracker.core.data.sessionexercise

import androidx.room.withTransaction
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.sync.SyncEntityNames
import com.gymtracker.core.data.sync.SyncPayloadCodec
import com.gymtracker.core.data.sync.syncDeleteEntry
import com.gymtracker.core.data.sync.syncWriteEntry
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * [SessionExerciseRepository] over Room.
 *
 * Every write also leaves a `sync_queue` row in the same transaction (US-57, ADR-0043) — see
 * [com.gymtracker.core.data.session.RoomSessionRepository]'s KDoc for why that needs [database]
 * and [codec] alongside [dao].
 */
class RoomSessionExerciseRepository
    @Inject
    constructor(
        private val dao: SessionExerciseDao,
        private val database: GymTrackerDatabase,
        private val codec: SyncPayloadCodec,
    ) : SessionExerciseRepository {
        private val syncQueue get() = database.syncQueueDao()

        override fun observeForSession(sessionId: SessionId): Flow<List<SessionExercise>> =
            dao.observeForSession(sessionId.value).map { rows -> rows.map { it.toDomain() } }

        override fun observeForSessions(sessionIds: List<SessionId>): Flow<List<SessionExercise>> =
            dao.observeForSessions(sessionIds.map { it.value }).map { rows -> rows.map { it.toDomain() } }

        override suspend fun find(id: SessionExerciseId): SessionExercise? = dao.find(id.value)?.toDomain()

        override suspend fun add(sessionExercise: SessionExercise) {
            val entity = sessionExercise.toEntity()
            val payload = codec.encode(entity)
            database.withTransaction {
                dao.insert(entity)
                syncQueue.insert(syncWriteEntry(SyncEntityNames.SESSION_EXERCISES, entity.id, payload))
            }
        }

        override suspend fun remove(id: SessionExerciseId) {
            database.withTransaction {
                if (dao.delete(id.value) > 0) {
                    syncQueue.insert(syncDeleteEntry(SyncEntityNames.SESSION_EXERCISES, id.value))
                }
            }
        }

        override suspend fun nextPosition(sessionId: SessionId): Int = dao.maxPosition(sessionId.value) + 1
    }
