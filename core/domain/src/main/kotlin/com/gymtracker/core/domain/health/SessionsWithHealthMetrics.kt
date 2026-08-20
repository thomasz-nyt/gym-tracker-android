package com.gymtracker.core.domain.health

import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.session.SessionRepository

/**
 * How many of a member's workouts carry imported health metrics (US-23).
 *
 * Read before [ForgetHealthMetrics] so the revoke offer can name a real number, and so no
 * offer is made at all when the answer is zero — an offer to delete nothing is exactly the
 * nag `health-connect.md` forbids.
 *
 * A workout whose read found no samples counts: US-22 stores a source marker with null values
 * in that case, which is still the app having looked, and still something to forget.
 */
class SessionsWithHealthMetrics(
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(userId: UserId): Int = sessions.countSessionsWithMetrics(userId)
}
