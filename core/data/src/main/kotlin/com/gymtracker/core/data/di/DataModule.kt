package com.gymtracker.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.member.DataStoreCurrentMember
import com.gymtracker.core.data.session.RoomSessionRepository
import com.gymtracker.core.data.session.SessionDao
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.session.StartSession
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.util.UUID
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): GymTrackerDatabase =
        Room
            .databaseBuilder(context, GymTrackerDatabase::class.java, GymTrackerDatabase.NAME)
            .build()

    @Provides
    fun sessionDao(database: GymTrackerDatabase): SessionDao = database.sessionDao()

    @Provides
    @Singleton
    fun preferences(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("gym-tracker") }

    /** UTC everywhere; the presentation layer is the only place a time zone belongs. */
    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemUTC()

    /**
     * Ids are generated here rather than by the database so a session has its identity before
     * it is written — US-03 needs a set to be persistable the instant it is confirmed.
     */
    @Provides
    fun startSession(
        sessions: SessionRepository,
        clock: Clock,
    ): StartSession =
        StartSession(sessions = sessions, clock = clock, newId = { SessionId(UUID.randomUUID().toString()) })
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindings {
    @Binds
    abstract fun sessionRepository(impl: RoomSessionRepository): SessionRepository

    @Binds
    abstract fun currentMember(impl: DataStoreCurrentMember): CurrentMember
}
