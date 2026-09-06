package com.gymtracker.core.domain.rest

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The rest between sets (US-05).
 *
 * There is no countdown here. A rest is an end time, and how much is left is a question you
 * ask the clock — so killing the app mid-rest loses nothing, and reopening it simply
 * recomputes (ADR-0010).
 *
 * This gates nothing. Logging the next set is never blocked by a running rest, which US-05
 * requires and which is true here structurally: nothing else consults this class.
 */
class RestTimer(
    private val store: RestTimerStore,
    private val clock: Clock,
) {
    /** Starts a rest of the member's configured length, replacing any already running. */
    suspend fun start() {
        val rest = store.defaultRest.first()
        store.setRest(clock.instant().plus(rest), rest)
    }

    /** Ends the rest now. US-05: "I can dismiss or skip it." */
    suspend fun skip() {
        store.setRestEndsAt(null)
    }

    /**
     * Adds [by] to the running rest (US-05 as amended by ADR-0049): the end time *and* the pinned
     * total move together, through the same atomic write [start] uses, so a rest started at 1:00
     * and extended reads "0:45 of 1:30" rather than a bar that jumps past full. With no rest
     * running this does nothing — there is nothing to extend, and starting one from a stray tap
     * would be the app inventing a rest nobody earned.
     */
    suspend fun extend(by: Duration) {
        val endsAt = store.restEndsAt.first() ?: return
        val total = store.restTotal.first() ?: by
        store.setRest(endsAt.plus(by), total.plus(by))
    }

    /**
     * How much rest is left, or null when none is running or it has already elapsed.
     *
     * Null for "finished" rather than [Duration.ZERO]: a rest that is over is not a rest of
     * no time.
     */
    fun remaining(): Flow<Duration?> = store.restEndsAt.map { endsAt -> endsAt?.remaining() }

    /**
     * What the running rest was configured for, or null when none is running — a progress bar's
     * denominator. Deliberately not [RestTimerStore.defaultRest] read live: see that property's
     * own doc for why a rest already running must not visibly retime when the member changes
     * the default in Settings.
     */
    fun total(): Flow<Duration?> = store.restTotal

    private fun Instant.remaining(): Duration? =
        Duration.between(clock.instant(), this).takeIf { !it.isNegative && !it.isZero }

    companion object {
        /** What one tap of `+30S` adds (ADR-0049) — the design bundle's own figure. */
        val EXTENSION_STEP: Duration = Duration.ofSeconds(EXTENSION_SECONDS)
        private const val EXTENSION_SECONDS = 30L
    }
}
