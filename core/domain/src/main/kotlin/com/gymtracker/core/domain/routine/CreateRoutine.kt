package com.gymtracker.core.domain.routine

import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.UserId

/**
 * Creates an empty routine (US-29).
 *
 * @param newId generates the id. Injected so tests are deterministic; production supplies
 *   a UUID, the same way [com.gymtracker.core.domain.session.StartSession] does.
 */
class CreateRoutine(
    private val routines: RoutineRepository,
    private val newId: () -> RoutineId,
) {
    /** @return the created routine, appended to the end of the member's list. */
    suspend operator fun invoke(
        userId: UserId,
        name: String,
    ): Routine {
        val routine =
            Routine(
                id = newId(),
                userId = userId,
                name = name,
                position = routines.nextRoutinePosition(userId),
            )
        routines.add(routine)
        return routine
    }
}
