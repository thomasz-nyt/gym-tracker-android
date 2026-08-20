package com.gymtracker.core.domain.health

import kotlinx.coroutines.flow.Flow

/**
 * The member's own opt-in and chosen device for live heart rate (US-46, ADR-0039),
 * device-local per ADR-0005 — not part of the US-40/41 backup envelope, for the same reason
 * [HealthIntegration] isn't: restoring someone else's export must never turn reads on for a
 * device that has never paired a band.
 *
 * [deviceAddress] is kept independently of [enabled] and survives the toggle being turned off
 * (US-49 drops the *connection*, not the *pairing* — the same convention system Bluetooth
 * settings use, so turning it back on does not force re-scanning for a band already chosen).
 */
interface HeartRateBandPreference {
    /** Emits again whenever the member changes either field. */
    fun observe(): Flow<HeartRateBandSelection>

    /** The current setting. Defaults to off, no device (US-46). */
    suspend fun current(): HeartRateBandSelection

    suspend fun setEnabled(enabled: Boolean)

    /** The chosen band's Bluetooth address, or `null` to forget it. */
    suspend fun setDevice(address: String?)
}

data class HeartRateBandSelection(
    val enabled: Boolean,
    val deviceAddress: String?,
)
