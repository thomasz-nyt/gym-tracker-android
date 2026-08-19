package com.gymtracker.core.domain.health

import kotlinx.coroutines.flow.Flow

/**
 * Nearby devices advertising the Bluetooth Heart Rate service, for the Settings pairing walk
 * (US-46, ADR-0039). A separate port from [LiveHeartRateSource]: scanning for candidates and
 * holding a live connection to the one already chosen are different operations with different
 * lifetimes, and `:feature:settings` (which needs only this one) must not have to depend on
 * `:feature:health`'s connection machinery to render a device list — `:feature:settings`
 * depends only on `:core:domain`, never on another feature module.
 */
interface HeartRateBandScanner {
    /**
     * Whether the device/OS can support this at all, independent of [HeartRateBandPreference]'s
     * toggle — the same split [HealthStatus] draws from [HealthIntegration], for the same
     * reason: Settings needs to decide whether to show its toggle *before* knowing whether the
     * member has turned it on, and a status that folded the two together could never say yes.
     */
    fun availability(): HeartRateBandAvailability

    /** Emits a [DiscoveredHeartRateBand] for each device found, until the collector cancels. */
    fun scan(): Flow<DiscoveredHeartRateBand>
}

sealed interface HeartRateBandAvailability {
    /** No Bluetooth adapter, or below API 31 (ADR-0039 restricts this feature to API 31+). */
    data object Unavailable : HeartRateBandAvailability

    /** The device can support this, but `BLUETOOTH_SCAN` and/or `BLUETOOTH_CONNECT` is not granted yet. */
    data object PermissionRequired : HeartRateBandAvailability

    /** Both permissions granted; scanning and connecting can proceed. */
    data object Ready : HeartRateBandAvailability
}

data class DiscoveredHeartRateBand(
    val address: String,
    val name: String?,
)

/**
 * The scan could not be started at all — as distinct from a scan that ran and found nothing
 * (US-48's honesty rule applied to pairing: "searching" and "searching is broken" must not look
 * the same on screen). [errorCode] is the platform's own `ScanCallback.SCAN_FAILED_*` value,
 * passed through rather than interpreted, since `:core:domain` cannot name those constants.
 */
class ScanFailedException(
    val errorCode: Int,
) : Exception("Bluetooth scan failed to start (code $errorCode)")
