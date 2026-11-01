package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.ReopenedWorkout
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.set.SetRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration

/** Reopens only the latest recent finish, with no intervening start (US-63). */
class ReopenWorkout(
    private val sessions: SessionRepository,
    private val sets: SetRepository,
    private val clock: Clock,
) {
    /** Returns false when ownership, recency or the no-new-workout boundary is not met. */
    suspend operator fun invoke(
        id: SessionId,
        member: UserId,
    ): Boolean {
        val session = sessions.findSession(id)
        return if (session != null && canReopen(session, member)) {
            val endedAt = checkNotNull(session.endedAt)
            val ids =
                sets
                    .observeForSessions(listOf(id))
                    .first()
                    .map { it.id }
                    .toSet()
            sessions.updateSession(
                session.copy(
                    endedAt = null,
                    workflow = session.workflow.copy(reopened = ReopenedWorkout(endedAt, ids)),
                ),
            )
            true
        } else {
            false
        }
    }

    private suspend fun canReopen(
        session: WorkoutSession,
        member: UserId,
    ): Boolean {
        val endedAt = session.endedAt ?: return false
        val age = Duration.between(endedAt, clock.instant())
        val ownedAndEligible = session.userId == member && session.workflow.reopenEligible
        val latest =
            sessions
                .observeFinishedSessions(member)
                .first()
                .firstOrNull()
                ?.id == session.id
        val withinWindow = !age.isNegative && age <= REOPEN_WINDOW
        return ownedAndEligible && latest && withinWindow && sessions.findActiveSession(member) == null
    }

    companion object {
        private const val REOPEN_MINUTES = 10L

        /** The agreed accidental-finish recovery window. */
        val REOPEN_WINDOW: Duration = Duration.ofMinutes(REOPEN_MINUTES)
    }
}
