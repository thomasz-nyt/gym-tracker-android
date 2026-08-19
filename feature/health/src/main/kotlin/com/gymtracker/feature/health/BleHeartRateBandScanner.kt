package com.gymtracker.feature.health

import com.gymtracker.core.domain.health.DiscoveredHeartRateBand
import com.gymtracker.core.domain.health.HeartRateBandAvailability
import com.gymtracker.core.domain.health.HeartRateBandScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The real [HeartRateBandScanner] (US-46, ADR-0039). Bound only when `:app`'s optional-feature
 * flag is on — the default binding stays
 * [com.gymtracker.core.domain.health.NoOpHeartRateBandScanner].
 */
class BleHeartRateBandScanner
    @Inject
    internal constructor(
        private val gateway: HeartRateBandGateway,
    ) : HeartRateBandScanner {
        override fun availability(): HeartRateBandAvailability =
            when {
                !gateway.isSupported() -> HeartRateBandAvailability.Unavailable
                !gateway.hasScanPermission() || !gateway.hasConnectPermission() ->
                    HeartRateBandAvailability.PermissionRequired
                else -> HeartRateBandAvailability.Ready
            }

        override fun scan(): Flow<DiscoveredHeartRateBand> {
            if (!gateway.isSupported() || !gateway.hasScanPermission()) return emptyFlow()

            return gateway.scanForDevices().map { DiscoveredHeartRateBand(it.address, it.name) }
        }
    }
