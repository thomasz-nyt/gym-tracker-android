package com.gymtracker.core.domain.units

import java.time.Duration

/**
 * "m:ss" for a clock-like readout — 90 seconds reads "1:30", never "PT1M30S" and never "90 s":
 * the rest countdown, the warm-up stopwatch, guided mode's per-set clock, US-44's set-to-set
 * interval, and (ADR-0050) a movement's own rest wherever its target is read out.
 *
 * Lifted here from `:feature:logging`'s `Durations.kt` (ADR-0036 consolidated three copies
 * there) on its first caller outside that feature — the routine editor's target line — which
 * delegates to this rather than copying the format a fourth time. Arithmetic on
 * [Duration.getSeconds] rather than `toMinutesPart`/`toSecondsPart`, which are API 31 and would
 * crash on the API 26 devices `tech-stack.md` supports.
 */
object MinutesSeconds {
    fun format(duration: Duration): String =
        "%d:%02d".format(duration.seconds / SECONDS_PER_MINUTE, duration.seconds % SECONDS_PER_MINUTE)

    private const val SECONDS_PER_MINUTE = 60
}
