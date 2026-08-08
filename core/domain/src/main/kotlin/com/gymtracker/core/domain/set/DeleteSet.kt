package com.gymtracker.core.domain.set

import com.gymtracker.core.domain.model.ExerciseSet

/**
 * Deletes one set, with a 5-second undo (US-04).
 *
 * Follows the same pattern as [com.gymtracker.core.domain.session.DeleteSession] and
 * [com.gymtracker.core.domain.sessionexercise.RemoveExerciseFromSession]: hard delete
 * immediately, snapshot in memory, restore on undo — ADR-0012's reasoning applies here too,
 * a deferred delete is silently cancelled if the screen that scheduled it goes away before the
 * undo window closes.
 *
 * A set is a leaf: nothing cascades from it, so unlike those two use cases' `DeletedSession`
 * and `RemovedExercise`, no wrapper snapshot type is needed here — the [ExerciseSet] itself is
 * everything [RestoreSet] needs to put it back.
 */
class DeleteSet(
    private val sets: SetRepository,
) {
    /** @return the deleted set, or null if there was no such set — nothing to undo either. */
    suspend operator fun invoke(id: String): ExerciseSet? = sets.delete(id)
}

/** Puts a deleted set back with the same id, index, values and `performedAt` (US-04). */
class RestoreSet(
    private val sets: SetRepository,
) {
    suspend operator fun invoke(deleted: ExerciseSet) {
        sets.add(deleted)
    }
}
