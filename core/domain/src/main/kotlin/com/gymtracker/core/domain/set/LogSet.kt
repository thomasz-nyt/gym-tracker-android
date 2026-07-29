package com.gymtracker.core.domain.set

import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.units.UnitConverter
import com.gymtracker.core.domain.units.WeightUnit
import java.time.Clock

/**
 * Records a set (US-03).
 *
 * Weight arrives in whatever unit the member reads, and leaves as canonical kilograms —
 * this and [com.gymtracker.core.domain.units.UnitConverter] are the only places that
 * conversion happens (ADR-0006).
 */
class LogSet(
    private val sets: SetRepository,
    private val clock: Clock,
    private val newId: () -> String,
) {
    /**
     * @param weight as the member typed it, in [unit]. Null for a bodyweight movement, which
     *   is recorded as absent rather than as zero.
     * @param rpe optional, 5.0..10.0 in 0.5 steps.
     * @throws IllegalArgumentException if reps or rpe are outside what `data-model.md` allows.
     */
    suspend operator fun invoke(
        sessionExerciseId: SessionExerciseId,
        weight: Double?,
        unit: WeightUnit,
        reps: Int,
        rpe: Double?,
    ): ExerciseSet {
        require(reps >= MIN_REPS) { "A set needs at least $MIN_REPS rep, but was $reps" }
        rpe?.let {
            require(it in MIN_RPE..MAX_RPE) { "RPE is $MIN_RPE..$MAX_RPE, but was $it" }
            require((it * 2) % 1.0 == 0.0) { "RPE moves in half steps, but was $it" }
        }

        val set =
            ExerciseSet(
                id = newId(),
                sessionExerciseId = sessionExerciseId,
                setIndex = sets.nextSetIndex(sessionExerciseId),
                weightKg = weight?.let { UnitConverter.toKilograms(it, unit) },
                reps = reps,
                rpe = rpe,
                performedAt = clock.instant(),
            )

        // Persisted before this returns, so the caller cannot transition the UI on a set that
        // is not yet on disk (US-03).
        sets.add(set)
        return set
    }

    private companion object {
        const val MIN_REPS = 1
        const val MIN_RPE = 5.0
        const val MAX_RPE = 10.0
    }
}
