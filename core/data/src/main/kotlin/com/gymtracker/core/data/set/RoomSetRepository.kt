package com.gymtracker.core.data.set

import androidx.room.withTransaction
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.sync.SyncEntityNames
import com.gymtracker.core.data.sync.SyncPayloadCodec
import com.gymtracker.core.data.sync.syncDeleteEntry
import com.gymtracker.core.data.sync.syncWriteEntry
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.set.SetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/**
 * [SetRepository] over Room.
 *
 * Every write also leaves a `sync_queue` row in the same transaction (US-57, ADR-0043) — see
 * [com.gymtracker.core.data.session.RoomSessionRepository]'s KDoc for why that needs [database]
 * and [codec] alongside [dao].
 */
class RoomSetRepository
    @Inject
    constructor(
        private val dao: SetDao,
        private val database: GymTrackerDatabase,
        private val codec: SyncPayloadCodec,
    ) : SetRepository {
        private val syncQueue get() = database.syncQueueDao()

        override fun observeForSessionExercise(sessionExerciseId: SessionExerciseId): Flow<List<ExerciseSet>> =
            dao.observeForSessionExercise(sessionExerciseId.value).map { rows -> rows.map { it.toDomain() } }

        override fun observeForSessions(sessionIds: List<SessionId>): Flow<List<ExerciseSet>> =
            dao.observeForSessions(sessionIds.map { it.value }).map { rows -> rows.map { it.toDomain() } }

        override suspend fun lastSetOf(
            exerciseId: ExerciseId,
            member: UserId,
        ): ExerciseSet? = dao.lastSetOf(exerciseId.value, member.value)?.toDomain()

        override suspend fun lastSetOfBefore(
            exerciseId: ExerciseId,
            member: UserId,
            excludingSessionId: SessionId,
        ): ExerciseSet? = dao.lastSetOfBefore(exerciseId.value, member.value, excludingSessionId.value)?.toDomain()

        override suspend fun lastSetAtInSession(sessionId: SessionId): Instant? =
            dao.lastPerformedAtInSession(sessionId.value)?.let(Instant::ofEpochMilli)

        override suspend fun nextSetIndex(sessionExerciseId: SessionExerciseId): Int =
            dao.maxSetIndex(sessionExerciseId.value) + 1

        override suspend fun add(set: ExerciseSet) {
            val entity = set.toEntity()
            val payload = codec.encode(entity)
            database.withTransaction {
                dao.insert(entity)
                syncQueue.insert(syncWriteEntry(SyncEntityNames.SETS, entity.id, payload))
            }
        }

        override suspend fun update(set: ExerciseSet) {
            val entity = set.toEntity()
            val payload = codec.encode(entity)
            database.withTransaction {
                dao.update(entity)
                syncQueue.insert(syncWriteEntry(SyncEntityNames.SETS, entity.id, payload))
            }
        }

        override suspend fun delete(id: String): ExerciseSet? =
            database
                .withTransaction {
                    val existing = dao.deleteAndReturn(id)
                    if (existing != null) syncQueue.insert(syncDeleteEntry(SyncEntityNames.SETS, id))
                    existing
                }?.toDomain()
    }
