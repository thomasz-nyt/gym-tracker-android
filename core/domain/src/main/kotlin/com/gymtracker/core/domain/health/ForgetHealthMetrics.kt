package com.gymtracker.core.domain.health

import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.session.SessionRepository

/**
 * Deletes every health metric a member has imported (US-23, ADR-0040).
 *
 * The workouts themselves survive: their start, end, routine, exercises and sets are the
 * member's own work, and only the borrowed metrics go. Afterwards each cleared workout is
 * indistinguishable from one logged before the member ever opted in — the source marker goes
 * with the numbers, so nothing renders "not recorded", which under US-22 would claim a read
 * happened and found nothing.
 *
 * Separate from turning the toggle off, which stops future reads on its own
 * ([RecordSessionMetrics] gates on [HealthIntegration]) and does not depend on this running.
 */
class ForgetHealthMetrics(
    private val sessions: SessionRepository,
) {
    /** Clears [userId]'s imported metrics, returning how many workouts actually changed. */
    suspend operator fun invoke(userId: UserId): Int = sessions.clearMetrics(userId)
}
