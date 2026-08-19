package com.gymtracker.core.domain.health

import kotlinx.coroutines.flow.Flow

/**
 * A live, transient heart-rate reading from a directly-paired band (US-46 … US-49, ADR-0039) —
 * not [HealthMetricsSource]. That port is a `suspend` read over a store, for a session that has
 * already ended; this one is a `Flow` over a Bluetooth connection that can drop and reconnect
 * mid-observation. Nothing this emits is ever persisted (constitution §5): it is display-only,
 * and never written to Room or the backup envelope.
 *
 * `:core:domain` has no Android or Bluetooth import — everything device-specific lives behind
 * `:feature:health`'s gateway seam.
 */
interface LiveHeartRateSource {
    /** Re-derives on every subscription; never caches a stale connection state across calls. */
    fun observe(): Flow<LiveHeartRate>
}

/**
 * The states a live reading can be in. [Searching] and [Lost] are deliberately distinct from
 * [Beating] and from each other (US-48): neither is ever rendered as if it carried a current
 * BPM, which is what keeps a display built on this type honest under constitution §2.4.
 */
sealed interface LiveHeartRate {
    /** No Bluetooth adapter, below API 31, no permission granted, or the toggle is off. */
    data object Unavailable : LiveHeartRate

    /** The toggle is on and a device is chosen, but no connection is established yet. */
    data object Searching : LiveHeartRate

    /** A current reading from the paired band. */
    data class Beating(
        val bpm: Int,
        val energyExpendedKilocalories: Int?,
    ) : LiveHeartRate

    /** Was connected; the connection dropped or the last reading went stale (US-48). */
    data object Lost : LiveHeartRate
}
