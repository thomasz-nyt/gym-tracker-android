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

    /**
     * The member's finished sessions, newest first — history (US-06).
     *
     * The session they are currently in is deliberately absent: it is not history yet, and
     * keeping it out is what stops US-06a deleting the workout in progress.
     */
    fun observeFinishedSessions(userId: UserId): Flow<List<WorkoutSession>>

    /** The member's active session right now, or null if they have none. */
    suspend fun findActiveSession(userId: UserId): WorkoutSession?

    /** Any session by id, finished or not, or null if there is none. */
    suspend fun findSession(id: SessionId): WorkoutSession?

    /** Persists a new session. Callers must have checked there is no active one (US-01). */
    suspend fun startSession(session: WorkoutSession)

    /**
     * Writes a session back exactly as it was, to undo a delete (US-06a).
     *
     * Separate from [startSession] because it is not a start: the session keeps its own id,
     * its original `started_at` and its `ended_at`, and none of US-01's one-active-session
     * reasoning applies to it.
     */
    suspend fun restoreSession(session: WorkoutSession)

    /**
     * Closes a session at [endedAt], which must be a real observed timestamp — either the
     * moment the member ended it or their last set's `performed_at` (US-01, US-06).
     */
    suspend fun endSession(
        id: SessionId,
        endedAt: Instant,
    )

    /**
     * Removes a session and, by cascade, its exercises and its sets.
     *
     * Used both for a session that ended up empty (US-01, US-06) and for deleting a past
     * workout outright (US-06a). Callers wanting to undo it must snapshot first — see
     * [DeleteSession], which is the only path that should be deleting a workout with sets in it.
     */
    suspend fun deleteSession(id: SessionId)
}
