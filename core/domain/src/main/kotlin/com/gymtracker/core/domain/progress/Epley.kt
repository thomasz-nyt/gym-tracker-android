package com.gymtracker.core.domain.progress

/**
 * Estimated one-rep max, by the Epley formula (US-16).
 *
 * `1RM = w × (1 + reps / 30)`.
 *
 * **This is an estimate and every screen that shows it says so** (US-16). It is the one
 * number in the app that was not lifted by anybody, which is why it is confined to a chart
 * series and to nothing that reads like a record. Constitution §2.4 forbids presenting an
 * estimate as a logged value, not computing one.
 */
object Epley {
    /**
     * @return the estimate in the same unit as [weightKg], or null when there is nothing to
     *   estimate from — a set with no load, or a rep count that never happened. Null rather
     *   than zero, because a bodyweight set does not have a one-rep max of nothing.
     */
    fun oneRepMax(
        weightKg: Double?,
        reps: Int,
    ): Double? =
        when {
            weightKg == null || reps < 1 -> null
            // A single is its own maximum. The formula would return 1.033 × w, a heavier
            // number than the one actually lifted, and calling that a *maximum* would invent
            // a lift.
            reps == 1 -> weightKg
            else -> weightKg * (1 + reps / REPS_DIVISOR)
        }

    private const val REPS_DIVISOR = 30.0
}
