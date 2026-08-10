package com.gymtracker.core.domain.routine

import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.RoutineItem

/**
 * Sets or clears one movement's target (US-30, ADR-0027).
 *
 * `id`, `routineId`, `exerciseId` and `position` are carried over unchanged from [item] — the
 * same shape [com.gymtracker.core.domain.set.UpdateSet] uses to correct a logged set — so this
 * can only ever change what a movement plans for, never which movement it is or where it sits.
 *
 * Passing `target = null` clears it. Editing one movement's target changes no other movement
 * (each is its own row) and no session already started (a session holds its own copy, made at
 * [com.gymtracker.core.domain.routine.StartSessionFromRoutine] time).
 */
class SetRoutineItemTarget(
    private val items: RoutineItemRepository,
) {
    /**
     * @throws IllegalArgumentException if [target] is non-null and any of its present fields
     *   is out of range.
     */
    suspend operator fun invoke(
        item: RoutineItem,
        target: MovementTarget?,
    ): RoutineItem {
        target?.let(TargetValidation::requireValid)

        val updated = item.copy(target = target)
        items.updateItem(updated)
        return updated
    }
}
