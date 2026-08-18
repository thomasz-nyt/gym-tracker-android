package com.gymtracker.core.data.session

import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.SessionMetrics
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.session.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/** [SessionRepository] over Room. */
class RoomSessionRepository
    @Inject
    constructor(
        private val dao: SessionDao,
    ) : SessionRepository {
        override fun observeActiveSession(userId: UserId): Flow<WorkoutSession?> =
            dao.observeActive(userId.value).map { it?.toDomain() }

        override fun observeFinishedSessions(userId: UserId): Flow<List<WorkoutSession>> =
            dao.observeFinished(userId.value).map { rows -> rows.map { it.toDomain() } }

        override suspend fun findActiveSession(userId: UserId): WorkoutSession? =
            dao.findActive(userId.value)?.toDomain()

        override suspend fun findSession(id: SessionId): WorkoutSession? = dao.find(id.value)?.toDomain()

        override suspend fun startSession(session: WorkoutSession) {
            dao.insert(session.toEntity())
        }

        /**
         * The same insert as [startSession] — a restored session is a row that used to exist,
         * with its own id and timestamps, so putting it back is nothing more than writing it.
         * `updated_at` becomes now, which is what M2's last-write-wins will need to see.
         */
        override suspend fun restoreSession(session: WorkoutSession) {
            dao.insert(session.toEntity())
        }

        override suspend fun endSession(
            id: SessionId,
            endedAt: Instant,
        ) {
            dao.end(id = id.value, endedAt = endedAt.toEpochMilli(), updatedAt = Instant.now().toEpochMilli())
        }

        /** The `ON DELETE CASCADE` on the child tables takes the exercises and sets with it. */
        override suspend fun deleteSession(id: SessionId) {
            dao.delete(id.value)
        }

        override suspend fun saveMetrics(
            id: SessionId,
            metrics: SessionMetrics,
        ) {
            dao.saveMetrics(
                SessionMetricsPatch(
                    id = id.value,
                    avgHr = metrics.avgHeartRate,
                    maxHr = metrics.maxHeartRate,
                    activeKcal = metrics.activeKilocalories,
                    metricsSource = metrics.source,
                    updatedAt = Instant.now().toEpochMilli(),
                ),
            )
        }
    }
