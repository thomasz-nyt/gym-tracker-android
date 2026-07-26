package com.gymtracker.core.domain.exercise

import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.UserId
import kotlinx.coroutines.flow.Flow

/**
 * The exercise catalog the member searches (US-02).
 *
 * The catalog is bundled in the app, so this never touches the network — searching works in a
 * gym with no signal (constitution §2).
 */
interface ExerciseCatalog {
    /**
     * Exercises whose name matches [query], most recently used first and the rest alphabetically.
     *
     * @param query a substring match on the name. Blank returns the whole catalog.
     * @param forMember whose usage history decides the ranking.
     */
    fun search(
        query: String,
        forMember: UserId,
    ): Flow<List<Exercise>>
}
