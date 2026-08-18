package com.gymtracker.feature.logging

import java.time.Duration

/**
 * "mm:ss", so 90 seconds reads "1:30" rather than "PT1M30S" — shared by every clock-like readout
 * in this feature: the rest countdown, the warm-up stopwatch, guided mode's per-set clock, and
 * US-44's set-to-set interval. Consolidated 2026-08-17 (ADR-0036) from three near-identical
 * private copies (`RestPanel.kt`'s `asCountdown`, this file's own prior `asMinutesSeconds`, and
 * a third about to be added for the interval) — one more caller was the point where "copy it
 * again" stopped being the cheaper choice than a shared function.
 *
 * Arithmetic on [Duration.getSeconds] rather than `toMinutesPart`/`toSecondsPart`, which are
 * API 31 and would crash on the API 26 devices `tech-stack.md` supports.
 */
internal fun Duration.asMinutesSeconds(): String =
    "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)

private const val SECONDS_PER_MINUTE = 60
