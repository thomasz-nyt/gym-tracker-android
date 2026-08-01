package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import com.gymtracker.core.domain.set.SetRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * The member's finished workouts, newest first, each summarised (US-06).
 *
 * Only finished sessions are listed. That is not only tidiness: it is what makes it
 * impossible to delete the session you are currently in (US-06a), because it is never on
 * screen to delete. Ending or discarding that one is US-01 and US-06.
 *
 * The exercises and sets for the whole list are fetched in one query each and the arithmetic
 * happens here, rather than being summed in SQL — `specs/testing-strategy.md` puts volume
 * where it can be tested against hand-computed fixtures.
 */
class SessionHistory(
    private val sessions: SessionRepository,
    private val sessionExercises: SessionExerciseRepository,
    private val sets: SetRepository,
) {
    /** Re-emits whenever anything it counts changes, so a delete needs to notify nothing. */
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(member: UserId): Flow<List<SessionSummary>> =
        sessions.observeFinishedSessions(member).flatMapLatest { finished ->
            if (finished.isEmpty()) {
                flowOf(emptyList())
            } else {
                val ids = finished.map { it.id }
                combine(
                    sessionExercises.observeForSessions(ids),
                    sets.observeForSessions(ids),
                ) { exercises, performed ->
                    finished.map { SessionSummary.of(it, exercises, performed) }
                }
            }
        }
}
