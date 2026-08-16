package com.gymtracker.core.domain.rest

import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant

/**
 * Where the rest timer lives between processes (ADR-0010).
 *
 * Device-local and unsynced, so DataStore per ADR-0005. Storing *when the rest ends* rather
 * than how much is left is what makes the timer survive the app being killed.
 */
interface RestTimerStore {
    /** When the running rest ends, or null if none is running. */
    val restEndsAt: Flow<Instant?>

    /**
     * How long the *running* rest was configured for when it started, or null if none is
     * running. Deliberately not the same value as [defaultRest]: US-42 promises that changing
     * the default in Settings does not retime a rest already running, and a progress bar reading
     * live off [defaultRest] would visibly break that promise — its fraction would jump even
     * though [restEndsAt] itself never moves. This is what [defaultRest] was pinned to at
     * [RestTimer.start] time, set atomically with [restEndsAt] so the two can never disagree
     * about which rest they describe.
     */
    val restTotal: Flow<Duration?>

    /** How long a rest lasts, 60 seconds until the member changes it (US-05). */
    val defaultRest: Flow<Duration>

    /** True until the notification permission has been requested once (US-05: never re-prompted). */
    val shouldAskForNotificationPermission: Flow<Boolean>

    suspend fun setRestEndsAt(instant: Instant?)

    /** Starts a rest atomically: the end time and the total it was configured for, together. */
    suspend fun setRest(
        endsAt: Instant,
        total: Duration,
    )

    suspend fun setDefaultRest(rest: Duration)

    suspend fun markNotificationPermissionAsked()
}
