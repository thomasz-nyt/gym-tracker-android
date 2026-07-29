package com.gymtracker.core.domain.model

import java.time.Instant

/**
 * One set, the unit everything else derives from (`data-model.md` § Core principle).
 *
 * @property sessionExerciseId which appearance of the exercise this belongs to (ADR-0004),
 *   so two visits to the same machine in one session keep their sets apart.
 * @property setIndex 1-based within the session-exercise.
 * @property weightKg canonical kilograms. Null for a bodyweight movement — absent, not zero.
 * @property rpe 5.0..10.0 in 0.5 steps, or null.
 */
data class ExerciseSet(
    val id: String,
    val sessionExerciseId: SessionExerciseId,
    val setIndex: Int,
    val weightKg: Double?,
    val reps: Int,
    val rpe: Double?,
    val performedAt: Instant,
)
