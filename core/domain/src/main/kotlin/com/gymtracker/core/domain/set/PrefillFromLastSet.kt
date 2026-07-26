package com.gymtracker.core.domain.set

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.units.UnitConverter
import com.gymtracker.core.domain.units.WeightUnit

/**
 * What the set-entry fields should start with.
 *
 * @property weight in the member's unit, ready to display. Null for a bodyweight movement.
 * @property rpe always null — see [PrefillFromLastSet].
 */
data class SetPrefill(
    val weight: Double?,
    val reps: Int,
    val rpe: Double? = null,
)

/**
 * US-03: weight and reps come from the member's most recent set of that exercise, in any
 * session. Two taps only works if the numbers are already right.
 *
 * RPE is deliberately not carried forward. It records how hard *that* set felt; repeating it
 * would be inventing a measurement nobody took (constitution §2).
 */
class PrefillFromLastSet(
    private val sets: SetRepository,
) {
    /** @return null when the member has never done this exercise, so the fields start empty. */
    suspend operator fun invoke(
        exerciseId: ExerciseId,
        member: UserId,
        unit: WeightUnit,
    ): SetPrefill? {
        val last = sets.lastSetOf(exerciseId, member) ?: return null

        return SetPrefill(
            weight = last.weightKg?.let { UnitConverter.fromKilograms(it, unit) },
            reps = last.reps,
        )
    }
}
