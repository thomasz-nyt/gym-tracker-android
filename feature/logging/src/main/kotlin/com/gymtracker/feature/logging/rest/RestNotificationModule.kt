package com.gymtracker.feature.logging.rest

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the rest notification's two seams (ADR-0046).
 *
 * They exist as interfaces so [RestNotificationCoordinator] — which holds the one rule this
 * feature can actually get wrong — is a plain JUnit test with no Robolectric and no
 * `AlarmManager`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RestNotificationModule {
    @Binds
    @Singleton
    abstract fun restAlarms(impl: RestAlarm): RestAlarms

    @Binds
    @Singleton
    abstract fun restNotifier(impl: RestNotification): RestNotifier
}
