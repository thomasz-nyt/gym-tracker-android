package com.gymtracker.core.domain.routine

import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.session.SessionRepository
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * Which of the member's routines Train home leads with when no workout is running (US-36):
 * the one it has been longest since they did, or — for a routine never done at all — before
 * any routine that has been.
 *
 * **This is the first reader of [com.gymtracker.core.domain.model.RoutineOrigin.id].** ADR-0028
 * wrote it once at session start and left it unread, in so many words, until "a future story (a
 * per-routine count, a 'last run of this routine' comparison) does not have to leave a permanent
 * gap for every session logged before that story is written." This is that story: the id is used
 * only to match a finished session back to the routine it came from, never rendered — what
 * renders is [Routine.name], read fresh through [RoutineRepository], never the session's own
 * (possibly stale, per that same ADR) copy of it.
 *
 * A routine deleted after being performed drops out of consideration the same way any deleted
 * routine does — [RoutineRepository.observeRoutines] simply stops listing it — so a dangling
 * `routine.id` on an old session is inert here, not a bug to guard against.
 */
class NextRoutineToTrain(
    private val routines: RoutineRepository,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(member: UserId): Routine? {
        val all = routines.observeRoutines(member).first()
        if (all.isEmpty()) return null

        val lastPerformed = mutableMapOf<String, Instant>()
        sessions.observeFinishedSessions(member).first().forEach { session ->
            val routineId = session.routine?.id ?: return@forEach
            val current = lastPerformed[routineId]
            if (current == null || session.startedAt.isAfter(current)) {
                lastPerformed[routineId] = session.startedAt
            }
        }

        // Never performed sorts as Instant.MIN — before anything with a real timestamp — and
        // ties (including "every routine is new") break by list position, so the result is
        // deterministic rather than whatever order a map happened to iterate in.
        return all.minWithOrNull(
            compareBy(
                { lastPerformed[it.id.value] ?: Instant.MIN },
                { it.position },
            ),
        )
    }
}
