package com.gymtracker.core.domain.model

/**
 * Sets, reps and load for one movement (US-30, ADR-0027) — either a [RoutineItem]'s plan for
 * it, or the snapshot a [SessionExercise] copied from one when the session started.
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
 */
data class MovementTarget(
    val sets: Int?,
    val reps: Int?,
    val weightKg: Double?,
)
