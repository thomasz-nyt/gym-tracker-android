package com.gymtracker.core.domain.model

/**
 * One appearance of an exercise within a session (ADR-0004).
 *
 * The same exercise may appear twice in one session — someone comes back to a machine later —
 * so this is what sets hang off, not the exercise itself.
 *
 * @property position 1-based order within the session.
 */
data class SessionExercise(
    val id: SessionExerciseId,
    val sessionId: SessionId,
    val exerciseId: ExerciseId,
    val position: Int,
)
