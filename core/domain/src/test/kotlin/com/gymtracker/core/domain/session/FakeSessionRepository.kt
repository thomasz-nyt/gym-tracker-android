package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Hand-written fake, per `specs/testing-strategy.md`: mocked repositories test that a
 * method was called; fakes test that the behaviour is right.
 */
class FakeSessionRepository(
    initial: List<WorkoutSession> = emptyList(),
) : SessionRepository {
    private val state = MutableStateFlow(initial)

    val sessions: List<WorkoutSession> get() = state.value

    override fun observeActiveSession(userId: UserId): Flow<WorkoutSession?> =
        state.map { all -> all.activeFor(userId) }

    override suspend fun findActiveSession(userId: UserId): WorkoutSession? = state.value.activeFor(userId)

    override suspend fun startSession(session: WorkoutSession) {
        state.value = state.value + session
    }

    override suspend fun endSession(
        id: SessionId,
        endedAt: Instant,
    ) {
        state.value = state.value.map { if (it.id == id) it.copy(endedAt = endedAt) else it }
    }

    override suspend fun discardSession(id: SessionId) {
        state.value = state.value.filterNot { it.id == id }
    }

    private fun List<WorkoutSession>.activeFor(userId: UserId): WorkoutSession? =
        lastOrNull {
            it.userId == userId &&
                it.endedAt == null
        }
}
