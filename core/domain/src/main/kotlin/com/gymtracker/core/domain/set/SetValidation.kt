package com.gymtracker.core.domain.set

/**
 * The reps/RPE rules `data-model.md` puts on every set, shared by [LogSet] and [UpdateSet]
 * (US-03, US-04) so a correction can never be looser than a freshly logged set.
 */
internal object SetValidation {
    const val MIN_REPS = 1
    const val MIN_RPE = 5.0
    const val MAX_RPE = 10.0

    /** @throws IllegalArgumentException if [reps] is below [MIN_REPS]. */
    fun requireValidReps(reps: Int) {
        require(reps >= MIN_REPS) { "A set needs at least $MIN_REPS rep, but was $reps" }
    }

    /** @throws IllegalArgumentException if [rpe] is non-null and outside [MIN_RPE]..[MAX_RPE] in 0.5 steps. */
    fun requireValidRpe(rpe: Double?) {
        rpe?.let {
            require(it in MIN_RPE..MAX_RPE) { "RPE is $MIN_RPE..$MAX_RPE, but was $it" }
            require((it * 2) % 1.0 == 0.0) { "RPE moves in half steps, but was $it" }
        }
    }
}
