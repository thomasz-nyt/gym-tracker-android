package com.gymtracker.core.domain.warmup

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The warm-up before the lifting starts (US-28).
 *
 * A stopwatch that **records nothing**. It counts up, it has no target and no end, and
 * stopping it discards the stopwatch. It adds no set or separate activity to history. The
 * workout's Start-to-Finish duration includes this elapsed time (ADR-0052). Nothing is
 * inferred or written as performed work (constitution §2.4).
 *
 * There is no ticking here. A warm-up is a start time, and how long it has run is a
 * question you ask the clock — so killing the app mid-warm-up loses nothing, and reopening
 * simply recomputes.
 *
 * This gates nothing. Nothing else in the domain consults this class, which is what keeps
 * a warm-up from becoming a step in anything.
 */
class WarmUpTimer(
    private val store: WarmUpTimerStore,
    private val clock: Clock,
) {
    /**
     * Starts a warm-up, or leaves a running one alone.
     *
     * Deliberately not "restart": unlike a rest, which is replaced every time a set is
     * logged, a warm-up you are already eight minutes into is the thing you would lose.
     */
    suspend fun start() {
        if (store.warmUpStartedAt.first() == null) {
            store.setWarmUpStartedAt(clock.instant())
        }
    }

    /** Ends the warm-up and discards it. There is nothing to write, so nothing is written. */
    suspend fun stop() {
        store.setWarmUpStartedAt(null)
    }

    /**
     * How long the warm-up has been running, or null when none is.
     *
     * Null means "not running", so a warm-up that has only just begun reads as
     * [Duration.ZERO] rather than null — it is running, and zero is the honest answer.
     * That is the opposite of the rest timer, where null means the rest is over.
     */
    fun elapsed(): Flow<Duration?> = store.warmUpStartedAt.map { startedAt -> startedAt?.elapsed() }

    private fun Instant.elapsed(): Duration = Duration.between(this, clock.instant())
}
