package com.gymtracker.feature.logging

import com.gymtracker.core.domain.units.MinutesSeconds
import java.time.Duration

/**
 * "mm:ss", so 90 seconds reads "1:30" rather than "PT1M30S" — shared by every clock-like readout
 * in this feature: the rest countdown, the warm-up stopwatch, guided mode's per-set clock, and
 * US-44's set-to-set interval. Consolidated 2026-08-17 (ADR-0036) from three near-identical
 * private copies; since ADR-0050 the format itself lives in `:core:domain` ([MinutesSeconds]), so
 * the routine editor could read a movement's rest the same way without a fourth copy — this is
 * the same one function, reached through the receiver every caller here already uses.
 */
internal fun Duration.asMinutesSeconds(): String = MinutesSeconds.format(this)
