package com.gymtracker.core.data.session

import androidx.room.withTransaction
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.sync.SyncEntityNames
import com.gymtracker.core.data.sync.SyncPayloadCodec
import com.gymtracker.core.data.sync.syncDeleteEntry
import com.gymtracker.core.data.sync.syncWriteEntry
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.SessionMetrics
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.session.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/**
 * [SessionRepository] over Room.
 *
 * Carries the same `TooManyFunctions` suppression as the interface it implements: this class is
 * that port's one implementation, so its function count *is* the port's count and cannot be
 * reduced independently of it.
 *
 * Every write also leaves a `sync_queue` row, in the same `database.withTransaction` block as
 * the write itself (US-57, ADR-0043) — [dao] alone cannot do this, since `sync_queue` is a
 * different table with its own DAO, which is why this class now also holds [database] and
 * [codec] rather than [dao] alone.
 */
@Suppress("TooManyFunctions")
class RoomSessionRepository
    @Inject
    constructor(
        private val dao: SessionDao,
        private val database: GymTrackerDatabase,
        private val codec: SyncPayloadCodec,
    ) : SessionRepository {
        private val syncQueue get() = database.syncQueueDao()

        override fun observeActiveSession(userId: UserId): Flow<WorkoutSession?> =
            dao.observeActive(userId.value).map { it?.toDomain() }

        override fun observeFinishedSessions(userId: UserId): Flow<List<WorkoutSession>> =
            dao.observeFinished(userId.value).map { rows -> rows.map { it.toDomain() } }

        override suspend fun findActiveSession(userId: UserId): WorkoutSession? =
            dao.findActive(userId.value)?.toDomain()

        override suspend fun findSession(id: SessionId): WorkoutSession? = dao.find(id.value)?.toDomain()

        override suspend fun startSession(session: WorkoutSession) {
            val entity = session.toEntity()
            val payload = codec.encode(entity)
            database.withTransaction {
                dao.insert(entity)
                syncQueue.insert(syncWriteEntry(SyncEntityNames.SESSIONS, entity.id, payload))
            }
        }

        /**
         * The same insert as [startSession] — a restored session is a row that used to exist,
         * with its own id and timestamps, so putting it back is nothing more than writing it.
         * `updated_at` becomes now, which is what M2's last-write-wins will need to see.
         *
         * Enqueues exactly like an ordinary write — US-57's own decision is that a restore gets
         * no special case; see ADR-0043's amendment.
         */
        override suspend fun restoreSession(session: WorkoutSession) {
            val entity = session.toEntity()
            val payload = codec.encode(entity)
            database.withTransaction {
                dao.insert(entity)
                syncQueue.insert(syncWriteEntry(SyncEntityNames.SESSIONS, entity.id, payload))
            }
        }

        /**
         * [SessionDao.end] only touches three columns via raw SQL, so the payload the outbox
         * needs — the row's whole current state — has to be read back afterward rather than
         * built from the parameters alone.
         */
        override suspend fun endSession(
            id: SessionId,
            endedAt: Instant,
        ) {
            val updatedAt = Instant.now().toEpochMilli()
            database.withTransaction {
                dao.end(id = id.value, endedAt = endedAt.toEpochMilli(), updatedAt = updatedAt)
                dao.find(id.value)?.let { after ->
                    syncQueue.insert(syncWriteEntry(SyncEntityNames.SESSIONS, after.id, codec.encode(after)))
                }
            }
        }

        /** The `ON DELETE CASCADE` on the child tables takes the exercises and sets with it. */
        override suspend fun deleteSession(id: SessionId) {
            database.withTransaction {
                if (dao.delete(id.value) > 0) {
                    syncQueue.insert(syncDeleteEntry(SyncEntityNames.SESSIONS, id.value))
                }
            }
        }

        /**
         * [SessionDao.clearMetricsForUser] is a bulk `WHERE`-scoped update touching anywhere
         * from zero to every one of the member's sessions, so the affected ids are read
         * *before* the update runs — [SessionDao.idsWithMetrics] matches nothing once the
         * clear itself has already blanked the columns it looks for — and each one's fresh row
         * is enqueued afterward.
         */
        override suspend fun clearMetrics(userId: UserId): Int {
            val updatedAt = Instant.now().toEpochMilli()
            return database.withTransaction {
                val affectedIds = dao.idsWithMetrics(userId.value)
                val count = dao.clearMetricsForUser(userId.value, updatedAt)
                affectedIds.forEach { id ->
                    dao.find(id)?.let { after ->
                        syncQueue.insert(syncWriteEntry(SyncEntityNames.SESSIONS, after.id, codec.encode(after)))
                    }
                }
                count
            }
        }

        override suspend fun countSessionsWithMetrics(userId: UserId): Int = dao.countWithMetrics(userId.value)

        override suspend fun saveMetrics(
            id: SessionId,
            metrics: SessionMetrics,
        ) {
            val updatedAt = Instant.now().toEpochMilli()
            database.withTransaction {
                dao.saveMetrics(
                    SessionMetricsPatch(
                        id = id.value,
                        avgHr = metrics.avgHeartRate,
                        maxHr = metrics.maxHeartRate,
                        activeKcal = metrics.activeKilocalories,
                        metricsSource = metrics.source,
                        updatedAt = updatedAt,
                    ),
                )
                dao.find(id.value)?.let { after ->
                    syncQueue.insert(syncWriteEntry(SyncEntityNames.SESSIONS, after.id, codec.encode(after)))
                }
            }
        }
    }
