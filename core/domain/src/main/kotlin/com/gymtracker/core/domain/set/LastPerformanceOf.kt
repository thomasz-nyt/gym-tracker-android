package com.gymtracker.core.domain.set

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.UserId
import java.time.Instant

/**
 * What the member actually lifted, the last time they did this movement.
 *
 * **History, never a target.** This is what a routine puts beside each movement (US-29) in
 * place of the stored sets-and-reps ADR-0020 refused to model. Every number here is one
 * someone performed, and the screen labels it as such.
 */
data class LastPerformance(
    /** Kilograms, the canonical unit. Null for a bodyweight movement — never zero. */
    val weightKg: Double?,
    val reps: Int,
    val performedAt: Instant,
)

/**
 * The member's most recent set of an exercise, as history (US-29).
 *
 * Distinct from [PrefillFromLastSet] on purpose, though both read the same row. A prefill is a
 * number about to be typed, so it is converted to the member's unit and needs no date. This is
 * a number about to be read, so it keeps kilograms and carries the date — without which
 * "100 lb × 8" beside a movement reads exactly like the prescription ADR-0020 declined to
 * store.
 */
class LastPerformanceOf(
    private val sets: SetRepository,
) {
    /** @return null when the member has never performed [exerciseId], so the row shows no numbers. */
    suspend operator fun invoke(
        exerciseId: ExerciseId,
        member: UserId,
    ): LastPerformance? {
        val last = sets.lastSetOf(exerciseId, member) ?: return null

        return LastPerformance(
            weightKg = last.weightKg,
            reps = last.reps,
            performedAt = last.performedAt,
        )
    }
}
