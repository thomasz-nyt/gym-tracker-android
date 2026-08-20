package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.SessionMetrics
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Sessions as the domain needs them. Implemented over Room in `:core:data`, which is the
 * source of truth for the UI — the network is a sync detail (constitution §2).
 *
 * One function past detekt's interface threshold, suppressed for the same reason `SessionDao`
 * carries the same suppression: this is a persistence port for one table, not a class
 * accumulating behaviour. US-23's two additions ([clearMetrics], [countSessionsWithMetrics])
 * are reads and writes of `sessions` like every other member here. Splitting the port by story
 * would put "sessions I can write" and "sessions I can forget" behind two interfaces over one
 * table — a worse seam than the count it buys back.
 */
@Suppress("TooManyFunctions")
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

    /**
     * Writes [metrics] onto session [id] (US-22). Touches only the four metrics columns —
     * `ended_at` and everything else about the session is untouched, so this can run at any
     * point after the session exists without racing anything else that writes to it.
     */
    suspend fun saveMetrics(
        id: SessionId,
        metrics: SessionMetrics,
    )

    /**
     * Clears every health metric [userId] has imported, returning how many sessions changed
     * (US-23, ADR-0040).
     *
     * All four metrics columns go together — the average, the peak, the calories **and** the
     * source marker. Leaving the source set would render a cleared session as "read, found
     * nothing" (US-22) rather than as one that was never read for, which is a different and
     * false statement about the member's data.
     *
     * Sessions carrying no metrics are left completely alone, `updated_at` included, so
     * revoking does not mark a member's whole history dirty for a future sync.
     */
    suspend fun clearMetrics(userId: UserId): Int

    /**
     * How many of [userId]'s sessions carry imported metrics — the number the revoke offer
     * names, and the check that stops it appearing when there is nothing to delete (US-23).
     *
     * Counts the active session as well as finished ones: it is the member's data like any
     * other, and by the time metrics exist on it the read has already happened.
     */
    suspend fun countSessionsWithMetrics(userId: UserId): Int
}
