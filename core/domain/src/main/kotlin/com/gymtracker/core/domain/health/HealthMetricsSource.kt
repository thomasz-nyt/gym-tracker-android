package com.gymtracker.core.domain.health

import com.gymtracker.core.domain.model.SessionMetrics
import java.time.Instant

/**
 * Health Connect as the domain sees it (M5, `specs/health-connect.md`) — no Android import,
 * no Health Connect import. The real implementation, [status] and [metricsFor] both re-check
 * live on every call rather than caching a result: a permission can be revoked in system
 * settings between two calls, and there is no process-death signal to invalidate a cache on.
 *
 * Health Connect is assumed absent (constitution §3): a household member may be a minor whose
 * account cannot use it, or the device may simply not have it, and the app must treat both the
 * same way. See [HealthStatus.Unavailable].
 */
interface HealthMetricsSource {
    /** Whether reads are possible right now. Re-derived on every call — see the class doc. */
    suspend fun status(): HealthStatus

    /**
     * Heart rate and active-calorie samples for [window], aggregated in memory to a session
     * summary. Raw samples never leave this function (constitution §5). Returns `null` if
     * [status] is not [HealthStatus.Ready], or if the window contains no samples at all — the
     * two cases the UI shows identically, as "not recorded" (constitution §2.4: never zero,
     * never estimated).
     */
    suspend fun metricsFor(window: ClosedRange<Instant>): SessionMetrics?
}

/**
 * The three states a caller can be in. Deliberately not four: `HealthConnectClient.
 * getSdkStatus()` distinguishes "not installed" from "installed, needs a provider update", but
 * both collapse to [Unavailable] here — see ADR-0038. There is exactly one negative branch, and
 * every UI that reads this type renders nothing for it, never a prompt.
 */
sealed interface HealthStatus {
    /**
     * Health Connect is not usable right now, for any of: not installed on this device, needs
     * a provider update, or the member's own per-device toggle (`HealthIntegration`) is off.
     * Callers cannot and must not distinguish which — see the class doc on [HealthMetricsSource].
     */
    data object Unavailable : HealthStatus

    /** The toggle is on and Health Connect is installed, but no permission has been granted yet. */
    data object PermissionRequired : HealthStatus

    /** At least one of the three permissions is granted; [HealthMetricsSource.metricsFor] can read. */
    data object Ready : HealthStatus
}
