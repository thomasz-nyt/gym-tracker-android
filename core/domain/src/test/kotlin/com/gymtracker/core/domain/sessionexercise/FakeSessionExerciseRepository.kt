package com.gymtracker.core.domain.sessionexercise

import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Hand-written fake, per `specs/testing-strategy.md`: mocked repositories test that a
 * method was called; fakes test that the behaviour is right.
 *
 * @param cascade stands in for the `ON DELETE CASCADE` on `sets`. Removing an appearance in
 *   Room takes its sets with it; a test that cares has to say so here, because nothing in the
 *   domain does it explicitly (US-02c, ADR-0012).
 */
class FakeSessionExerciseRepository(
    initial: List<SessionExercise> = emptyList(),
    private val cascade: (SessionExerciseId) -> Unit = {},
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

    override suspend fun find(id: SessionExerciseId): SessionExercise? = state.value.firstOrNull { it.id == id }

    override suspend fun add(sessionExercise: SessionExercise) {
        state.value = state.value + sessionExercise
    }

    override suspend fun remove(id: SessionExerciseId) {
        state.value = state.value.filterNot { it.id == id }
        cascade(id)
    }

    // MAX(position) + 1, as the DAO does it. A count would reuse a position after a removal
    // from the middle of a session (US-02c).
    override suspend fun nextPosition(sessionId: SessionId): Int =
        (forSession(sessionId).maxOfOrNull { it.position } ?: 0) + 1
}
