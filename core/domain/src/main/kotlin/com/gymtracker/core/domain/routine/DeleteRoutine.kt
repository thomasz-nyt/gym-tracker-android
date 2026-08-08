package com.gymtracker.core.domain.routine

import com.gymtracker.core.domain.model.RoutineId

/**
 * Deletes a routine and its movements (US-29).
 *
 * **No session is touched, past or present.** A session started from this routine keeps every
 * exercise and every set it recorded, because the copy at start time is the only relationship
 * that ever existed between them (ADR-0020). Deleting Tuesday's plan cannot delete Tuesday.
 */
class DeleteRoutine(
    private val routines: RoutineRepository,
) {
    suspend operator fun invoke(id: RoutineId) {
        routines.delete(id)
    }
}
