package com.gymtracker.core.domain.model

import java.time.Instant

/**
 * One appearance of an exercise within a session (ADR-0004).
 *
 * The same exercise may appear twice in one session — someone comes back to a machine later —
 * so this is what sets hang off, not the exercise itself.
 *
 * @property position 1-based order within the session.
 * @property finishedAt when the member marked this appearance done, or null while it is in
 *   progress (US-02d). Always an explicit act, never inferred, and cleared by any set logged
 *   after it (ADR-0019).
 */
data class SessionExercise(
    val id: SessionExerciseId,
    val sessionId: SessionId,
    val exerciseId: ExerciseId,
    val position: Int,
    val finishedAt: Instant? = null,
)
