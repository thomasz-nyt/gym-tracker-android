package com.gymtracker.core.data.session

import com.gymtracker.core.domain.model.SessionId
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

        override suspend fun findActiveSession(userId: UserId): WorkoutSession? =
            dao.findActive(userId.value)?.toDomain()

        override suspend fun startSession(session: WorkoutSession) {
            dao.insert(session.toEntity())
        }

        override suspend fun endSession(
            id: SessionId,
            endedAt: Instant,
        ) {
            dao.end(id = id.value, endedAt = endedAt.toEpochMilli(), updatedAt = Instant.now().toEpochMilli())
        }

        override suspend fun discardSession(id: SessionId) {
            dao.delete(id.value)
        }

        /** Any session by id, ended or not. Used by tests and by history in US-06. */
        suspend fun findSession(id: SessionId): WorkoutSession? = dao.find(id.value)?.toDomain()
    }
