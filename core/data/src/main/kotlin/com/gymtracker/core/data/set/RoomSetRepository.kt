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

        override suspend fun lastSetOf(
            exerciseId: ExerciseId,
            member: UserId,
        ): ExerciseSet? = dao.lastSetOf(exerciseId.value, member.value)?.toDomain()

        override suspend fun lastSetAtInSession(sessionId: SessionId): Instant? =
            dao.lastPerformedAtInSession(sessionId.value)?.let(Instant::ofEpochMilli)

        override suspend fun nextSetIndex(sessionExerciseId: SessionExerciseId): Int =
            dao.maxSetIndex(sessionExerciseId.value) + 1

        override suspend fun add(set: ExerciseSet) {
            dao.insert(
                SetEntity(
                    id = set.id,
                    sessionExerciseId = set.sessionExerciseId.value,
                    setIndex = set.setIndex,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    rpe = set.rpe,
                    performedAt = set.performedAt.toEpochMilli(),
                    updatedAt = Instant.now().toEpochMilli(),
                    syncState = SYNC_STATE_PENDING,
                ),
            )
        }
    }
