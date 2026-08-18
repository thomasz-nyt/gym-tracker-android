package com.gymtracker.feature.health

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the internal SDK seam — see [HealthConnectGateway]'s class doc for why it exists. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class HealthConnectModule {
    @Binds
    abstract fun healthConnectGateway(impl: AndroidHealthConnectGateway): HealthConnectGateway
}
