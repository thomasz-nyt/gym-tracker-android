package com.gymtracker.core.domain.set

import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.units.WeightUnit

/**
 * What the member entered for a set, before it is converted and stored.
 *
 * These four always travel together, so they travel as one thing.
 *
 * @property weight as typed, in [unit]. Null for a bodyweight movement.
 */
data class SetInput(
    val weight: Double?,
    val unit: WeightUnit,
    val reps: Int,
    val rpe: Double? = null,
)

/**
 * Records the same set several times over — "3 sets of 12" (ADR-0009).
 *
 * Each set is still its own row with its own index, so US-04 can edit or delete any one of
 * them and every M4 chart still reads individual sets. This is an input shorthand, not a
 * stored concept.
 */
class LogSets(
    private val logSet: LogSet,
) {
    /**
     * @param sets how many identical sets to record. Defaults to one, which is the
     *   set-by-set path the two-tap requirement depends on.
     * @throws IllegalArgumentException if [sets] is less than one.
     */
    suspend operator fun invoke(
        sessionExerciseId: SessionExerciseId,
        input: SetInput,
        sets: Int = 1,
    ): List<ExerciseSet> {
        require(sets >= 1) { "A set count must be at least 1, but was $sets" }

        // Sequential rather than concurrent: each row's index comes from counting the rows
        // already stored, so they have to be written in order.
        return (1..sets).map {
            logSet(sessionExerciseId, input.weight, input.unit, input.reps, input.rpe)
        }
    }
}
