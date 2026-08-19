package com.gymtracker.app.di

import com.gymtracker.BuildConfig
import com.gymtracker.core.domain.health.HealthMetricsSource
import com.gymtracker.core.domain.health.HeartRateBandScanner
import com.gymtracker.core.domain.health.LiveHeartRateSource
import com.gymtracker.core.domain.health.NoOpHealthMetricsSource
import com.gymtracker.core.domain.health.NoOpHeartRateBandScanner
import com.gymtracker.core.domain.health.NoOpLiveHeartRateSource
import com.gymtracker.feature.health.BleHeartRateBandScanner
import com.gymtracker.feature.health.BleHeartRateSource
import com.gymtracker.feature.health.HealthConnectMetricsSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Wires [HealthMetricsSource] and [LiveHeartRateSource] to their real implementations only
 * when the optional-feature flag is on (`app/build.gradle.kts`'s `OPTIONAL_FEATURES_ENABLED`,
 * ADR-0038/ADR-0039) — the default bindings stay [NoOpHealthMetricsSource] and
 * [NoOpLiveHeartRateSource], per `tech-stack.md`'s optional-feature contract. `:app` is the
 * only module that depends on both `:core:domain` and `:feature:health`, so this is where the
 * choice has to live; neither of the two feature-side classes knows the other exists.
 */
@Module
@InstallIn(SingletonComponent::class)
object HealthModule {
    @Provides
    fun noOpHealthMetricsSource(): NoOpHealthMetricsSource = NoOpHealthMetricsSource()

    @Provides
    fun healthMetricsSource(
        real: HealthConnectMetricsSource,
        noOp: NoOpHealthMetricsSource,
    ): HealthMetricsSource = if (BuildConfig.OPTIONAL_FEATURES_ENABLED) real else noOp

    @Provides
    fun noOpLiveHeartRateSource(): NoOpLiveHeartRateSource = NoOpLiveHeartRateSource()

    @Provides
    fun liveHeartRateSource(
        real: BleHeartRateSource,
        noOp: NoOpLiveHeartRateSource,
    ): LiveHeartRateSource = if (BuildConfig.OPTIONAL_FEATURES_ENABLED) real else noOp

    @Provides
    fun noOpHeartRateBandScanner(): NoOpHeartRateBandScanner = NoOpHeartRateBandScanner()

    @Provides
    fun heartRateBandScanner(
        real: BleHeartRateBandScanner,
        noOp: NoOpHeartRateBandScanner,
    ): HeartRateBandScanner = if (BuildConfig.OPTIONAL_FEATURES_ENABLED) real else noOp
}
