package com.gymtracker.core.data.sessionexercise

import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** [SessionExerciseRepository] over Room. */
class RoomSessionExerciseRepository
    @Inject
    constructor(
        private val dao: SessionExerciseDao,
    ) : SessionExerciseRepository {
        override fun observeForSession(sessionId: SessionId): Flow<List<SessionExercise>> =
            dao.observeForSession(sessionId.value).map { rows -> rows.map { it.toDomain() } }

        override fun observeForSessions(sessionIds: List<SessionId>): Flow<List<SessionExercise>> =
            dao.observeForSessions(sessionIds.map { it.value }).map { rows -> rows.map { it.toDomain() } }

        override suspend fun find(id: SessionExerciseId): SessionExercise? = dao.find(id.value)?.toDomain()

        override suspend fun add(sessionExercise: SessionExercise) {
            dao.insert(sessionExercise.toEntity())
        }

        override suspend fun remove(id: SessionExerciseId) {
            dao.delete(id.value)
        }

        override suspend fun nextPosition(sessionId: SessionId): Int = dao.maxPosition(sessionId.value) + 1
    }
