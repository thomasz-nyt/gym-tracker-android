package com.gymtracker.app.di

import com.gymtracker.BuildConfig
import com.gymtracker.core.domain.health.HealthMetricsSource
import com.gymtracker.core.domain.health.NoOpHealthMetricsSource
import com.gymtracker.feature.health.HealthConnectMetricsSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Wires [HealthMetricsSource] to the real implementation only when the optional-feature flag
 * is on (`app/build.gradle.kts`'s `OPTIONAL_FEATURES_ENABLED`, ADR-0038) — the default binding
 * stays [NoOpHealthMetricsSource], per `tech-stack.md`'s optional-feature contract. `:app` is
 * the only module that depends on both `:core:domain` and `:feature:health`, so this is where
 * the choice has to live; neither of the two feature-side classes knows the other exists.
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
}
