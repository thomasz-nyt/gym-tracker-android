package com.gymtracker.core.domain.model

import java.time.Duration

/**
 * Sets, reps, load and — since ADR-0050 — the rest that follows each set, for one movement
 * (US-30, ADR-0027): either a [RoutineItem]'s plan for it, or the snapshot a [SessionExercise]
 * copied from one when the session started.
 *
 * Each field is independently nullable: "3 sets of 8, load unrecorded" is a real plan, and so
 * is "bench, no numbers", which is what every movement carried before this existed. There is no
 * all-or-nothing rule here — a target with every field null is just a movement with no plan,
 * which is why [RoutineItem.target] and [SessionExercise.target] are themselves nullable too
 * rather than defaulting to an all-null [MovementTarget].
 *
 * **Never written to `sets`, and never read by anything that computes a derived number.**
 * Volume, the trend, Epley, and personal records all read `sets` alone — a planned number that
 * was never lifted must not become one of those (constitution §2.4, ADR-0027).
 *
 * @property weightKg canonical unit is always kg, exactly like [ExerciseSet.weightKg].
 * @property restSeconds the rest to take after each set of this movement, in whole seconds
 *   (US-05 and US-30 as amended by ADR-0050). Null means the member's default rest from Settings,
 *   not "no rest" — the same absence rule as every other field. Not a target in the §2.4 sense:
 *   a rest is time the app counts down, never a number it could claim was lifted.
 */
data class MovementTarget(
    val sets: Int?,
    val reps: Int?,
    val weightKg: Double?,
    val restSeconds: Int? = null,
) {
    /** [restSeconds] as the [Duration] the rest timer takes, or null for the member's default. */
    val rest: Duration?
        get() = restSeconds?.let { Duration.ofSeconds(it.toLong()) }
}
