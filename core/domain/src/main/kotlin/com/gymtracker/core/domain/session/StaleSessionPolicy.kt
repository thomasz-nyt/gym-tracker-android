package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.WorkoutSession
import java.time.Duration
import java.time.Instant

/**
 * What the app should ask about a session that was left open (US-01).
 *
 * There is no "end it now" case on purpose: `now` is not a time the member was at the
 * gym, and the app never invents an end time.
 */
sealed interface StaleSessionPrompt {
    /** The session has sets, so its last set's timestamp is an honest end time. */
    data class Finish(
        val session: WorkoutSession,
        val endedAt: Instant,
    ) : StaleSessionPrompt

    /** The session has no sets, so there is nothing to keep and no end time to claim. */
    data class Discard(
        val session: WorkoutSession,
    ) : StaleSessionPrompt
}

/**
 * Decides whether an active session has been abandoned.
 *
 * A session is stale when its **last activity** — its last set, or its start if it has
 * none — is more than [STALE_AFTER] old. Session age alone is not the test: someone who
 * has been lifting for five hours is still lifting.
 */
object StaleSessionPolicy {
    private const val STALE_AFTER_HOURS = 4L

    /** US-01: "more than 4 hours old". Exactly four hours is not yet stale. */
    val STALE_AFTER: Duration = Duration.ofHours(STALE_AFTER_HOURS)

    /**
     * @param session the member's active session.
     * @param lastSetAt the `performed_at` of its most recent set, or null if it has none.
     * @param now the current instant.
     * @return the prompt to show, or null if the session is still live or already ended.
     */
    fun evaluate(
        session: WorkoutSession,
        lastSetAt: Instant?,
        now: Instant,
    ): StaleSessionPrompt? {
        val lastActivity = lastSetAt ?: session.startedAt
        val abandoned = session.isActive && Duration.between(lastActivity, now) > STALE_AFTER

        return when {
            !abandoned -> null
            lastSetAt == null -> StaleSessionPrompt.Discard(session)
            else -> StaleSessionPrompt.Finish(session, lastSetAt)
        }
    }
}
