package com.gymtracker.core.domain.progress

import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import com.gymtracker.core.domain.set.SetRepository
import kotlinx.coroutines.flow.first

/**
 * Which of the member's finished sessions contain at least one personal record (US-38), for the
 * Progress list's `PR` badge.
 *
 * [PersonalRecordsAchievedIn] answers the same question correctly for one session, but it does
 * so by re-reading and re-judging the member's entire lifting history once per set in that
 * session — the right cost for [com.gymtracker.feature.logging.FinishSummaryScreen]'s one row
 * (US-31), the wrong one for every visible row of a 200-session list (the gap US-33 itself
 * deferred). This instead reads every loaded set the member has ever logged exactly once, and
 * walks each (exercise, reps) group in chronological order — the same "first time is not a
 * record, and beating has to be strict" rule [DetectPersonalRecord] already defines (ADR-0025),
 * computed as one pass instead of one query per set.
 */
class SessionsWithRecords(
    private val sessions: SessionRepository,
    private val sessionExercises: SessionExerciseRepository,
    private val sets: SetRepository,
) {
    suspend operator fun invoke(member: UserId): Set<SessionId> {
        val sessionIds = sessions.observeFinishedSessions(member).first().map { it.id }
        if (sessionIds.isEmpty()) return emptySet()

        val appearanceById = sessionExercises.observeForSessions(sessionIds).first().associateBy { it.id }
        val loadedSets = sets.observeForSessions(sessionIds).first().filter { it.weightKg != null }

        val recordSessions = mutableSetOf<SessionId>()
        loadedSets
            .groupBy { set -> appearanceById[set.sessionExerciseId]?.exerciseId to set.reps }
            .values
            .forEach { atThisRepCount ->
                var best = Double.NEGATIVE_INFINITY
                atThisRepCount.sortedBy { it.performedAt }.forEachIndexed { index, set ->
                    val weight = set.weightKg!!
                    // The first appearance at a rep count establishes the baseline, per
                    // DetectPersonalRecord's own rule — it is not itself a record.
                    if (index > 0 && weight > best) {
                        appearanceById[set.sessionExerciseId]?.sessionId?.let { recordSessions.add(it) }
                    }
                    best = maxOf(best, weight)
                }
            }
        return recordSessions
    }
}
