package com.gymtracker.core.domain.set

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Hand-written fake, per `specs/testing-strategy.md`.
 *
 * Sets reach their session through `session_exercises` (ADR-0004), so this fake needs the
 * same mapping the join gives in Room. Tests register an appearance with [belongsTo], or
 * pass one to [add] alongside the set.
 */
class FakeSetRepository : SetRepository {
    private val state = MutableStateFlow(emptyList<ExerciseSet>())
    private val sessionOf = mutableMapOf<SessionExerciseId, SessionId>()

    /** The exercise whose most recent set the US-03 prefill should find. */
    val lastFor = mutableMapOf<ExerciseId, String>()

    val all: List<ExerciseSet> get() = state.value

    /** Records which session an appearance belongs to, standing in for the join. */
    fun belongsTo(appearance: SessionExercise) {
        sessionOf[appearance.id] = appearance.sessionId
    }

    /** The sets of one session, as the join would return them. */
    fun forSession(sessionId: SessionId): List<ExerciseSet> =
        state.value.filter { sessionOf[it.sessionExerciseId] == sessionId }.sortedBy { it.setIndex }

    /** Stands in for the `ON DELETE CASCADE` from `sessions` through `session_exercises`. */
    fun cascadeDelete(sessionId: SessionId) {
        state.value = state.value.filterNot { sessionOf[it.sessionExerciseId] == sessionId }
    }

    /** Stands in for the `ON DELETE CASCADE` from one `session_exercises` row (US-02c). */
    fun cascadeDeleteExercise(sessionExerciseId: SessionExerciseId) {
        state.value = state.value.filterNot { it.sessionExerciseId == sessionExerciseId }
    }

    override fun observeForSessionExercise(sessionExerciseId: SessionExerciseId): Flow<List<ExerciseSet>> =
        state.map { rows -> rows.filter { it.sessionExerciseId == sessionExerciseId }.sortedBy { it.setIndex } }

    override fun observeForSessions(sessionIds: List<SessionId>): Flow<List<ExerciseSet>> =
        state.map { rows -> rows.filter { sessionOf[it.sessionExerciseId] in sessionIds } }

    override suspend fun lastSetOf(
        exerciseId: ExerciseId,
        member: UserId,
    ): ExerciseSet? = lastFor[exerciseId]?.let { id -> state.value.firstOrNull { it.id == id } }

    override suspend fun lastSetAtInSession(sessionId: SessionId): Instant? =
        forSession(sessionId).maxOfOrNull { it.performedAt }

    override suspend fun nextSetIndex(sessionExerciseId: SessionExerciseId): Int =
        state.value.count { it.sessionExerciseId == sessionExerciseId } + 1

    override suspend fun add(set: ExerciseSet) {
        state.value = state.value + set
    }

    override suspend fun update(set: ExerciseSet) {
        state.value = state.value.map { if (it.id == set.id) set else it }
    }

    override suspend fun delete(id: String): ExerciseSet? {
        val existing = state.value.firstOrNull { it.id == id } ?: return null
        state.value = state.value.filterNot { it.id == id }
        return existing
    }
}
