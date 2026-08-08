package com.gymtracker.core.domain.routine

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.RoutineItemId

/**
 * Appends a movement to a routine (US-29).
 *
 * Duplicates are allowed, for the same reason [com.gymtracker.core.domain.sessionexercise
 * .AddExerciseToSession] allows them: a routine shapes a session, and US-02 lets the same
 * exercise appear twice in one. Each appearance is its own row.
 */
class AddExerciseToRoutine(
    private val items: RoutineItemRepository,
    private val newId: () -> RoutineItemId,
) {
    /** @return the appended item. */
    suspend operator fun invoke(
        routineId: RoutineId,
        exerciseId: ExerciseId,
    ): RoutineItem {
        val item =
            RoutineItem(
                id = newId(),
                routineId = routineId,
                exerciseId = exerciseId,
                position = items.nextItemPosition(routineId),
            )
        items.addItem(item)
        return item
    }
}
