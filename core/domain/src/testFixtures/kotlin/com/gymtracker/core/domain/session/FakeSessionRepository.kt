package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.SessionMetrics
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Hand-written fake, per `specs/testing-strategy.md`: mocked repositories test that a
 * method was called; fakes test that the behaviour is right.
 *
 * @param cascade stands in for the `ON DELETE CASCADE` on `session_exercises` and `sets`.
 *   Deleting a session in Room takes its children with it; a test that cares about that has
 *   to say so here, because nothing in the domain does it explicitly (ADR-0012).
 */
class FakeSessionRepository(
    initial: List<WorkoutSession> = emptyList(),
    private val cascade: (SessionId) -> Unit = {},
) : SessionRepository {
    private val state = MutableStateFlow(initial)

    val sessions: List<WorkoutSession> get() = state.value

    override fun observeActiveSession(userId: UserId): Flow<WorkoutSession?> =
        state.map { all -> all.activeFor(userId) }

    override fun observeFinishedSessions(userId: UserId): Flow<List<WorkoutSession>> =
        state.map { all ->
            all
                .filter { it.userId == userId && it.endedAt != null }
                .sortedByDescending { it.startedAt }
        }

    override suspend fun findActiveSession(userId: UserId): WorkoutSession? = state.value.activeFor(userId)

    override suspend fun findSession(id: SessionId): WorkoutSession? = state.value.firstOrNull { it.id == id }

    override suspend fun startSession(session: WorkoutSession) {
        state.value = state.value + session
    }

    override suspend fun restoreSession(session: WorkoutSession) {
        state.value = state.value + session
    }

    override suspend fun endSession(
        id: SessionId,
        endedAt: Instant,
    ) {
        state.value = state.value.map { if (it.id == id) it.copy(endedAt = endedAt) else it }
    }

    override suspend fun deleteSession(id: SessionId) {
        state.value = state.value.filterNot { it.id == id }
        cascade(id)
    }

    override suspend fun saveMetrics(
        id: SessionId,
        metrics: SessionMetrics,
    ) {
        state.value = state.value.map { if (it.id == id) it.copy(metrics = metrics) else it }
    }

    override suspend fun clearMetrics(userId: UserId): Int {
        val target = state.value.filter { it.userId == userId && it.metrics != null }
        state.value = state.value.map { if (it in target) it.copy(metrics = null) else it }
        return target.size
    }

    override suspend fun countSessionsWithMetrics(userId: UserId): Int =
        state.value.count { it.userId == userId && it.metrics != null }

    private fun List<WorkoutSession>.activeFor(userId: UserId): WorkoutSession? =
        lastOrNull {
            it.userId == userId &&
                it.endedAt == null
        }
}
