package com.gymtracker.core.domain.progress

import java.time.LocalDate

/**
 * One session's worth of one exercise (US-16).
 *
 * Every nullable field here is nullable for the same reason: a session of bodyweight sets has
 * no load, and absence is a state rather than a zero (constitution §2.4). [sets] is not
 * nullable, because the sets happened whatever they weighed.
 */
data class ExerciseTrendPoint(
    val performedOn: LocalDate,
    /** The heaviest set actually lifted that day. Null when nothing was loaded. */
    val topSetKg: Double?,
    /** Σ weight × reps over the loaded sets. Null when none of them were. */
    val volumeKg: Double?,
    /** The best [Epley] estimate of the day, and an estimate wherever it is shown. */
    val estimatedOneRepMaxKg: Double?,
    val sets: Int,
)

/**
 * An exercise over time — and, when there is not enough of it, the honest absence (US-19).
 *
 * **US-19 is enforced by this type rather than remembered by a screen.** "With a single data
 * point, no trend line is drawn and no trend is claimed" holds because [SinglePoint] hands
 * out one point and no list: there is nothing for a line to be drawn through, and no field a
 * slope could be read from. The same trick ADR-0023 used to stop the rest panel rendering
 * "set 3 of 5" when nothing knows what 5 would be.
 */
sealed interface ExerciseTrend {
    /** The member has never performed this exercise. Charts say so; they do not draw a zero. */
    data object NoData : ExerciseTrend

    /** Performed exactly once. A dot, and no trend. */
    data class SinglePoint(
        val point: ExerciseTrendPoint,
    ) : ExerciseTrend

    /** Two or more sessions, oldest first — a chart reads left to right in time. */
    data class Series(
        val points: List<ExerciseTrendPoint>,
    ) : ExerciseTrend
}
