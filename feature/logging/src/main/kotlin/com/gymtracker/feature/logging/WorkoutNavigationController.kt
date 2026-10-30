package com.gymtracker.feature.logging

import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.model.MovementOutcome
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.session.WorkoutActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Navigation and explicit movement outcomes share the persisted workout (US-63). */
class WorkoutNavigationController(
    private val actions: WorkoutActions,
    private val sessions: SessionRepository,
    private val member: CurrentMember,
    private val selection: ExerciseSelectionController,
    private val scope: CoroutineScope,
) {
    fun select(id: SessionExerciseId) {
        selection.select(id)
        scope.launch { sessions.findActiveSession(member.id())?.let { actions.select(it.id, id) } }
    }

    fun outcome(
        row: SessionExerciseRow,
        outcome: MovementOutcome?,
    ) {
        scope.launch { actions.outcome(row.sessionExercise.id, outcome) }
    }

    fun move(
        id: SessionExerciseId,
        direction: Int,
    ) {
        scope.launch { actions.move(id, direction) }
    }
}
