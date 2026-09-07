package com.gymtracker.core.domain.routine

import com.gymtracker.core.domain.model.MovementTarget

/**
 * The floor on a [MovementTarget]'s fields (US-30, ADR-0027; the rest since ADR-0050), each
 * checked only when present — a null field is a plan that says nothing about that number, not a
 * value of zero.
 *
 * Mirrors [com.gymtracker.core.domain.set.SetValidation]'s reps floor deliberately: a target is
 * a prefill for a set (ADR-0017's "the target is a prefill, never a promise"), so it cannot be
 * looser than what a freshly logged set is allowed to be. The rest's floor is one second for the
 * same reason the default rest cannot be set to nothing: a rest of zero is not a rest, and "no
 * rest for this movement" is a superset concept this model does not have.
 */
internal object TargetValidation {
    const val MIN_SETS = 1
    const val MIN_REPS = 1
    const val MIN_REST_SECONDS = 1

    /** @throws IllegalArgumentException if any present field of [target] is out of range. */
    fun requireValid(target: MovementTarget) {
        target.sets?.let {
            require(it >= MIN_SETS) { "A target needs at least $MIN_SETS set, but was $it" }
        }
        target.reps?.let {
            require(it >= MIN_REPS) { "A target needs at least $MIN_REPS rep, but was $it" }
        }
        target.weightKg?.let {
            require(it >= 0) { "A target's load cannot be negative, but was $it" }
        }
        target.restSeconds?.let {
            require(it >= MIN_REST_SECONDS) { "A target's rest needs at least $MIN_REST_SECONDS second, but was $it" }
        }
    }
}
