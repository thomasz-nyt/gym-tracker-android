package com.gymtracker.core.domain.health

import com.gymtracker.core.domain.model.SessionMetrics
import java.time.Instant

/**
 * Health Connect as the domain sees it (M5, `specs/health-connect.md`) — no Android import,
 * no Health Connect import. [status] reflects only the SDK and the OS-level permission grant,
 * **not** the member's own opt-in ([HealthIntegration]) — see [HealthStatus]'s class doc for why
 * the two are deliberately independent. Both [status] and [metricsFor] re-check live on every
 * call rather than caching a result: a permission can be revoked in system settings between two
 * calls, and there is no process-death signal to invalidate a cache on.
 *
 * Health Connect is assumed absent (constitution §3): a household member may be a minor whose
 * account cannot use it, or the device may simply not have it, and the app must treat both the
 * same way. See [HealthStatus.Unavailable].
 */
interface HealthMetricsSource {
    /** Whether the SDK and OS permissions allow a read right now. Re-derived on every call. */
    suspend fun status(): HealthStatus

    /**
     * Heart rate and active-calorie samples for [window], aggregated in memory to a session
     * summary. Raw samples never leave this function (constitution §5). Returns `null` if
     * [status] is not [HealthStatus.Ready], or if the window contains no samples at all — the
     * two cases the UI shows identically, as "not recorded" (constitution §2.4: never zero,
     * never estimated). Callers must additionally check [HealthIntegration] before calling this
     * — see the class doc.
     */
    suspend fun metricsFor(window: ClosedRange<Instant>): SessionMetrics?
}

/**
 * The three states the SDK and its OS-level permissions can be in. Deliberately not four:
 * `HealthConnectClient.getSdkStatus()` distinguishes "not installed" from "installed, needs a
 * provider update", but both collapse to [Unavailable] here — see ADR-0038. There is exactly
 * one negative branch, and every UI that reads this type renders nothing for it, never a prompt.
 *
 * **Deliberately independent of [HealthIntegration]**, the member's own per-device opt-in.
 * Folding the toggle in here was tried first and reverted (ADR-0038): if `Unavailable` meant
 * either "not installed" *or* "toggle off", the Settings screen could never legitimately show
 * its own toggle control at all, since the value it would read to decide whether to render the
 * control is the same value the control itself is about to change — nothing could ever turn it
 * on. The toggle stays a second, orthogonal gate: Settings shows its control once this type is
 * not [Unavailable] (the device and account can use Health Connect, regardless of whether this
 * member has opted in yet — `health-connect.md`'s "no settings row that leads nowhere"), and a
 * read only happens when both this status is [Ready] **and** [HealthIntegration.current] is
 * true.
 */
sealed interface HealthStatus {
    /** Not installed, needs a provider update, or otherwise not usable on this device/account. */
    data object Unavailable : HealthStatus

    /** Health Connect is installed, but no permission has been granted yet. */
    data object PermissionRequired : HealthStatus

    /** At least one of the three permissions is granted; [HealthMetricsSource.metricsFor] can read. */
    data object Ready : HealthStatus
}
