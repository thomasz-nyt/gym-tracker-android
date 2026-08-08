package com.gymtracker.core.domain.routine

import com.gymtracker.core.domain.model.RoutineId

/**
 * Reorders a routine by moving one movement (US-29: "drag to reorder").
 *
 * Positions are renumbered contiguously from 1 across the whole routine afterwards. That is
 * worth doing here even though [RoutineItemRepository.removeItem] deliberately leaves gaps: a
 * drag is an explicit statement about the order of *every* row, so this is the one operation
 * entitled to renumber them.
 */
class MoveExerciseInRoutine(
    private val items: RoutineItemRepository,
) {
    /**
     * Moves the movement at index [from] to index [to], both 0-based over the routine's
     * current order.
     *
     * Out-of-range indices are ignored rather than throwing: the caller is a drag surface, and
     * a stale index is a UI bug, not a reason to crash mid-workout.
     */
    suspend operator fun invoke(
        routineId: RoutineId,
        from: Int,
        to: Int,
    ) {
        val current = items.itemsOf(routineId)
        if (from !in current.indices || to !in current.indices || from == to) return

        val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
        items.setItemPositions(reordered.withIndex().associate { (index, item) -> item.id to index + 1 })
    }
}
