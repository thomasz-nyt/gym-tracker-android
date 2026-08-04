package com.gymtracker.core.domain.sessionexercise

import com.gymtracker.core.domain.model.SessionExerciseId
import java.time.Clock

/**
 * Marks one appearance done, or takes the mark back (US-02d).
 *
 * Both directions are the member's explicit act — the card's toggle, or completing a guided
 * walkthrough (US-05a). The third way the mark changes is not here: logging a set clears it
 * inside [com.gymtracker.core.domain.set.LogSet], next to the write, so no path can record a
 * set without also un-finishing the exercise it lands on (ADR-0019).
 */
class FinishExercise(
    private val sessionExercises: SessionExerciseRepository,
    private val clock: Clock,
) {
    /** Declares the appearance done, as of now. */
    suspend fun mark(id: SessionExerciseId) = sessionExercises.setFinishedAt(id, clock.instant())

    /** Takes the declaration back — the mis-tap, or the machine that freed up. */
    suspend fun clear(id: SessionExerciseId) = sessionExercises.setFinishedAt(id, null)
}
