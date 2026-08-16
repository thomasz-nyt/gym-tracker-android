package com.gymtracker.core.data.backup

import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.routine.toDomain
import com.gymtracker.core.data.session.toDomain
import com.gymtracker.core.data.sessionexercise.toDomain
import com.gymtracker.core.data.set.toDomain
import com.gymtracker.core.domain.backup.BackupContents
import com.gymtracker.core.domain.backup.BackupStore
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.rest.RestTimerStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * [BackupStore] over Room and DataStore (US-40, ADR-0034).
 *
 * Takes [GymTrackerDatabase] itself rather than one DAO per table — every other Room-backed
 * repository in this codebase is scoped to a single table and takes a single DAO, but this one
 * is deliberately not: it spans all five backed-up tables, and `replaceAll` (US-41, PR2) will
 * need `database.withTransaction` regardless, so the database is the natural thing to hold.
 *
 * [read] is the whole of this class for now. `replaceAll` — the wipe-and-restore half — arrives
 * with US-41 (PR2), once a failing `ImportBackup` test motivates it; see [BackupStore]'s KDoc.
 */
class RoomBackupStore
    @Inject
    constructor(
        private val database: GymTrackerDatabase,
        private val unitPreference: UnitPreference,
        private val restTimerStore: RestTimerStore,
    ) : BackupStore {
        override suspend fun read(memberId: UserId): BackupContents {
            val userId = memberId.value
            return BackupContents(
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
