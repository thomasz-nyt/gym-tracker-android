package com.gymtracker.core.domain.backup

import com.gymtracker.core.domain.model.UserId

/**
 * Reads and writes everything a backup file carries (US-40, US-41, ADR-0034).
 *
 * Deliberately its own port rather than a composition of the existing repositories: nothing
 * else in the domain needs *every* row a member owns at once — `SessionRepository
 * .observeFinishedSessions` excludes the session in progress on purpose (US-06a), and there is
 * no existing read of every set or every routine item a member has. Implemented over Room in
 * `:core:data`, the same as every other repository (constitution §2).
 *
 * [read] is the whole of US-40. `replaceAll` arrives with US-41 (PR2) — kept off this interface
 * until then, the same way [com.gymtracker.core.domain.routine.RoutineItemRepository.updateItem]
 * "arrived with US-30" onto an interface US-29 shipped first: a method with no failing test
 * behind it yet has no business existing.
 */
interface BackupStore {
    /** Every row [memberId] owns, across all five backed-up tables, plus their preferences. */
    suspend fun read(memberId: UserId): BackupContents
}
