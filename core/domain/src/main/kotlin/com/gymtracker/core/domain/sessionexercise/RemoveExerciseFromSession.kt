package com.gymtracker.core.domain.sessionexercise

import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.set.SetRepository
import kotlinx.coroutines.flow.first

/**
 * An exercise taken out of the session, complete enough to put back (US-02c).
 *
 * Held in memory for the length of the undo window and nowhere else, exactly as
 * [com.gymtracker.core.domain.session.DeletedSession] is (ADR-0012).
 */
data class RemovedExercise(
    val sessionExercise: SessionExercise,
    val sets: List<ExerciseSet>,
)

/**
 * Removes an exercise from the session it is in, returning what it took so
 * [RestoreExerciseToSession] can undo it (US-02c).
 *
 * The machine was taken, or occupied, or the exercise was added by mistake. The removal is
 * real immediately, for ADR-0012's reason: a deferred one dies with the screen that scheduled
 * it, and the member is left with the row they asked to be rid of.
 *
 * Sets are not deleted here. `sets.session_exercise_id` is `ON DELETE CASCADE` in the schema,
 * which is the one place that rule holds for every caller.
 *
 * @return null if there was no such exercise, meaning there is nothing to undo either.
 */
class RemoveExerciseFromSession(
    private val sessionExercises: SessionExerciseRepository,
    private val sets: SetRepository,
) {
    suspend operator fun invoke(id: SessionExerciseId): RemovedExercise? {
        val appearance = sessionExercises.find(id) ?: return null

        // Read the sets before the removal cascades them away.
        val snapshot = RemovedExercise(appearance, sets.observeForSessionExercise(id).first())

        sessionExercises.remove(id)
        return snapshot
    }
}

/**
 * Puts a removed exercise back (US-02c).
 *
 * Written parent-first, because the foreign key that cascades the removal also rejects a set
 * inserted before the appearance it belongs to. The original `position` comes back with it, so
 * the workout reads in the order it was performed.
 */
class RestoreExerciseToSession(
    private val sessionExercises: SessionExerciseRepository,
    private val sets: SetRepository,
) {
    suspend operator fun invoke(removed: RemovedExercise) {
        sessionExercises.add(removed.sessionExercise)
        removed.sets.forEach { sets.add(it) }
    }
}
