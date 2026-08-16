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
 */
interface BackupStore {
    /** Every row [memberId] owns, across all five backed-up tables, plus their preferences. */
    suspend fun read(memberId: UserId): BackupContents

    /**
     * Replaces the member's data wholesale (US-41's replace-all, ADR-0034): deletes what is
     * there and writes [contents] in its place, restores [BackupContents.memberId] as the
     * device's current member id, and writes back the unit and rest-default preferences.
     *
     * Callers are responsible for validating [contents] first — see `ValidateBackup` — and for
     * refusing to call this while a session is active (US-41, [ImportBackup]). This method does
     * neither; it trusts what it is given, the same as `SessionRepository.startSession` trusts
     * its caller to have checked there is no active session already.
     */
    suspend fun replaceAll(contents: BackupContents)
}
