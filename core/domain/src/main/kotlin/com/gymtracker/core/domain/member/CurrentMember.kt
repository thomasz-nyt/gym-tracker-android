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
}
