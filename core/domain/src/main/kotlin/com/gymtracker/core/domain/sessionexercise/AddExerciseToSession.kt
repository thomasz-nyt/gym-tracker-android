package com.gymtracker.core.domain.sessionexercise

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId

/**
 * Appends an exercise to a session (US-02).
 *
 * Duplicates are allowed on purpose: US-02 says the same exercise may appear twice in one
 * session, and each appearance gets its own row so their sets stay separate.
 */
class AddExerciseToSession(
    private val sessionExercises: SessionExerciseRepository,
    private val newId: () -> SessionExerciseId,
) {
    suspend operator fun invoke(
        sessionId: SessionId,
        exerciseId: ExerciseId,
    ): SessionExercise {
        val appended =
            SessionExercise(
                id = newId(),
                sessionId = sessionId,
                exerciseId = exerciseId,
                position = sessionExercises.nextPosition(sessionId),
            )
        sessionExercises.add(appended)
        return appended
    }
}
