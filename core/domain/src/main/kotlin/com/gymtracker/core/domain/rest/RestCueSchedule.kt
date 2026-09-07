package com.gymtracker.core.domain.rest

import java.time.Duration
import java.time.Instant

/**
 * When a running rest cues the member ahead of its end (US-05 as amended by ADR-0049).
 *
 * One pure rule, so the coordinator that schedules the alarm and the test that pins it read the
 * same line: ten seconds before the end — the same moment ADR-0036 turns the countdown's numeral
 * red, now made audible and tactile for a phone nobody is looking at. The cue at zero needs no
 * rule of its own; it rides the rest-over alarm ADR-0010 already fires.
 */
object RestCueSchedule {
    /** How far ahead of the end the pre-cue lands. */
    val LEAD: Duration = Duration.ofSeconds(LEAD_SECONDS)

    /**
     * @return when to cue ahead of [endsAt], or null when that moment is not ahead of [now] — a
     *   rest shorter than [LEAD], or one already inside its last ten seconds, gets no pre-cue
     *   rather than one fired late or immediately.
     */
    fun cueAt(
        endsAt: Instant,
        now: Instant,
    ): Instant? = endsAt.minus(LEAD).takeIf { it.isAfter(now) }

    private const val LEAD_SECONDS = 10L
}
