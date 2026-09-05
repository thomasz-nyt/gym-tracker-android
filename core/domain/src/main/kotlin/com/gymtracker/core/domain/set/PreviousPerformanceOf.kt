package com.gymtracker.core.domain.set

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * Everything the member lifted the last time they did a movement — every set of that
 * appearance, in order, and the day it happened (US-61).
 *
 * History, never a target (the same rule [LastPerformance] follows for a routine's row): each
 * number here is one someone performed. Kilograms, the canonical unit — this is read, not typed.
 */
data class PreviousPerformance(
    /** The day, taken from the appearance's first set. */
    val performedAt: Instant,
    /** In set order, as they were done. Never empty — an appearance with no sets is not a performance. */
    val sets: List<ExerciseSet>,
)

/**
 * The most recent earlier appearance of an exercise, in full (US-61).
 *
 * ADR-0023's rest panel shows the *last set* of the last time — one line, a number to beat.
 * Progressive overload is paced against the whole of last time: three sets of eight at 135 is a
 * different starting point from eight, seven, five. This reads the appearance that set belongs
 * to and returns all of it, through two reads the repository already has, so it costs no query
 * and is correct for every session ever logged.
 *
 * Distinct from [PrefillFromLastSet] (one set, converted, about to be typed) and from
 * [LastPerformanceOf] (one set, for a routine's row, in any session): this excludes the running
 * session, the way the rest panel's own comparison does — today's sets are on the screen already.
 */
class PreviousPerformanceOf(
    private val sets: SetRepository,
) {
    /**
     * @param excludingSessionId the running session, whose own sets are not "last time".
     * @return null when the member has never done [exerciseId] before this session.
     */
    suspend operator fun invoke(
        exerciseId: ExerciseId,
        member: UserId,
        excludingSessionId: SessionId,
    ): PreviousPerformance? {
        val last = sets.lastSetOfBefore(exerciseId, member, excludingSessionId) ?: return null
        val appearance = sets.observeForSessionExercise(last.sessionExerciseId).first().sortedBy { it.setIndex }
        // Empty only if the row `lastSetOfBefore` found has gone between the two reads — then
        // there is no performance to describe, same as never having done it.
        return appearance
            .takeIf { it.isNotEmpty() }
            ?.let { PreviousPerformance(performedAt = it.minOf { set -> set.performedAt }, sets = it) }
    }
}
