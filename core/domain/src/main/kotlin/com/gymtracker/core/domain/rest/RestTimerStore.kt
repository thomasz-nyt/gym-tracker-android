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

    /** How long a rest lasts, 90 seconds until the member changes it (US-05). */
    val defaultRest: Flow<Duration>

    /** True until the notification permission has been requested once (US-05: never re-prompted). */
    val shouldAskForNotificationPermission: Flow<Boolean>

    suspend fun setRestEndsAt(instant: Instant?)

    suspend fun setDefaultRest(rest: Duration)

    suspend fun markNotificationPermissionAsked()
}
