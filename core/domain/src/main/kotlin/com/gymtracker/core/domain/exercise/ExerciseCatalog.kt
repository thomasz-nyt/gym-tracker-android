package com.gymtracker.core.domain.exercise

import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The exercise catalog the member searches (US-02) and browses (US-12).
 *
 * The catalog is bundled in the app, so this never touches the network — searching works in a
 * gym with no signal (constitution §2).
 */
interface ExerciseCatalog {
    /**
     * The whole catalog, ranked: most recently used first, then starters (ADR-0007), then
     * alphabetically.
     *
     * Only the *ranking* needs the database, because it joins against the member's sessions.
     * Narrowing is [CatalogQuery]'s job — see the note there on why the predicates are not
     * SQL.
     *
     * @param forMember whose usage history decides the ranking.
     */
    fun observeRanked(forMember: UserId): Flow<List<Exercise>>

    /**
     * The catalog narrowed by text and filters, still ranked (US-12).
     *
     * A convenience over [observeRanked], not a second thing to implement.
     */
    fun browse(
        query: String,
        filter: CatalogFilter,
        forMember: UserId,
    ): Flow<List<Exercise>> = observeRanked(forMember).map { CatalogQuery.apply(it, query, filter) }

    /**
     * [browse] with no filters — the in-session "add an exercise" search (US-02).
     *
     * Matches names and aliases, so the search someone uses mid-workout finds "pec deck" too
     * (ADR-0015).
     */
    fun search(
        query: String,
        forMember: UserId,
    ): Flow<List<Exercise>> = browse(query, CatalogFilter(), forMember)
}
