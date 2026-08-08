package com.gymtracker.core.data.set

import com.gymtracker.core.data.session.SYNC_STATE_PENDING
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

/** [SetRepository] over Room. */
class RoomSetRepository
    @Inject
    constructor(
        private val dao: SetDao,
    ) : SetRepository {
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
            dao.insert(set.toEntity())
        }

        override suspend fun update(set: ExerciseSet) {
            dao.update(set.toEntity())
        }

        override suspend fun delete(id: String): ExerciseSet? = dao.deleteAndReturn(id)?.toDomain()

        /** Every write stamps `updated_at` and marks the row pending, exactly as [add] does. */
        private fun ExerciseSet.toEntity() =
            SetEntity(
                id = id,
                sessionExerciseId = sessionExerciseId.value,
                setIndex = setIndex,
                weightKg = weightKg,
                reps = reps,
                rpe = rpe,
                performedAt = performedAt.toEpochMilli(),
                updatedAt = Instant.now().toEpochMilli(),
                syncState = SYNC_STATE_PENDING,
            )
    }
