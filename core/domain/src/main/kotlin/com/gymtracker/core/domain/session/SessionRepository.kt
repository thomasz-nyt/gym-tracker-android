package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Sessions as the domain needs them. Implemented over Room in `:core:data`, which is the
 * source of truth for the UI — the network is a sync detail (constitution §2).
 */
interface SessionRepository {
    /** The member's active session, or null. Emits again whenever it changes. */
    fun observeActiveSession(userId: UserId): Flow<WorkoutSession?>

    /** The member's active session right now, or null if they have none. */
    suspend fun findActiveSession(userId: UserId): WorkoutSession?

    /** Persists a new session. Callers must have checked there is no active one (US-01). */
    suspend fun startSession(session: WorkoutSession)

    /**
     * Closes a session at [endedAt], which must be a real observed timestamp — either the
     * moment the member ended it or their last set's `performed_at` (US-01, US-06).
     */
    suspend fun endSession(
        id: SessionId,
        endedAt: Instant,
    )

    /** Removes a session entirely. Only valid for a session with no sets (US-06). */
    suspend fun discardSession(id: SessionId)
}
