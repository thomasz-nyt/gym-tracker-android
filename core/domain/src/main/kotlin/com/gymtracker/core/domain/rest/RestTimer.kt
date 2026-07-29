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
        store.setRestEndsAt(clock.instant().plus(rest))
    }

    /** Ends the rest now. US-05: "I can dismiss or skip it." */
    suspend fun skip() {
        store.setRestEndsAt(null)
    }

    /**
     * How much rest is left, or null when none is running or it has already elapsed.
     *
     * Null for "finished" rather than [Duration.ZERO]: a rest that is over is not a rest of
     * no time.
     */
    fun remaining(): Flow<Duration?> = store.restEndsAt.map { endsAt -> endsAt?.remaining() }

    private fun Instant.remaining(): Duration? =
        Duration.between(clock.instant(), this).takeIf { !it.isNegative && !it.isZero }
}
