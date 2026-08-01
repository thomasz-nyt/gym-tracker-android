package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.set.SetRepository
import java.time.Clock
import java.time.Instant

/** What happened when the member pressed "Finish workout". */
sealed interface EndSessionResult {
    /** The session was closed at [endedAt]. */
    data class Ended(
        val endedAt: Instant,
    ) : EndSessionResult

    /** It had no sets, so there was nothing to keep (US-06). */
    data object Discarded : EndSessionResult
}

/**
 * Ends the member's session (US-06).
 *
 * The end time is **now**, the moment they pressed the button — not their last set. Ending at
 * the last set is the stale-session path (US-01), and it is right there precisely because
 * nobody was present to press anything; using it here would shorten every workout by however
 * long the last rest was.
 *
 * A session with no sets is deleted rather than saved, so an accidental "Start workout" leaves
 * nothing behind to explain later.
 */
class EndSession(
    private val sessions: SessionRepository,
    private val sets: SetRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(id: SessionId): EndSessionResult {
        if (sets.lastSetAtInSession(id) == null) {
            sessions.deleteSession(id)
            return EndSessionResult.Discarded
        }

        val endedAt = clock.instant()
        sessions.endSession(id, endedAt)
        return EndSessionResult.Ended(endedAt)
    }
}
