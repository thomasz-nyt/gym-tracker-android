package com.gymtracker.core.domain.progress

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-33's top section: "the change over the last 8 weeks" for a chosen lift's estimated 1RM.
 *
 * A pure computation over an already-fetched [ExerciseTrend.Series], so it is testable here
 * without a repository — the same split [Epley] and [WeeklyVolumeByBodyPart] use for the math
 * behind a screen.
 */
class EightWeekChangeTest {
    private fun point(
        date: String,
        estimateKg: Double?,
    ) = ExerciseTrendPoint(
        performedOn = LocalDate.parse(date),
        topSetKg = null,
        volumeKg = null,
        estimatedOneRepMaxKg = estimateKg,
        sets = 1,
    )

    @Test
    fun `the delta compares against the point closest to eight weeks before the latest`() {
        val series =
            ExerciseTrend.Series(
                listOf(
                    point("2026-06-01", 117.0), // well over 8 weeks before 8/4
                    point("2026-07-14", 120.0), // within the window, not the comparison point
                    point("2026-08-04", 124.0),
                ),
            )

        val change = series.eightWeekChangeInEstimate()

        assertEquals(124.0, change?.currentKg)
        assertEquals(7.0, change?.deltaKg)
    }

    @Test
    fun `history younger than eight weeks has a current estimate but no delta`() {
        val series =
            ExerciseTrend.Series(
                listOf(
                    point("2026-07-28", 120.0),
                    point("2026-08-04", 124.0),
                ),
            )

        val change = series.eightWeekChangeInEstimate()

        assertEquals(124.0, change?.currentKg)
        assertNull(change?.deltaKg, "comparing against a two-week-old point would not be an honest 8-week change")
    }

    @Test
    fun `a session that was all bodyweight is skipped when finding the latest estimate`() {
        val series =
            ExerciseTrend.Series(
                listOf(
                    point("2026-06-01", 117.0),
                    point("2026-08-04", null), // most recent, but nothing to estimate from
                ),
            )

        // The latest point with an estimate, not the most recent point overall.
        val change = series.eightWeekChangeInEstimate()

        assertEquals(117.0, change?.currentKg)
    }

    @Test
    fun `no point ever carried an estimate reports absence, not a claim`() {
        val series = ExerciseTrend.Series(listOf(point("2026-08-01", null), point("2026-08-04", null)))

        assertNull(series.eightWeekChangeInEstimate())
    }
}
