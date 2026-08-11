package com.gymtracker.core.domain.progress

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import com.gymtracker.core.domain.set.SetRepository
import kotlinx.coroutines.flow.first

/**
 * The lift the Progress tab's top section leads with, without asking (US-33): whatever the
 * member most recently actually trained.
 *
 * **Only the newest finished session is considered.** A routine copies in every one of its
 * movements whether or not the member reaches them (US-29), so that session's first appearance
 * by position is not necessarily one that was performed — this looks past untouched appearances
 * to the first one with a set logged. If *none* of that session's appearances were performed, the
 * result is null even if an older session has something to show: reaching back through history
 * for a lift to feature would make the section describe the past, not "since you last trained."
 * A null result is US-19's absence, not a bug — the caller says so plainly.
 */
class MostRecentlyTrainedExercise(
    private val sessions: SessionRepository,
    private val sessionExercises: SessionExerciseRepository,
    private val sets: SetRepository,
) {
    suspend operator fun invoke(member: UserId): ExerciseId? {
        val mostRecent = sessions.observeFinishedSessions(member).first().firstOrNull() ?: return null
        val appearances = sessionExercises.observeForSession(mostRecent.id).first().sortedBy { it.position }
        val performedIds =
            sets
                .observeForSessions(listOf(mostRecent.id))
                .first()
                .map { it.sessionExerciseId }
                .toSet()
        return appearances.firstOrNull { it.id in performedIds }?.exerciseId
    }
}
