package com.gymtracker.core.domain.model

/**
 * One appearance of an exercise within a session (ADR-0004).
 *
 * The same exercise may appear twice in one session — someone comes back to a machine later —
 * so this is what sets hang off, not the exercise itself.
 *
 * [target] is US-30's snapshot (ADR-0027): when [com.gymtracker.core.domain.routine
 * .StartSessionFromRoutine] copies a [RoutineItem] with a target into a session, the target
 * comes with it. This is a copy, not a reference — there is still no field naming a routine
 * anywhere on this class, so editing the routine afterwards cannot change what a session
 * already carries, and editing the session still never edits the routine. "Add set" prefills
 * from [target] when it is present, from the member's last performed set otherwise (US-03,
 * unchanged); [target] itself is never written to `sets`.
 *
 * @property position 1-based order within the session.
 */
data class SessionExercise(
    val id: SessionExerciseId,
    val sessionId: SessionId,
    val exerciseId: ExerciseId,
    val position: Int,
    val target: MovementTarget? = null,
)
