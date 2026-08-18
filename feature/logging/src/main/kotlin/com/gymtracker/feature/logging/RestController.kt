package com.gymtracker.feature.logging

import com.gymtracker.core.domain.rest.RestTimer
import com.gymtracker.core.domain.rest.RestTimerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * The rest between sets (US-05), split out of `ActiveSessionViewModel` for the same reason
 * set entry was: the screen now covers the session, the catalog, set entry and the rest, and
 * only the first of those is really the ViewModel's own job.
 *
 * Everything here is derived from the stored end time, so nothing needs restoring after the
 * process is killed (ADR-0010).
 */
class RestController(
    private val restTimer: RestTimer,
    private val store: RestTimerStore,
    private val scope: CoroutineScope,
) {
    private val started = MutableStateFlow(false)

    /** Flips when a rest begins, so the screen knows to schedule the notification. */
    val restStarted: StateFlow<Boolean> = started

    /** Starts the rest that follows a logged set. */
    suspend fun startAfterSet() {
        restTimer.start()
        started.value = true
    }

    /** US-05: "I can dismiss or skip it." */
    fun skip() {
        scope.launch { restTimer.skip() }
    }

    /** When the running rest ends, for the alarm. Null if none is running. */
    suspend fun endsAt(): Instant? = store.restEndsAt.first()

    suspend fun shouldAskForNotifications(): Boolean = store.shouldAskForNotificationPermission.first()

    fun onNotificationPermissionAsked() {
        scope.launch { store.markNotificationPermissionAsked() }
    }

    /** Acknowledges that the alarm for this rest has been scheduled. */
    fun onHandled() {
        started.value = false
    }

    /**
     * The rest countdown, re-read every second so it moves: how much is left, and what it was
     * configured for when it started (a progress bar's denominator — [RestTimer.total]'s own
     * doc explains why that is a different question from [RestTimerStore.defaultRest]).
     *
     * Both values come from the same tick, read together rather than as two independently
     * combined flows. `ActiveSessionViewModel`'s own class docs name exactly this trap for
     * `SessionData`: two flows updated by separate writes can each push their own emission for
     * one underlying change, and a `combine` over both would observe a genuinely transient
     * state in between — [remaining] already moved, [total] not yet, or the reverse. Reading
     * both inside the one tick is what makes them arrive as a single atomic value instead.
     *
     * The tick is only a redraw signal. Both values always come from the stored end time and
     * total against the clock, so a missed tick cannot make either drift.
     */
    fun reading(): Flow<RestReading> =
        flow {
            while (true) {
                emit(RestReading(remaining = restTimer.remaining().first(), total = restTimer.total().first()))
                delay(TICK_MILLIS)
            }
        }.distinctUntilChanged()

    private companion object {
        const val TICK_MILLIS = 1_000L
    }
}

/** One tick of [RestController.reading] — the countdown and what it started from, together. */
data class RestReading(
    val remaining: Duration?,
    val total: Duration?,
)
