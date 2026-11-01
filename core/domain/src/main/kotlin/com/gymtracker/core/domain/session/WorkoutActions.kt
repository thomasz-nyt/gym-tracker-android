package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.MovementOutcome
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import com.gymtracker.core.domain.set.SetRepository
import kotlinx.coroutines.flow.first

/** Explicit navigation and outcome decisions for the shared workout (US-63). */
class WorkoutActions(
    private val sessions: SessionRepository,
    private val movements: SessionExerciseRepository,
    private val sets: SetRepository,
) {
    /** Selects an existing occurrence in this active workout, durably. */
    suspend fun select(
        sessionId: SessionId,
        movementId: SessionExerciseId,
    ) {
        val session = sessions.findSession(sessionId) ?: return
        if (session.endedAt != null || movements.find(movementId)?.sessionId != sessionId) return
        sessions.updateSession(session.copy(workflow = session.workflow.copy(selected = movementId)))
    }

    /** Records only a valid explicit outcome; an outcome never creates or changes actual sets. */
    suspend fun outcome(
        id: SessionExerciseId,
        outcome: MovementOutcome?,
    ): Boolean {
        val movement = activeMovement(id) ?: return false
        val count = sets.observeForSessionExercise(id).first().size
        val planned = movement.target?.sets
        val valid =
            when (outcome) {
                null -> true
                MovementOutcome.SKIPPED -> count == 0
                MovementOutcome.DONE -> count > 0 && planned == null
                MovementOutcome.ENDED_EARLY -> count > 0 && planned != null && count < planned
            }
        if (valid) movements.update(movement.copy(outcome = outcome))
        return valid
    }

    /** Moves one occurrence one place, preserving its identity, target and actual records. */
    suspend fun move(
        id: SessionExerciseId,
        direction: Int,
    ) {
        val movement = activeMovement(id) ?: return
        val session = checkNotNull(sessions.findSession(movement.sessionId))
        val ordered = movements.observeForSession(session.id).first().sortedBy { it.position }
        val index = ordered.indexOfFirst { it.id == id }
        val destination = index + direction.coerceIn(-1, 1)
        if (index < 0 || destination !in ordered.indices || index == destination) return
        val other = ordered[destination]
        movements.update(movement.copy(position = other.position))
        movements.update(other.copy(position = movement.position))
        sessions.updateSession(session.copy(workflow = session.workflow.copy(structuralEdits = true)))
    }

    private suspend fun activeMovement(id: SessionExerciseId): SessionExercise? =
        movements.find(id)?.takeIf { sessions.findSession(it.sessionId)?.isActive == true }
}
