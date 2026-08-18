package com.gymtracker.feature.health

import androidx.health.connect.client.HealthConnectClient
import com.gymtracker.core.domain.health.HealthIntegration
import com.gymtracker.core.domain.health.HealthMetricsSource
import com.gymtracker.core.domain.health.HealthStatus
import com.gymtracker.core.domain.model.SessionMetrics
import java.time.Instant
import javax.inject.Inject

/**
 * The real [HealthMetricsSource] (M5). Bound only when `:app`'s optional-feature flag enables
 * the health module (ADR-0038) — the default binding stays
 * [com.gymtracker.core.domain.health.NoOpHealthMetricsSource].
 *
 * [status] re-derives every gate on every call, never caching a result: the SDK status can
 * change (installed mid-session), the toggle can change (US-21/US-23), and a permission can be
 * revoked in system settings between two launches — `specs/health-connect.md` requires all three
 * to be re-checked, not assumed stable.
 */
class HealthConnectMetricsSource
    @Inject
    internal constructor(
        private val gateway: HealthConnectGateway,
        private val healthIntegration: HealthIntegration,
    ) : HealthMetricsSource {
        override suspend fun status(): HealthStatus {
            // The SDK's two negative statuses (not installed, needs a provider update) and the
            // member's own toggle being off all collapse to the same Unavailable, in this order
            // (ADR-0038) — a caller can never tell which of the three produced it.
            val sdkAvailable = gateway.sdkStatus() == HealthConnectClient.SDK_AVAILABLE
            val toggledOn = healthIntegration.current()

            return when {
                !sdkAvailable || !toggledOn -> HealthStatus.Unavailable
                gateway.grantedPermissions().isEmpty() -> HealthStatus.PermissionRequired
                else -> HealthStatus.Ready
            }
        }

        /**
         * Stubbed for this PR — always `null`. US-20/US-21 (this PR) build the availability
         * check and the opt-in; the read itself is US-22's PR, per ADR-0038.
         */
        override suspend fun metricsFor(window: ClosedRange<Instant>): SessionMetrics? = null
    }
