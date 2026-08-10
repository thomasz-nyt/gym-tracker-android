package com.gymtracker.core.domain.sessionexercise

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId

/**
 * Appends an exercise to a session (US-02).
 *
 * Duplicates are allowed on purpose: US-02 says the same exercise may appear twice in one
 * session, and each appearance gets its own row so their sets stay separate.
 *
 * @param target US-30's copied plan (ADR-0027), when this appearance came from a routine item
 *   that had one. Defaulted to null, so every US-02 call site — adding from the catalog, not a
 *   routine — is unaffected.
 */
class AddExerciseToSession(
    private val sessionExercises: SessionExerciseRepository,
    private val newId: () -> SessionExerciseId,
) {
    suspend operator fun invoke(
        sessionId: SessionId,
        exerciseId: ExerciseId,
        target: MovementTarget? = null,
    ): SessionExercise {
        val appended =
            SessionExercise(
                id = newId(),
                sessionId = sessionId,
                exerciseId = exerciseId,
                position = sessionExercises.nextPosition(sessionId),
                target = target,
            )
        sessionExercises.add(appended)
        return appended
    }
}
