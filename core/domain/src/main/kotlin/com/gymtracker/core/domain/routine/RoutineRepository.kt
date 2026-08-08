package com.gymtracker.core.domain.routine

import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.UserId
import kotlinx.coroutines.flow.Flow

/**
 * The member's saved routines (US-29, ADR-0020).
 *
 * One repository per table, as everywhere else here — the movements inside a routine live in
 * [RoutineItemRepository]. Note the shape: there is no method that writes a weight, a rep
 * count or a set count, because there is no column to write one to. The absence is the point.
 */
interface RoutineRepository {
    /** The member's routines, ordered by position. */
    fun observeRoutines(userId: UserId): Flow<List<Routine>>

    /** One routine, or null if it is not there. */
    suspend fun find(id: RoutineId): Routine?

    /** Appends [routine]. Callers get its position from [nextRoutinePosition]. */
    suspend fun add(routine: Routine)

    /** Renames a routine, leaving its movements alone. */
    suspend fun rename(
        id: RoutineId,
        name: String,
    )

    /**
     * Deletes a routine.
     *
     * Its items go with it via `ON DELETE CASCADE`, and **no session is touched** — the copy
     * made at start time is the only relationship that ever existed between the two.
     */
    suspend fun delete(id: RoutineId)

    /** The position a new routine should take in the member's list. 1-based. */
    suspend fun nextRoutinePosition(userId: UserId): Int
}
