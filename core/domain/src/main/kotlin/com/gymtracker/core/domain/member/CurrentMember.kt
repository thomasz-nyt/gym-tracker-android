package com.gymtracker.core.domain.member

import com.gymtracker.core.domain.model.UserId

/**
 * The member this device logs for.
 *
 * Before M2 there is exactly one, identified by a locally generated UUID
 * (`data-model.md` § "Identity before M2"). At M2 the same seam returns the authenticated
 * Supabase user id, so nothing above this interface changes.
 */
interface CurrentMember {
    /** The member's id, generating and persisting one on first call if needed. */
    suspend fun id(): UserId

    /**
     * Overwrites the stored id — restoring a backup's identity (US-41, ADR-0034) rather than
     * rewriting the rows under a freshly generated one, which is the whole reason a restored
     * backup is visible to any screen: every read filters on this id. [AccountAdoption] (US-58,
     * ADR-0042) reuses this same overwrite for a second reason — moving the device's current
     * member id to a newly-signed-in account — since both are exactly the same operation: point
     * every future read at a different id, never generate one.
     *
     * The one deliberate exception to [id]'s "generated once and never regenerated" — see
     * `DataStoreCurrentMember`'s own KDoc.
     */
    suspend fun restore(id: UserId)
}
