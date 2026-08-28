package com.gymtracker

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.di.PersistenceModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Singleton

/**
 * Gives every instrumented test component persistence that cannot touch the installed app's
 * workouts and cannot leak into the next test (`testing-strategy.md`).
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [PersistenceModule::class],
)
object TestPersistenceModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): GymTrackerDatabase = Room.inMemoryDatabaseBuilder(context, GymTrackerDatabase::class.java).build()

    @Provides
    @Singleton
    fun preferences(): DataStore<Preferences> = InMemoryPreferencesDataStore()
}

/** A complete DataStore implementation with no file and one serial update path. */
private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val mutex = Mutex()
    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock {
            transform(state.value).also { state.value = it }
        }
}
