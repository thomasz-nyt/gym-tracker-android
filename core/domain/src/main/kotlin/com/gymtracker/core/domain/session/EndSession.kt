package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.set.SetRepository
import java.time.Clock

/** Whether a finished session was kept or thrown away (US-06). */
enum class EndSessionResult {
    Ended,
    Discarded,
}

/**
 * Ends the workout (US-06).
 *
 * A session with no sets is discarded rather than saved: nothing happened, and a history
 * full of empty entries from accidental taps would be worse than no history. A session with
 * sets ends now — that is a real observed moment, unlike the stale-session path in US-01
 * where "now" would be a fiction and the last set's time is used instead.
 */
class EndSession(
    private val sessions: SessionRepository,
    private val sets: SetRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(id: SessionId): EndSessionResult {
        val hasSets = sets.lastSetAtInSession(id) != null

        return if (hasSets) {
            sessions.endSession(id, clock.instant())
            EndSessionResult.Ended
        } else {
            sessions.discardSession(id)
            EndSessionResult.Discarded
        }
    }
}
