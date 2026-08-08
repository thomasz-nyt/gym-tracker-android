package com.gymtracker.core.domain.routine

import com.gymtracker.core.domain.model.RoutineItemId

/**
 * Removes one movement from a routine (US-29).
 *
 * No undo here, unlike US-02c's removal from a live session. A routine is edited deliberately
 * and away from the gym floor, and nothing is lost that was not typed a moment earlier — the
 * mid-workout removal needed undo because it destroys logged sets, and this destroys nothing.
 */
class RemoveExerciseFromRoutine(
    private val items: RoutineItemRepository,
) {
    suspend operator fun invoke(id: RoutineItemId) {
        items.removeItem(id)
    }
}
