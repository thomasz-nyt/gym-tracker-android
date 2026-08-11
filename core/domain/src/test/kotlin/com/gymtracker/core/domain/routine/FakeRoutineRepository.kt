package com.gymtracker.core.domain.routine

import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Hand-written fakes, per `specs/testing-strategy.md`.
 *
 * They emulate the SQL semantics that matter — `ORDER BY position` and `MAX(position) + 1` —
 * because that is what makes a fake worth having over a mock.
 *
 * @param cascade stands in for the `ON DELETE CASCADE` from `routines` to `routine_items`.
 *   Nothing in the domain deletes items explicitly, so a test that cares has to wire it, the
 *   same way [com.gymtracker.core.domain.session.FakeSessionRepository] does.
 */
internal class FakeRoutineRepository(
    private val cascade: (RoutineId) -> Unit = {},
) : RoutineRepository {
    private val state = MutableStateFlow(emptyList<Routine>())

    val all: List<Routine> get() = state.value

    override fun observeRoutines(userId: UserId): Flow<List<Routine>> =
        state.map { routines -> routines.filter { it.userId == userId }.sortedBy { it.position } }

    override suspend fun find(id: RoutineId): Routine? = state.value.firstOrNull { it.id == id }

    override suspend fun add(routine: Routine) {
        state.value = state.value + routine
    }

    override suspend fun rename(
        id: RoutineId,
        name: String,
    ) {
        state.value = state.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun delete(id: RoutineId) {
        state.value = state.value.filterNot { it.id == id }
        cascade(id)
    }

    override suspend fun nextRoutinePosition(userId: UserId): Int =
        (state.value.filter { it.userId == userId }.maxOfOrNull { it.position } ?: 0) + 1
}

/** The movements half of the fake pair; see [FakeRoutineRepository]. */
internal class FakeRoutineItemRepository : RoutineItemRepository {
    private val state = MutableStateFlow(emptyList<RoutineItem>())

    val all: List<RoutineItem> get() = state.value

    /** Stands in for the `ON DELETE CASCADE` from `routines`. */
    fun cascadeDelete(routineId: RoutineId) {
        state.value = state.value.filterNot { it.routineId == routineId }
    }

    override fun observeItems(routineId: RoutineId): Flow<List<RoutineItem>> = state.map { forRoutine(routineId) }

    override suspend fun itemsOf(routineId: RoutineId): List<RoutineItem> = forRoutine(routineId)

    override suspend fun addItem(item: RoutineItem) {
        state.value = state.value + item
    }

    override suspend fun updateItem(item: RoutineItem) {
        state.value = state.value.map { if (it.id == item.id) item else it }
    }

    override suspend fun removeItem(id: RoutineItemId) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun nextItemPosition(routineId: RoutineId): Int =
        (forRoutine(routineId).maxOfOrNull { it.position } ?: 0) + 1

    override suspend fun setItemPositions(positions: Map<RoutineItemId, Int>) {
        state.value = state.value.map { item -> positions[item.id]?.let { item.copy(position = it) } ?: item }
    }

    private fun forRoutine(routineId: RoutineId) =
        state.value.filter { it.routineId == routineId }.sortedBy { it.position }
}
