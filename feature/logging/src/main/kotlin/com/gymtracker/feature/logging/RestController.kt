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
    private val rationale = MutableStateFlow(false)

    /**
     * Flips when a rest begins *on this screen*, which is the one moment it is appropriate to
     * ask for notification permission (US-05).
     *
     * Deliberately not the same signal as `restEndsAt` going non-null, which the notification
     * itself follows (ADR-0046). That one also fires when `LOG SET` is tapped from the shade
     * with the app in the background — a fine moment to post a notification, and a terrible one
     * to raise a permission dialog.
     */
    val restStarted: StateFlow<Boolean> = started

    /**
     * Starts the rest that follows a logged set — [rest] when the movement's own target names one
     * (US-30 as amended by ADR-0050), else the member's default.
     */
    suspend fun startAfterSet(rest: Duration? = null) {
        restTimer.start(rest)
        markStarted()
    }

    /**
     * Notes that a rest has begun without starting one.
     *
     * For the one-tap path, where `LogUpNextSet` starts the rest itself so the notification's
     * `LOG SET` gets the same behaviour without a second implementation (US-54). Calling
     * [startAfterSet] there would re-time the rest that use case has already started.
     */
    fun markStarted() {
        started.value = true
    }

    /** US-05: "I can dismiss or skip it." */
    fun skip() {
        scope.launch { restTimer.skip() }
    }

    /** `+30S` (ADR-0049): thirty more seconds on the running rest, end time and total together. */
    fun extend() {
        scope.launch { restTimer.extend(RestTimer.EXTENSION_STEP) }
    }

    suspend fun shouldAskForNotifications(): Boolean = store.shouldAskForNotificationPermission.first()

    /**
     * US-05 as amended (2026-09-05): one in-app line saying what rest alerts are for, before
     * Android's own prompt — showing for the first rest ever, and only when there is something to
     * ask (the route decides that; this only holds whether the line is up).
     */
    val notificationRationale: StateFlow<Boolean> = rationale

    fun offerNotificationRationale() {
        rationale.value = true
    }

    /** Either answer counts as asked — once, never re-prompted (US-05). */
    fun onNotificationRationaleAnswered() {
        rationale.value = false
        scope.launch { store.markNotificationPermissionAsked() }
    }

    /** Acknowledges that this rest's permission check has been made. */
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
