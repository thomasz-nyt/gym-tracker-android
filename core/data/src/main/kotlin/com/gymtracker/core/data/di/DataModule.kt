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
import com.gymtracker.core.data.guided.DataStoreGuidedPlanStore
import com.gymtracker.core.data.member.DataStoreCurrentMember
import com.gymtracker.core.data.member.DataStoreUnitPreference
import com.gymtracker.core.data.rest.DataStoreRestTimerStore
import com.gymtracker.core.data.routine.RoomRoutineItemRepository
import com.gymtracker.core.data.routine.RoomRoutineRepository
import com.gymtracker.core.data.routine.RoutineDao
import com.gymtracker.core.data.routine.RoutineItemDao
import com.gymtracker.core.data.session.RoomSessionRepository
import com.gymtracker.core.data.session.SessionDao
import com.gymtracker.core.data.sessionexercise.RoomSessionExerciseRepository
import com.gymtracker.core.data.sessionexercise.SessionExerciseDao
import com.gymtracker.core.data.set.RoomSetRepository
import com.gymtracker.core.data.set.SetDao
import com.gymtracker.core.data.warmup.DataStoreWarmUpTimerStore
import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.guided.GuidedPlanStore
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.progress.DetectPersonalRecord
import com.gymtracker.core.domain.progress.ExerciseLogOf
import com.gymtracker.core.domain.progress.ExerciseTrendOf
import com.gymtracker.core.domain.progress.MostRecentlyTrainedExercise
import com.gymtracker.core.domain.progress.PersonalRecordsAchievedIn
import com.gymtracker.core.domain.progress.PersonalRecordsOf
import com.gymtracker.core.domain.progress.WeeklyVolumeByBodyPart
import com.gymtracker.core.domain.rest.DetermineUpNextSet
import com.gymtracker.core.domain.rest.RestTimer
import com.gymtracker.core.domain.rest.RestTimerStore
import com.gymtracker.core.domain.routine.AddExerciseToRoutine
import com.gymtracker.core.domain.routine.CreateRoutine
import com.gymtracker.core.domain.routine.DeleteRoutine
import com.gymtracker.core.domain.routine.MoveExerciseInRoutine
import com.gymtracker.core.domain.routine.RemoveExerciseFromRoutine
import com.gymtracker.core.domain.routine.RenameRoutine
import com.gymtracker.core.domain.routine.RoutineItemRepository
import com.gymtracker.core.domain.routine.RoutineRepository
import com.gymtracker.core.domain.routine.StartSessionFromRoutine
import com.gymtracker.core.domain.session.DeleteSession
import com.gymtracker.core.domain.session.EndSession
import com.gymtracker.core.domain.session.RestoreSession
import com.gymtracker.core.domain.session.SessionHistory
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.session.StartSession
import com.gymtracker.core.domain.session.WorkoutDetail
import com.gymtracker.core.domain.sessionexercise.AddExerciseToSession
import com.gymtracker.core.domain.sessionexercise.RemoveExerciseFromSession
import com.gymtracker.core.domain.sessionexercise.RestoreExerciseToSession
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import com.gymtracker.core.domain.set.DeleteSet
import com.gymtracker.core.domain.set.LastPerformanceOf
import com.gymtracker.core.domain.set.LogSet
import com.gymtracker.core.domain.set.LogSets
import com.gymtracker.core.domain.set.PrefillFromLastSet
import com.gymtracker.core.domain.set.RestoreSet
import com.gymtracker.core.domain.set.SetRepository
import com.gymtracker.core.domain.set.UpdateSet
import com.gymtracker.core.domain.warmup.WarmUpTimer
import com.gymtracker.core.domain.warmup.WarmUpTimerStore
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
import java.time.ZoneId
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
                GymTrackerDatabase.MIGRATION_5_6,
                GymTrackerDatabase.MIGRATION_6_7,
                GymTrackerDatabase.MIGRATION_7_8,
                GymTrackerDatabase.MIGRATION_8_9,
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
    fun routineDao(database: GymTrackerDatabase): RoutineDao = database.routineDao()

    @Provides
    fun routineItemDao(database: GymTrackerDatabase): RoutineItemDao = database.routineItemDao()

    @Provides
    fun createRoutine(routines: RoutineRepository): CreateRoutine =
        CreateRoutine(routines) { RoutineId(UUID.randomUUID().toString()) }

    @Provides
    fun addExerciseToRoutine(items: RoutineItemRepository): AddExerciseToRoutine =
        AddExerciseToRoutine(items) { RoutineItemId(UUID.randomUUID().toString()) }

    @Provides
    fun removeExerciseFromRoutine(items: RoutineItemRepository): RemoveExerciseFromRoutine =
        RemoveExerciseFromRoutine(items)

    @Provides
    fun moveExerciseInRoutine(items: RoutineItemRepository): MoveExerciseInRoutine = MoveExerciseInRoutine(items)

    @Provides
    fun renameRoutine(routines: RoutineRepository): RenameRoutine = RenameRoutine(routines)

    @Provides
    fun deleteRoutine(routines: RoutineRepository): DeleteRoutine = DeleteRoutine(routines)

    @Provides
    fun startSessionFromRoutine(
        routines: RoutineRepository,
        items: RoutineItemRepository,
        startSession: StartSession,
        addExerciseToSession: AddExerciseToSession,
    ): StartSessionFromRoutine = StartSessionFromRoutine(routines, items, startSession, addExerciseToSession)

    @Provides
    fun logSet(
        sets: SetRepository,
        clock: Clock,
    ): LogSet = LogSet(sets, clock) { UUID.randomUUID().toString() }

    @Provides
    fun logSets(logSet: LogSet): LogSets = LogSets(logSet)

    @Provides
    fun restTimer(
        store: RestTimerStore,
        clock: Clock,
    ): RestTimer = RestTimer(store, clock)

    @Provides
    fun warmUpTimer(
        store: WarmUpTimerStore,
        clock: Clock,
    ): WarmUpTimer = WarmUpTimer(store, clock)

    @Provides
    fun prefillFromLastSet(sets: SetRepository): PrefillFromLastSet = PrefillFromLastSet(sets)

    @Provides
    fun lastPerformanceOf(sets: SetRepository): LastPerformanceOf = LastPerformanceOf(sets)

    /**
     * The member's own zone — the one exception to the UTC rule above, and a deliberate one.
     *
     * Instants are stored and compared in UTC. But a chart's unit is a *day*, and which day a
     * 23:30 session belongs to is a question only the member's zone can answer. Bucketing in
     * UTC would move a late Sunday workout into Monday for anyone west of Greenwich, which is
     * a wrong answer rather than a rounding one.
     */
    @Provides
    fun zone(): ZoneId = ZoneId.systemDefault()

    @Provides
    fun exerciseTrendOf(
        sessions: SessionRepository,
        sessionExercises: SessionExerciseRepository,
        sets: SetRepository,
        zone: ZoneId,
    ): ExerciseTrendOf = ExerciseTrendOf(sessions, sessionExercises, sets, zone)

    @Provides
    fun mostRecentlyTrainedExercise(
        sessions: SessionRepository,
        sessionExercises: SessionExerciseRepository,
        sets: SetRepository,
    ): MostRecentlyTrainedExercise = MostRecentlyTrainedExercise(sessions, sessionExercises, sets)

    @Provides
    fun exerciseLogOf(
        sessions: SessionRepository,
        sessionExercises: SessionExerciseRepository,
        sets: SetRepository,
        zone: ZoneId,
    ): ExerciseLogOf = ExerciseLogOf(sessions, sessionExercises, sets, zone)

    @Provides
    fun personalRecordsOf(
        sessions: SessionRepository,
        sessionExercises: SessionExerciseRepository,
        sets: SetRepository,
        zone: ZoneId,
    ): PersonalRecordsOf = PersonalRecordsOf(sessions, sessionExercises, sets, zone)

    @Provides
    fun detectPersonalRecord(
        recordsOf: PersonalRecordsOf,
        zone: ZoneId,
    ): DetectPersonalRecord = DetectPersonalRecord(recordsOf, zone)

    @Provides
    fun personalRecordsAchievedIn(detect: DetectPersonalRecord): PersonalRecordsAchievedIn =
        PersonalRecordsAchievedIn(detect)

    @Provides
    fun weeklyVolumeByBodyPart(
        sessions: SessionRepository,
        sessionExercises: SessionExerciseRepository,
        sets: SetRepository,
        catalog: ExerciseCatalog,
        zone: ZoneId,
    ): WeeklyVolumeByBodyPart = WeeklyVolumeByBodyPart(sessions, sessionExercises, sets, catalog, zone)

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
        restTimerStore: RestTimerStore,
        clock: Clock,
    ): StartSession =
        StartSession(
            sessions = sessions,
            restTimerStore = restTimerStore,
            clock = clock,
            newId = { SessionId(UUID.randomUUID().toString()) },
        )

    @Provides
    fun endSession(
        sessions: SessionRepository,
        sets: SetRepository,
        clock: Clock,
    ): EndSession = EndSession(sessions, sets, clock)

    @Provides
    fun sessionHistory(
        sessions: SessionRepository,
        sessionExercises: SessionExerciseRepository,
        sets: SetRepository,
    ): SessionHistory = SessionHistory(sessions, sessionExercises, sets)

    @Provides
    fun deleteSession(
        sessions: SessionRepository,
        sessionExercises: SessionExerciseRepository,
        sets: SetRepository,
    ): DeleteSession = DeleteSession(sessions, sessionExercises, sets)

    @Provides
    fun restoreSession(
        sessions: SessionRepository,
        sessionExercises: SessionExerciseRepository,
        sets: SetRepository,
    ): RestoreSession = RestoreSession(sessions, sessionExercises, sets)

    @Provides
    fun workoutDetail(
        sessions: SessionRepository,
        sessionExercises: SessionExerciseRepository,
        sets: SetRepository,
        catalog: ExerciseCatalog,
    ): WorkoutDetail = WorkoutDetail(sessions, sessionExercises, sets, catalog)

    @Provides
    fun removeExerciseFromSession(
        sessionExercises: SessionExerciseRepository,
        sets: SetRepository,
    ): RemoveExerciseFromSession = RemoveExerciseFromSession(sessionExercises, sets)

    @Provides
    fun restoreExerciseToSession(
        sessionExercises: SessionExerciseRepository,
        sets: SetRepository,
    ): RestoreExerciseToSession = RestoreExerciseToSession(sessionExercises, sets)

    @Provides
    fun determineUpNextSet(
        sessionExercises: SessionExerciseRepository,
        sets: SetRepository,
        prefillFromLastSet: PrefillFromLastSet,
    ): DetermineUpNextSet = DetermineUpNextSet(sessionExercises, sets, prefillFromLastSet)

    @Provides
    fun updateSet(sets: SetRepository): UpdateSet = UpdateSet(sets)

    @Provides
    fun deleteSet(sets: SetRepository): DeleteSet = DeleteSet(sets)

    @Provides
    fun restoreSet(sets: SetRepository): RestoreSet = RestoreSet(sets)
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
    abstract fun restTimerStore(impl: DataStoreRestTimerStore): RestTimerStore

    @Binds
    abstract fun warmUpTimerStore(impl: DataStoreWarmUpTimerStore): WarmUpTimerStore

    @Binds
    abstract fun routines(impl: RoomRoutineRepository): RoutineRepository

    @Binds
    abstract fun routineItems(impl: RoomRoutineItemRepository): RoutineItemRepository

    @Binds
    abstract fun guidedPlanStore(impl: DataStoreGuidedPlanStore): GuidedPlanStore

    @Binds
    abstract fun exerciseCatalog(impl: RoomExerciseCatalog): ExerciseCatalog

    @Binds
    abstract fun sessionExercises(impl: RoomSessionExerciseRepository): SessionExerciseRepository

    @Binds
    abstract fun sets(impl: RoomSetRepository): SetRepository
}

private val Context.gymTrackerPreferences: DataStore<Preferences> by preferencesDataStore(name = "gym-tracker")
