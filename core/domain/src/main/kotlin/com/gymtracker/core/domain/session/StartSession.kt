package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import java.time.Clock

/** Whether "Start workout" created a session or returned the one already running. */
sealed interface StartSessionResult {
    /** A new session was created and persisted. */
    data class Started(
        val session: WorkoutSession,
    ) : StartSessionResult

    /** The member already had an active session, so that one is returned unchanged (US-01). */
    data class Resumed(
        val session: WorkoutSession,
    ) : StartSessionResult
}

/**
 * Starts a gym session, or resumes the member's active one.
 *
 * Enforces US-01's "only one active session per member at a time": this use case is the
 * only way a session is created, and it will not create a second one.
 *
 * @param newId generates the id for a new session. Injected so tests are deterministic;
 *   production supplies a UUID.
 */
class StartSession(
    private val sessions: SessionRepository,
    private val clock: Clock,
    private val newId: () -> SessionId,
) {
    /** @return [StartSessionResult.Resumed] if the member was already in a session. */
    suspend operator fun invoke(userId: UserId): StartSessionResult {
        sessions.findActiveSession(userId)?.let { return StartSessionResult.Resumed(it) }

        val session =
            WorkoutSession(
                id = newId(),
                userId = userId,
                gymName = null,
                startedAt = clock.instant(),
                endedAt = null,
                metrics = null,
            )
        sessions.startSession(session)
        return StartSessionResult.Started(session)
    }
}
