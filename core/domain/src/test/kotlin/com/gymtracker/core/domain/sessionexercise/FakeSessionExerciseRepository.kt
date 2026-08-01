package com.gymtracker.core.domain.sessionexercise

import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Hand-written fake, per `specs/testing-strategy.md`: mocked repositories test that a
 * method was called; fakes test that the behaviour is right.
 */
class FakeSessionExerciseRepository(
    initial: List<SessionExercise> = emptyList(),
) : SessionExerciseRepository {
    private val state = MutableStateFlow(initial)

    val all: List<SessionExercise> get() = state.value

    /** The rows of one session, as the database would return them. */
    fun forSession(sessionId: SessionId): List<SessionExercise> =
        state.value.filter { it.sessionId == sessionId }.sortedBy { it.position }

    /**
     * Deletes a session's exercises, standing in for the `ON DELETE CASCADE` that does this
     * in Room. Nothing in production calls it; the fake has to be told the row went.
     */
    fun cascadeDelete(sessionId: SessionId) {
        state.value = state.value.filterNot { it.sessionId == sessionId }
    }

    override fun observeForSession(sessionId: SessionId): Flow<List<SessionExercise>> =
        state.map { forSession(sessionId) }

    override fun observeForSessions(sessionIds: List<SessionId>): Flow<List<SessionExercise>> =
        state.map { rows -> rows.filter { it.sessionId in sessionIds }.sortedBy { it.position } }

    override suspend fun add(sessionExercise: SessionExercise) {
        state.value = state.value + sessionExercise
    }

    override suspend fun nextPosition(sessionId: SessionId): Int = forSession(sessionId).size + 1
}
