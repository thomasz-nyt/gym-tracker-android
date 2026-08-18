package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.ExerciseSet
import java.time.Duration

/**
 * "How long since the last set" (US-44, `Redesign.dc.html` Turn 3, frame `3g`) — a read over
 * [ExerciseSet.performedAt] alone, so it costs no migration and is correct retroactively on
 * every session already logged. Sits beside [SessionSummary] and [SessionDetail] as another
 * aggregate over a session's own sets, not a new concept.
 *
 * Two different numbers both read as "per-set time" (Turn 3's own diagnosis): time-under-load
 * needs a start event only guided mode has and a column `sets` does not have; this is the other
 * one, the set-to-set interval, which needs neither.
 */
object SetIntervals {
    /**
     * The gap since the set logged immediately before each one, keyed by [ExerciseSet.id].
     *
     * [sessionSets] is every set in the session, not one exercise's — intervals deliberately
     * span movements, since the walk to the next machine is the number that explains a long
     * session (Turn 3's own example: "the +3:41 is walking to the cable stack"). The list is
     * sorted by [ExerciseSet.performedAt] here rather than trusted to already be in order.
     *
     * The first set in the sorted list has no entry: there is nothing before it to measure
     * from. Neither does a set whose gap is under [SUPPRESSION_FLOOR] — [LogSets] loops writing
     * several sets with near-identical timestamps (ADR-0009's bulk entry), and rendering that as
     * "+0:00" would read as a bug, the same complaint the redesign audit made about the history
     * summary line (constitution §2.4: an unreliable number is worse absent than shown wrong).
     */
    fun of(sessionSets: List<ExerciseSet>): Map<String, Duration> {
        val sorted = sessionSets.sortedBy { it.performedAt }
        val intervals = mutableMapOf<String, Duration>()
        for (index in 1 until sorted.size) {
            val gap = Duration.between(sorted[index - 1].performedAt, sorted[index].performedAt)
            if (gap >= SUPPRESSION_FLOOR) intervals[sorted[index].id] = gap
        }
        return intervals
    }

    /**
     * The average interval among [exerciseSets]' own consecutive sets — an exercise's internal
     * pace, not counting the lead-in from whatever came before its first set.
     *
     * [exerciseSets] is one exercise's own sets; [intervals] is [of]'s session-wide result. The
     * first set (by [ExerciseSet.performedAt]) is always excluded here regardless of what its
     * own interval was: that gap is inherited from the previous movement, which [of] still
     * attaches to the row for the reason above, but which is not "how fast this movement went".
     * Null when there are fewer than two sets, or every remaining gap was suppressed — never
     * zero, per constitution §2.4.
     */
    fun average(
        exerciseSets: List<ExerciseSet>,
        intervals: Map<String, Duration>,
    ): Duration? {
        val internal = exerciseSets.sortedBy { it.performedAt }.drop(1).mapNotNull { intervals[it.id] }
        if (internal.isEmpty()) return null
        return Duration.ofMillis(internal.sumOf { it.toMillis() } / internal.size)
    }

    /** Below this, a gap reads as bulk entry (ADR-0009) rather than time actually spent resting. */
    private val SUPPRESSION_FLOOR: Duration = Duration.ofSeconds(SUPPRESSION_FLOOR_SECONDS)
    private const val SUPPRESSION_FLOOR_SECONDS = 5L
}
