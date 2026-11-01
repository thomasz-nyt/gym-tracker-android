package com.gymtracker.core.domain.routine

import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.RoutineItemId

/** Removes one authored occurrence and retains its complete snapshot for Undo (US-62). */
class RemoveExerciseFromRoutine(
    private val items: RoutineItemRepository,
) {
    /** Returns the exact removed item, including authored targets and position. */
    suspend operator fun invoke(id: RoutineItemId) {
        items.removeItem(id)
    }

    /** Removes this exact item and returns its complete snapshot for Undo. */
    suspend operator fun invoke(item: RoutineItem): RoutineItem {
        items.removeItem(item.id)
        return item
    }

    /** Restores a removed occurrence with its original identity and authored values. */
    suspend fun restore(item: RoutineItem) {
        items.addItem(item)
    }
}
