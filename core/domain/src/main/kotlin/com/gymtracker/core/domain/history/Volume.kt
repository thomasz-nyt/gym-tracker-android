package com.gymtracker.core.domain.history

import com.gymtracker.core.domain.model.ExerciseSet
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Total volume for US-06's history list: weight × reps, summed, in kilograms.
 *
 * Bodyweight sets are **excluded**, not counted as zero. We do not know what the member
 * weighs, and a session of press-ups showing "0 kg" would claim they lifted nothing
 * (constitution §2). A session with no weighted sets has no volume at all, which the UI
 * renders as a dash.
 *
 * An actual zero — an unloaded bar, a machine's lightest setting — does count, because
 * somebody recorded it.
 */
object Volume {
    /** @return null when nothing weighted was logged. */
    fun of(sets: List<ExerciseSet>): Double? {
        val weighted = sets.filter { it.weightKg != null }
        if (weighted.isEmpty()) return null

        return weighted
            .fold(BigDecimal.ZERO) { total, set ->
                total + BigDecimal.valueOf(set.weightKg!!) * BigDecimal(set.reps)
            }.setScale(SCALE, RoundingMode.HALF_UP)
            .toDouble()
    }

    /** Two places, matching how kilograms are stored (ADR-0006). */
    private const val SCALE = 2
}
