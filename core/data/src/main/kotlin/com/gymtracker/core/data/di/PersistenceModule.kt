package com.gymtracker.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.gymtracker.core.data.database.GymTrackerDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The two device-persistent stores, isolated from the repositories and use cases in
 * [DataModule] so instrumented tests can replace them without duplicating the app's graph.
 *
 * The production DataStore delegate remains process-wide. A Hilt singleton is per component,
 * and test components are recreated; constructing a second DataStore over the same file would
 * throw. The test graph replaces this whole module with memory-backed stores instead.
 */
@Module
@InstallIn(SingletonComponent::class)
object PersistenceModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): GymTrackerDatabase =
        Room
            .databaseBuilder(context, GymTrackerDatabase::class.java, GymTrackerDatabase.NAME)
            .addMigrations(
                GymTrackerDatabase.MIGRATION_1_2,
                GymTrackerDatabase.MIGRATION_2_3,
                GymTrackerDatabase.MIGRATION_3_4,
                GymTrackerDatabase.MIGRATION_4_5,
                GymTrackerDatabase.MIGRATION_5_6,
                GymTrackerDatabase.MIGRATION_6_7,
                GymTrackerDatabase.MIGRATION_7_8,
                GymTrackerDatabase.MIGRATION_8_9,
                GymTrackerDatabase.MIGRATION_9_10,
            ).build()

    @Provides
    @Singleton
    fun preferences(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.gymTrackerPreferences
}

private val Context.gymTrackerPreferences: DataStore<Preferences> by preferencesDataStore(name = "gym-tracker")
