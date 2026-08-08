package com.gymtracker.core.domain.set

import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.units.UnitConverter
import com.gymtracker.core.domain.units.WeightUnit

/**
 * Corrects a set already logged (US-04): weight, reps or RPE were not right the first time.
 *
 * Reuses [LogSet]'s validation rules verbatim via [SetValidation], so an edited set can never
 * end up outside what a freshly logged one is allowed to be. `id`, `sessionExerciseId`,
 * `setIndex` and `performedAt` are carried over unchanged from [set] — only weight, reps and
 * RPE are things this story lets a member correct.
 */
class UpdateSet(
    private val sets: SetRepository,
) {
    /**
     * @param set the set being corrected, as it is now.
     * @param weight as the member typed it, in [unit]. Null for a bodyweight movement, which is
     *   recorded as absent rather than as zero.
     * @param rpe optional, 5.0..10.0 in 0.5 steps.
     * @throws IllegalArgumentException if reps or rpe are outside what `data-model.md` allows.
     */
    suspend operator fun invoke(
        set: ExerciseSet,
        weight: Double?,
        unit: WeightUnit,
        reps: Int,
        rpe: Double?,
    ): ExerciseSet {
        SetValidation.requireValidReps(reps)
        SetValidation.requireValidRpe(rpe)

        val updated =
            set.copy(
                weightKg = weight?.let { UnitConverter.toKilograms(it, unit) },
                reps = reps,
                rpe = rpe,
            )

        sets.update(updated)
        return updated
    }
}
