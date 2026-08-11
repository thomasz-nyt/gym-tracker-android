package com.gymtracker.core.domain.progress

/**
 * US-33's top section: the current estimated 1RM, and how it has moved over roughly the last
 * 8 weeks.
 *
 * @property currentKg the latest session's estimate — never the latest *point*, if that point
 *   happened to be all bodyweight and carried no estimate of its own.
 * @property deltaKg the change since the point closest to (but not after) 8 weeks earlier, or
 *   null when history does not reach back that far. Comparing against a two-week-old point and
 *   calling it "the 8-week change" would not be honest, so the line is omitted rather than
 *   mislabelled (constitution §2.4).
 */
data class EightWeekChange(
    val currentKg: Double,
    val deltaKg: Double?,
)

private const val EIGHT_WEEKS_DAYS = 56L

/**
 * Reads [ExerciseTrend.Series.points] rather than a repository, so this is testable as pure
 * data — the same split [Epley] draws between the arithmetic and the read that feeds it.
 */
fun ExerciseTrend.Series.eightWeekChangeInEstimate(): EightWeekChange? {
    val withEstimate = points.mapNotNull { point -> point.estimatedOneRepMaxKg?.let { point.performedOn to it } }
    val (latestDate, latestValue) = withEstimate.lastOrNull() ?: return null

    val eightWeeksAgo = latestDate.minusDays(EIGHT_WEEKS_DAYS)
    val delta =
        withEstimate
            .lastOrNull { (date, _) -> !date.isAfter(eightWeeksAgo) }
            ?.second
            ?.let { earlier -> latestValue - earlier }

    return EightWeekChange(currentKg = latestValue, deltaKg = delta)
}
