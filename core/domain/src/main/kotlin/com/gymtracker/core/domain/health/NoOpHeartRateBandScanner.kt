package com.gymtracker.core.domain.health

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** The default [HeartRateBandScanner] binding: always unavailable, finds nothing. */
class NoOpHeartRateBandScanner : HeartRateBandScanner {
    override fun availability(): HeartRateBandAvailability = HeartRateBandAvailability.Unavailable

    override fun scan(): Flow<DiscoveredHeartRateBand> = emptyFlow()
}
