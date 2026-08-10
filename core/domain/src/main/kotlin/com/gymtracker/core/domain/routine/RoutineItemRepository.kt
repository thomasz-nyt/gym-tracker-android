package com.gymtracker.core.domain.routine

import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.RoutineItemId
import kotlinx.coroutines.flow.Flow

/**
 * The movements inside a routine (US-29, ADR-0020).
 *
 * A separate repository from [RoutineRepository] because `routine_items` is a separate table,
 * which is how `sets` and `session_exercises` are split too.
 *
 * [updateItem] arrived with US-30 (ADR-0027) for [RoutineItem.target] specifically — it is not
 * a general-purpose row editor. `exerciseId` and `position` have their own, narrower use cases
 * ([removeItem], [setItemPositions]) and go through those instead.
 */
interface RoutineItemRepository {
    /** One routine's movements, ordered by position. */
    fun observeItems(routineId: RoutineId): Flow<List<RoutineItem>>

    /** One routine's movements in order, read once rather than observed. */
    suspend fun itemsOf(routineId: RoutineId): List<RoutineItem>

    /** Appends [item]. Callers get its position from [nextItemPosition]. */
    suspend fun addItem(item: RoutineItem)

    /**
     * Writes [item] back in full (US-30). In practice this only ever changes [RoutineItem
     * .target] — see [com.gymtracker.core.domain.routine.SetRoutineItemTarget], the one caller.
     */
    suspend fun updateItem(item: RoutineItem)

    /**
     * Removes one movement.
     *
     * Positions are left as they are, exactly as `SessionExerciseRepository.remove` leaves
     * them: the gap is closed by the next reorder, never by renumbering rows nobody moved.
     */
    suspend fun removeItem(id: RoutineItemId)

    /**
     * The position a movement appended to [routineId] should take. 1-based.
     *
     * `MAX(position) + 1` rather than a count, so removing from the middle cannot mint a
     * position a surviving row already holds.
     */
    suspend fun nextItemPosition(routineId: RoutineId): Int

    /** Applies a whole new ordering at once, so a drag is one transaction rather than N writes. */
    suspend fun setItemPositions(positions: Map<RoutineItemId, Int>)
}
