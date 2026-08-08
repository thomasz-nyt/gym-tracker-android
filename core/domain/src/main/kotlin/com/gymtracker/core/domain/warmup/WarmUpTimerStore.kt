package com.gymtracker.core.domain.warmup

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Where the warm-up lives between processes (ADR-0021).
 *
 * Device-local and unsynced, so DataStore per ADR-0005 — and DataStore is the *only* place
 * it lives. A warm-up has no row in `session_exercises`, no row in `sets`, and no column on
 * `sessions`, because a second kind of thing a session can contain is an activity type and
 * constitution §1 refuses one.
 *
 * Storing *when the warm-up began* rather than how long it has run is what makes it survive
 * the app being killed. The rest timer stores the mirror image for the same reason: it
 * counts down, so its absolute instant is an end (ADR-0010).
 */
interface WarmUpTimerStore {
    /** When the running warm-up began, or null if none is running. */
    val warmUpStartedAt: Flow<Instant?>

    /** Records the start of a warm-up, or clears the running one when given null. */
    suspend fun setWarmUpStartedAt(instant: Instant?)
}
