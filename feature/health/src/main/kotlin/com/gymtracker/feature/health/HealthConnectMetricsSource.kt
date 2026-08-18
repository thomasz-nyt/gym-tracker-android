package com.gymtracker.feature.health

import androidx.health.connect.client.HealthConnectClient
import com.gymtracker.core.domain.health.HealthMetricsSource
import com.gymtracker.core.domain.health.HealthPermission
import com.gymtracker.core.domain.health.HealthStatus
import com.gymtracker.core.domain.model.SessionMetrics
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * The real [HealthMetricsSource] (M5). Bound only when `:app`'s optional-feature flag enables
 * the health module (ADR-0038) — the default binding stays
 * [com.gymtracker.core.domain.health.NoOpHealthMetricsSource].
 *
 * Carries no dependency on [com.gymtracker.core.domain.health.HealthIntegration] — the member's
 * toggle is a second, orthogonal gate a caller combines with [status] itself, not something this
 * class folds in (see [HealthStatus]'s class doc for why that was tried and reverted).
 *
 * [status] re-derives every gate on every call, never caching a result: the SDK status can
 * change (installed mid-session) and a permission can be revoked in system settings between two
 * launches — `specs/health-connect.md` requires both to be re-checked, not assumed stable.
 */
class HealthConnectMetricsSource
    @Inject
    internal constructor(
        private val gateway: HealthConnectGateway,
    ) : HealthMetricsSource {
        override suspend fun status(): HealthStatus {
            // The SDK's two negative statuses — not installed, needs a provider update — both
            // collapse to the same Unavailable (ADR-0038); a caller can never tell which
            // produced it.
            if (gateway.sdkStatus() != HealthConnectClient.SDK_AVAILABLE) return HealthStatus.Unavailable

            return if (gateway.grantedPermissions().isEmpty()) {
                HealthStatus.PermissionRequired
            } else {
                HealthStatus.Ready
            }
        }

        /**
         * US-22. Each of the three permissions gates its own read independently — a member who
         * granted only active calories gets that one metric and nulls for the rest, never a
         * refused read overall (`health-connect.md`'s "partial permissions" case). The exercise
         * permission, if granted, narrows the window the other two run over to the actual
         * recorded session rather than the app's own start/end, which can run a little wide of
         * what a wearable considered "the workout."
         */
        override suspend fun metricsFor(window: ClosedRange<Instant>): SessionMetrics? {
            if (status() != HealthStatus.Ready) return null

            val granted = gateway.grantedPermissions()
            val refined =
                if (HealthPermission.EXERCISE.id in granted) {
                    gateway.exerciseSessionWindow(window) ?: window
                } else {
                    window
                }

            val heartRate =
                if (HealthPermission.HEART_RATE.id in granted) gateway.heartRateBpm(refined) else emptyList()
            val activeCalories =
                if (HealthPermission.ACTIVE_CALORIES.id in granted) gateway.activeCaloriesKcal(refined) else emptyList()

            return SessionMetrics(
                avgHeartRate = heartRate.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                maxHeartRate = heartRate.maxOrNull()?.toInt(),
                activeKilocalories = activeCalories.takeIf { it.isNotEmpty() }?.sum()?.roundToInt(),
                source = SOURCE,
            )
        }

        private companion object {
            const val SOURCE = "health_connect"
        }
    }
