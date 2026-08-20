package com.gymtracker.feature.health

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the internal SDK seams — see [HealthConnectGateway]'s and [HeartRateBandGateway]'s
 * class docs for why they exist.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class HealthConnectModule {
    @Binds
    abstract fun healthConnectGateway(impl: AndroidHealthConnectGateway): HealthConnectGateway

    @Binds
    abstract fun heartRateBandGateway(impl: AndroidHeartRateBandGateway): HeartRateBandGateway
}
