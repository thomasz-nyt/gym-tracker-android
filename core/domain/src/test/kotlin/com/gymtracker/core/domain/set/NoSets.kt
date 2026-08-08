package com.gymtracker.core.domain.set

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

/** Every method throws or returns nothing, so a test fake only overrides what it uses. */
internal open class NoSets : SetRepository {
    override fun observeForSessionExercise(sessionExerciseId: SessionExerciseId): Flow<List<ExerciseSet>> =
        flowOf(emptyList())

    override fun observeForSessions(sessionIds: List<SessionId>): Flow<List<ExerciseSet>> = flowOf(emptyList())

    override suspend fun lastSetOf(
        exerciseId: ExerciseId,
        member: UserId,
    ): ExerciseSet? = null

    override suspend fun lastSetAtInSession(sessionId: SessionId): Instant? = null

    override suspend fun nextSetIndex(sessionExerciseId: SessionExerciseId): Int = 1

    override suspend fun add(set: ExerciseSet) = Unit

    override suspend fun update(set: ExerciseSet) = Unit

    override suspend fun delete(id: String): ExerciseSet? = null
}
