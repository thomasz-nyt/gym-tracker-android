package com.gymtracker.core.data.backup

import androidx.room.withTransaction
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.routine.toDomain
import com.gymtracker.core.data.routine.toEntity
import com.gymtracker.core.data.session.toDomain
import com.gymtracker.core.data.session.toEntity
import com.gymtracker.core.data.sessionexercise.toDomain
import com.gymtracker.core.data.sessionexercise.toEntity
import com.gymtracker.core.data.set.toDomain
import com.gymtracker.core.data.set.toEntity
import com.gymtracker.core.data.sync.SyncEntityNames
import com.gymtracker.core.data.sync.SyncPayloadCodec
import com.gymtracker.core.data.sync.syncWriteEntry
import com.gymtracker.core.domain.backup.BackupContents
import com.gymtracker.core.domain.backup.BackupStore
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.rest.RestTimerStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * [BackupStore] over Room and DataStore (US-40, US-41, ADR-0034).
 *
 * Takes [GymTrackerDatabase] itself rather than one DAO per table — every other Room-backed
 * repository in this codebase is scoped to a single table and takes a single DAO, but this one
 * is deliberately not: it spans all five backed-up tables, and [replaceAll] needs
 * `database.withTransaction` regardless, so the database is the natural thing to hold.
 *
 * [replaceAll] enqueues every row it restores, exactly like an ordinary write — US-57's own
 * decision, not a special case for how the row arrived (ADR-0043's amendment). It does **not**
 * enqueue a delete for whatever [replaceAll] wipes beforehand; that question is named, not
 * answered, in this session's own notes — see the M2 roadmap entry.
 */
class RoomBackupStore
    @Inject
    constructor(
        private val database: GymTrackerDatabase,
        private val unitPreference: UnitPreference,
        private val restTimerStore: RestTimerStore,
        private val currentMember: CurrentMember,
        private val codec: SyncPayloadCodec,
    ) : BackupStore {
        override suspend fun read(memberId: UserId): BackupContents {
            val userId = memberId.value
            return database.withTransaction {
                BackupContents(
                    memberId = memberId,
                    unit = unitPreference.current(),
                    restDefault = restTimerStore.defaultRest.first(),
                    sessions = database.sessionDao().allForUser(userId).map { it.toDomain() },
                    sessionExercises = database.sessionExerciseDao().allForUser(userId).map { it.toDomain() },
                    sets = database.setDao().allForUser(userId).map { it.toDomain() },
                    routines = database.routineDao().allForUser(userId).map { it.toDomain() },
                    routineItems = database.routineItemDao().allForUser(userId).map { it.toDomain() },
                )
            }
        }

        /**
         * SQL replacement rolls back on write failure; preference changes are compensated.
         * IO/SQLite/cancellation span unrelated exception hierarchies; rethrow after compensation.
         */
        @Suppress("TooGenericExceptionCaught")
        override suspend fun replaceAll(contents: BackupContents) {
            val previousMember = currentMember.id()
            val previousUnit = unitPreference.current()
            val previousRest = restTimerStore.defaultRest.first()
            try {
                database.withTransaction {
                    check(database.sessionDao().findActive(previousMember.value) == null) { "A workout is running" }
                    setOf(previousMember.value, contents.memberId.value).forEach { userId ->
                        database.sessionDao().deleteAllForUser(userId)
                        database.routineDao().deleteAllForUser(userId)
                    }

                    val sessions = contents.sessions.map { it.toEntity() }
                    database.sessionDao().insertAll(sessions)
                    sessions.forEach { enqueueRestoredRow(SyncEntityNames.SESSIONS, it.id, codec.encode(it)) }

                    val sessionExercises = contents.sessionExercises.map { it.toEntity() }
                    database.sessionExerciseDao().insertAll(sessionExercises)
                    sessionExercises.forEach {
                        enqueueRestoredRow(
                            SyncEntityNames.SESSION_EXERCISES,
                            it.id,
                            codec.encode(it),
                        )
                    }

                    val sets = contents.sets.map { it.toEntity() }
                    database.setDao().insertAll(sets)
                    sets.forEach { enqueueRestoredRow(SyncEntityNames.SETS, it.id, codec.encode(it)) }

                    val routines = contents.routines.map { it.toEntity() }
                    database.routineDao().insertAll(routines)
                    routines.forEach { enqueueRestoredRow(SyncEntityNames.ROUTINES, it.id, codec.encode(it)) }

                    val routineItems = contents.routineItems.map { it.toEntity() }
                    database.routineItemDao().insertAll(routineItems)
                    routineItems.forEach { enqueueRestoredRow(SyncEntityNames.ROUTINE_ITEMS, it.id, codec.encode(it)) }
                    // A failed preference write must abort SQL too. Room and DataStore are separate
                    // stores, so compensate preferences on failure before reporting the restore.
                    unitPreference.set(contents.unit)
                    restTimerStore.setDefaultRest(contents.restDefault)
                    currentMember.restore(contents.memberId)
                }
            } catch (error: Exception) {
                withContext(NonCancellable) {
                    runCatching { unitPreference.set(previousUnit) }.exceptionOrNull()?.let(error::addSuppressed)
                    runCatching {
                        restTimerStore.setDefaultRest(
                            previousRest,
                        )
                    }.exceptionOrNull()?.let(error::addSuppressed)
                    runCatching { currentMember.restore(previousMember) }.exceptionOrNull()?.let(error::addSuppressed)
                }
                throw error
            }
        }

        private suspend fun enqueueRestoredRow(
            entity: String,
            entityId: String,
            payloadJson: String,
        ) {
            database.syncQueueDao().insert(syncWriteEntry(entity, entityId, payloadJson))
        }
    }
