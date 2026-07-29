package com.gymtracker.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.exercise.AndroidCatalogAssetReader
import com.gymtracker.core.data.exercise.CatalogAssetReader
import com.gymtracker.core.data.exercise.ExerciseDao
import com.gymtracker.core.data.exercise.RoomExerciseCatalog
import com.gymtracker.core.data.member.DataStoreCurrentMember
import com.gymtracker.core.data.member.DataStoreUnitPreference
import com.gymtracker.core.data.session.RoomSessionRepository
import com.gymtracker.core.data.session.SessionDao
import com.gymtracker.core.data.sessionexercise.RoomSessionExerciseRepository
import com.gymtracker.core.data.sessionexercise.SessionExerciseDao
import com.gymtracker.core.data.set.RoomSetRepository
import com.gymtracker.core.data.set.SetDao
import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.session.StartSession
import com.gymtracker.core.domain.sessionexercise.AddExerciseToSession
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import com.gymtracker.core.domain.set.LogSet
import com.gymtracker.core.domain.set.LogSets
import com.gymtracker.core.domain.set.PrefillFromLastSet
import com.gymtracker.core.domain.set.SetRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
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
            .addMigrations(
                GymTrackerDatabase.MIGRATION_1_2,
                GymTrackerDatabase.MIGRATION_2_3,
                GymTrackerDatabase.MIGRATION_3_4,
                GymTrackerDatabase.MIGRATION_4_5,
            ).build()

    @Provides
    fun sessionDao(database: GymTrackerDatabase): SessionDao = database.sessionDao()

    @Provides
    fun exerciseDao(database: GymTrackerDatabase): ExerciseDao = database.exerciseDao()

    @Provides
    fun sessionExerciseDao(database: GymTrackerDatabase): SessionExerciseDao = database.sessionExerciseDao()

    @Provides
    fun setDao(database: GymTrackerDatabase): SetDao = database.setDao()

    @Provides
    fun logSet(
        sets: SetRepository,
        clock: Clock,
    ): LogSet = LogSet(sets, clock) { UUID.randomUUID().toString() }

    @Provides
    fun logSets(logSet: LogSet): LogSets = LogSets(logSet)

    @Provides
    fun prefillFromLastSet(sets: SetRepository): PrefillFromLastSet = PrefillFromLastSet(sets)

    @Provides
    fun addExerciseToSession(sessionExercises: SessionExerciseRepository): AddExerciseToSession =
        AddExerciseToSession(sessionExercises) { SessionExerciseId(UUID.randomUUID().toString()) }

    /** Lenient about unknown keys so a catalog gaining a field does not break older installs. */
    @Provides
    @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    fun catalogAssetReader(
        @ApplicationContext context: Context,
    ): CatalogAssetReader = AndroidCatalogAssetReader(context)

    /**
     * One DataStore per file per process, enforced by the delegate rather than by `@Singleton`.
     *
     * A Hilt singleton is per component, and components are recreated — between instrumented
     * tests, for instance — which produces a second DataStore over the same file and throws.
     * The property delegate is process-wide, so it cannot happen.
     */
    @Provides
    @Singleton
    fun preferences(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.gymTrackerPreferences

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

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

    @Binds
    abstract fun unitPreference(impl: DataStoreUnitPreference): UnitPreference

    @Binds
    abstract fun exerciseCatalog(impl: RoomExerciseCatalog): ExerciseCatalog

    @Binds
    abstract fun sessionExercises(impl: RoomSessionExerciseRepository): SessionExerciseRepository

    @Binds
    abstract fun sets(impl: RoomSetRepository): SetRepository
}

private val Context.gymTrackerPreferences: DataStore<Preferences> by preferencesDataStore(name = "gym-tracker")
