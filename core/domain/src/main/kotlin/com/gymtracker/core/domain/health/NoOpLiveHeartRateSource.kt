package com.gymtracker.core.domain.health

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The default [LiveHeartRateSource] binding (`tech-stack.md`'s optional-feature contract):
 * always [LiveHeartRate.Unavailable]. Every screen must render correctly against this
 * implementation — that is what the optional-feature test suite asserts.
 *
 * Lives in `:core:domain`, not `:feature:health`, for the same reason
 * [NoOpHealthMetricsSource] does: `:app` can bind the no-op path without depending on the
 * optional module at all.
 */
class NoOpLiveHeartRateSource : LiveHeartRateSource {
    override fun observe(): Flow<LiveHeartRate> = flowOf(LiveHeartRate.Unavailable)
}
