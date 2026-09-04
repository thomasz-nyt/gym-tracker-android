package com.gymtracker.core.domain.member

import com.gymtracker.core.domain.model.UserId

/**
 * Adopts a device's local rows into a newly-signed-in account, exactly once per install
 * (ADR-0042, US-58).
 *
 * [adopt] is the one place that logic lives — a future sign-in flow calls it with whatever id
 * Supabase just authenticated, and does not need to know whether this is the device's first
 * sign-in or its fifth. There is no separate business rule above "re-key once" the way
 * [com.gymtracker.core.domain.backup.ImportBackup] layers a refusal on top of
 * [com.gymtracker.core.domain.backup.BackupStore.replaceAll], so unlike that pair this port has
 * no thin use case sitting in front of it — [adopt]'s own contract is the whole rule.
 */
interface AccountAdoption {
    /**
     * Called once, right after a successful sign-in, with the id the server just authenticated.
     *
     * On this install's first-ever call: every row the device's current member id owns is
     * re-assigned to [signedInAs] in one transaction, and this install is marked as having
     * adopted an account, permanently. On every call after that, on this install — a different
     * member signing in on a shared device, or the same member signing back in — nothing is
     * re-assigned; the device's existing rows are left exactly where they are, under whatever
     * id already owns them. Either way, the device's current member id becomes [signedInAs]
     * going forward.
     */
    suspend fun adopt(signedInAs: UserId)
}
