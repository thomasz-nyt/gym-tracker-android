package com.gymtracker.core.domain.routine

import com.gymtracker.core.domain.model.RoutineId

/** Renames a routine, leaving its movements untouched (US-29). */
class RenameRoutine(
    private val routines: RoutineRepository,
) {
    suspend operator fun invoke(
        id: RoutineId,
        name: String,
    ) {
        routines.rename(id, name)
    }
}
